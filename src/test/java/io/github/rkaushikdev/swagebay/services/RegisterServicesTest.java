package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.calibration.Certificate;
import io.github.rkaushikdev.swagebay.calibration.Instrument;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.QualificationRepository.Lapse;
import io.github.rkaushikdev.swagebay.reporting.Filed;
import io.github.rkaushikdev.swagebay.reporting.Location;
import io.github.rkaushikdev.swagebay.reporting.Retention;
import io.github.rkaushikdev.swagebay.safety.Incident;
import io.github.rkaushikdev.swagebay.safety.Occurrence;
import io.github.rkaushikdev.swagebay.stock.Issue;
import io.github.rkaushikdev.swagebay.stock.Lot;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.welding.Position;
import io.github.rkaushikdev.swagebay.welding.Qualification;
import io.github.rkaushikdev.swagebay.welding.WeldingProcess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegisterServicesTest {

    private static final LocalDate DAY = LocalDate.of(2025, 4, 1);
    private static final LocalDateTime MOMENT = LocalDateTime.of(2025, 4, 1, 23, 30);
    private static final Certificate CERTIFICATE = new Certificate(DAY, DAY.plusDays(365), "NRC", "C-1");

    private static Incident anIncident() {
        return new Incident("INC-1", Occurrence.OVEREXPOSURE, MOMENT, "Come By Chance", "0142", "the source hung up");
    }

    private static Lot aLot(String number, int sheets, int expiresIn) {
        return new Lot(number, "d7", "4x10", sheets, DAY.minusDays(30), DAY.plusDays(expiresIn));
    }

    private static Lot aLot() {
        return aLot("L-1", 100, 200);
    }

    private static Qualification aQualification() {
        return new Qualification("w-19", WeldingProcess.SMAW, Position.ALL, mm(12), DAY, "P1");
    }

    @Test
    void anIncidentIsRecordedOnce() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordIncident(work, anIncident());
            assertThat(work.incidents().get("INC-1").getSite()).isEqualTo("Come By Chance");
            assertThatThrownBy(() -> RegisterService.recordIncident(work, anIncident()))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void reportingAnIncidentStopsItsClock() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordIncident(work, anIncident());
            RegisterService.notifyIncident(work, "INC-1", MOMENT.plusHours(2));
            assertThat(work.incidents().unreported()).isEmpty();
        });
    }

    @Test
    void anIncidentIsOnlyReportedOnceAndNeverBackwards() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordIncident(work, anIncident());
            assertThatThrownBy(() -> RegisterService.notifyIncident(work, "INC-1", MOMENT.minusHours(1)))
                    .isInstanceOf(ValidationError.class);
            RegisterService.notifyIncident(work, "INC-1", MOMENT);
            assertThatThrownBy(() -> RegisterService.notifyIncident(work, "INC-1", MOMENT.plusHours(1)))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void reportingAnIncidentThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> RegisterService.notifyIncident(work, "INC-9", MOMENT)))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCalibrationIsRecordedAndTheInstrumentStopsBeingOverdue() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordCalibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE);
            assertThat(work.calibrations().expiredOn(DAY.plusDays(30))).isEmpty();
            assertThat(work.calibrations().expiredOn(DAY.plusDays(400))).containsExactly("LU-1");
        });
    }

    @Test
    void theSameCalibrationTwiceIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordCalibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE);
            assertThatThrownBy(
                            () -> RegisterService.recordCalibration(work, "LU-1", Instrument.SURVEY_METER, CERTIFICATE))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aCalibrationNeedsASerial() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(
                        work -> RegisterService.recordCalibration(work, "  ", Instrument.SURVEY_METER, CERTIFICATE)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void filmIsReceivedAndShowsUpOnHand() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot());
            assertThat(RegisterService.sheetsOnHand(work, DAY, "d7", "4x10")).isEqualTo(100);
        });
    }

    @Test
    void theSameLotTwiceIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot());
            assertThatThrownBy(() -> RegisterService.receiveLot(work, aLot())).isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void filmIsIssuedShortestDatedFirstAndComesOffTheShelf() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot("L-1", 100, 300));
            RegisterService.receiveLot(work, aLot("L-2", 100, 100));
            assertThat(RegisterService.issueFilm(work, DAY, "d7", "4x10", 40)).containsExactly(new Issue("L-2", 40));
            assertThat(RegisterService.sheetsOnHand(work, DAY, "d7", "4x10")).isEqualTo(160);
        });
    }

    @Test
    void anIssueSpansLotsWhenOneWillNotCoverIt() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot("L-1", 30, 50));
            RegisterService.receiveLot(work, aLot("L-2", 100, 300));
            assertThat(RegisterService.issueFilm(work, DAY, "d7", "4x10", 50))
                    .containsExactly(new Issue("L-1", 30), new Issue("L-2", 20));
        });
    }

    @Test
    void askingForMoreFilmThanThereIsNamesTheShortfall() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot("L-1", 20, 200));
            assertThatThrownBy(() -> RegisterService.issueFilm(work, DAY, "d7", "4x10", 50))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    caught.context().get("on_hand"))
                            .isEqualTo(20));
        });
    }

    @Test
    void anIssueOfNothingIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> RegisterService.issueFilm(work, DAY, "d7", "4x10", 0)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theReorderPointFiresAtTheFloor() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.receiveLot(work, aLot("L-1", 25, 200));
            assertThat(RegisterService.reorderNeeded(work, DAY, "d7", "4x10", 20))
                    .isFalse();
            assertThat(RegisterService.reorderNeeded(work, DAY, "d7", "4x10", 25))
                    .isTrue();
            assertThatThrownBy(() -> RegisterService.reorderNeeded(work, DAY, "d7", "4x10", -1))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aRadiographIsFiledOnceAndKeepsItsLocation() {
        Filed entry = new Filed(
                "25-0001-R0001", "25-0001", DAY, Retention.FIVE_YEARS, new Location("Come By Chance", "A", "14"));
        TestDatabase.fresh().transaction(work -> {
            RegisterService.fileRadiograph(work, entry);
            assertThat(work.archive().find("25-0001-R0001").orElseThrow().getBox())
                    .contains("14");
            assertThatThrownBy(() -> RegisterService.fileRadiograph(work, entry))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aFilmFiledWithNoLocationIsReported() {
        Filed entry = new Filed("25-0001-R0002", "25-0001", DAY, Retention.INDEFINITE, Optional.empty());
        TestDatabase.fresh().transaction(work -> {
            RegisterService.fileRadiograph(work, entry);
            assertThat(work.archive().withoutALocation()).containsExactly("25-0001-R0002");
        });
    }

    @Test
    void aQualificationIsRecordedAndStartsItsContinuityClock() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordQualification(work, aQualification());
            assertThat(work.qualifications().lapsedOn(DAY.plusDays(100))).isEmpty();
            assertThat(work.qualifications().lapsedOn(DAY.plusDays(200))).containsExactly(new Lapse("w-19", "SMAW"));
        });
    }

    @Test
    void usingAProcessPushesTheLapseOut() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordQualification(work, aQualification());
            RegisterService.recordProcessUse(work, "w-19", "SMAW", DAY.plusDays(150));
            assertThat(work.qualifications().lapsedOn(DAY.plusDays(200))).isEmpty();
        });
    }

    @Test
    void usingAProcessNobodyIsQualifiedOnIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> RegisterService.recordProcessUse(work, "w-99", "SMAW", DAY)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theSameQualificationTwiceIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            RegisterService.recordQualification(work, aQualification());
            assertThatThrownBy(() -> RegisterService.recordQualification(work, aQualification()))
                    .isInstanceOf(ConflictError.class);
        });
    }
}
