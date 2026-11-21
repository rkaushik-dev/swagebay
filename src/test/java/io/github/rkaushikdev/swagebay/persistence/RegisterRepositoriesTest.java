package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.CalibrationRow;
import io.github.rkaushikdev.swagebay.persistence.models.FiledRow;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.persistence.models.LotRow;
import io.github.rkaushikdev.swagebay.persistence.models.QualificationRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RegisterRepositoriesTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 24);
    private static final LocalDateTime MOMENT = LocalDateTime.of(2025, 3, 24, 23, 30);

    private static IncidentRow incident(String reference, LocalDateTime notified, LocalDateTime found) {
        IncidentRow row = new IncidentRow();
        row.setReference(reference);
        row.setOccurrence("dose over a limit");
        row.setDiscoveredAt(found);
        row.setSite("site");
        row.setReportedBy("0142");
        row.setAccount("a");
        row.setNotifiedAt(notified);
        return row;
    }

    private static IncidentRow incident(String reference) {
        return incident(reference, null, MOMENT);
    }

    private static IncidentRow foundAt(String reference, LocalDateTime found) {
        return incident(reference, null, found);
    }

    private static CalibrationRow calibration(String serial, LocalDate done) {
        CalibrationRow row = new CalibrationRow();
        row.setSerial(serial);
        row.setInstrumentKind("survey meter");
        row.setCalibratedOn(done);
        row.setDueOn(done.plusDays(365));
        row.setLaboratory("NRC");
        row.setCertificateReference("C-1");
        return row;
    }

    private static LotRow lot(String number, int sheets, int expiresIn, String size) {
        LotRow row = new LotRow();
        row.setLotNumber(number);
        row.setFilmKey("d7");
        row.setSize(size);
        row.setSheets(sheets);
        row.setReceivedOn(DAY.minusDays(30));
        row.setExpiresOn(DAY.plusDays(expiresIn));
        return row;
    }

    private static LotRow lot(String number, int sheets, int expiresIn) {
        return lot(number, sheets, expiresIn, "4x10");
    }

    private static FiledRow filed(String radiograph, String job, boolean located) {
        FiledRow row = new FiledRow();
        row.setRadiograph(radiograph);
        row.setJob(job);
        row.setFiledOn(DAY);
        row.setRetention("five years");
        row.setSite(located ? "site" : null);
        row.setCabinet(located ? "A" : null);
        row.setBox(located ? "14" : null);
        return row;
    }

    private static FiledRow filed(String radiograph) {
        return filed(radiograph, "25-0001", true);
    }

    private static QualificationRow qualification(String welder, String process, LocalDate lastUsed) {
        QualificationRow row = new QualificationRow();
        row.setWelder(welder);
        row.setProcess(process);
        row.setPosition("6G");
        row.setCoupon(mm(12));
        row.setMaterialGroup("P1");
        row.setQualifiedOn(DAY.minusDays(400));
        row.setLastUsedOn(lastUsed);
        return row;
    }

    @Test
    void anUnreportedIncidentIsOutstandingAndAReportedOneIsNot() {
        try (TestSession db = TestSession.open()) {
            db.add(incident("INC-1"));
            db.add(incident("INC-2", MOMENT, MOMENT));
            db.commit();
            assertThat(new IncidentRepository(db.entityManager()).unreported())
                    .extracting(IncidentRow::getReference)
                    .containsExactly("INC-1");
        }
    }

    @Test
    void theUnreportedListComesBackOldestFirst() {
        try (TestSession db = TestSession.open()) {
            db.add(foundAt("INC-2", MOMENT));
            db.add(foundAt("INC-1", MOMENT.minusHours(2)));
            db.commit();
            assertThat(new IncidentRepository(db.entityManager()).unreported())
                    .extracting(IncidentRow::getReference)
                    .containsExactly("INC-1", "INC-2");
        }
    }

    @Test
    void anIncidentIsFoundByReferenceAndAMissingOneRaises() {
        try (TestSession db = TestSession.open()) {
            db.add(incident("INC-1"));
            db.commit();
            IncidentRepository repository = new IncidentRepository(db.entityManager());
            assertThat(repository.get("INC-1").getSite()).isEqualTo("site");
            assertThat(repository.find("INC-9")).isEmpty();
            assertThatThrownBy(() -> repository.get("INC-9")).isInstanceOf(NotFoundError.class);
        }
    }

    @Test
    void incidentsSinceAMomentExcludeTheOlderOnes() {
        try (TestSession db = TestSession.open()) {
            db.add(foundAt("INC-1", MOMENT.minusDays(5)));
            db.add(foundAt("INC-2", MOMENT));
            db.commit();
            assertThat(new IncidentRepository(db.entityManager()).since(MOMENT.minusDays(1)))
                    .extracting(IncidentRow::getReference)
                    .containsExactly("INC-2");
        }
    }

    @Test
    void theLatestCertificateIsTheOneThatCounts() {
        try (TestSession db = TestSession.open()) {
            db.add(calibration("LU-1", DAY.minusDays(400)));
            db.add(calibration("LU-1", DAY));
            db.commit();
            assertThat(new CalibrationRepository(db.entityManager()).latestFor("LU-1"))
                    .hasValueSatisfying(
                            latest -> assertThat(latest.getCalibratedOn()).isEqualTo(DAY));
        }
    }

    @Test
    void anInstrumentWithAnExpiredCertificateIsListed() {
        try (TestSession db = TestSession.open()) {
            db.add(calibration("LU-1", DAY.minusDays(400)));
            db.add(calibration("LU-2", DAY));
            db.commit();
            assertThat(new CalibrationRepository(db.entityManager()).expiredOn(DAY))
                    .containsExactly("LU-1");
        }
    }

    @Test
    void theLookAheadNamesWhatCanStillBeBooked() {
        try (TestSession db = TestSession.open()) {
            db.add(calibration("LU-1", DAY.minusDays(350)));
            db.commit();
            CalibrationRepository repository = new CalibrationRepository(db.entityManager());
            assertThat(repository.dueWithin(DAY, 30)).containsExactly("LU-1");
            assertThat(repository.dueWithin(DAY, 5)).isEmpty();
            assertThatThrownBy(() -> repository.dueWithin(DAY, -1)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void filmComesOffTheShelfShortestDatedFirst() {
        try (TestSession db = TestSession.open()) {
            db.add(lot("L-1", 100, 300));
            db.add(lot("L-2", 100, 100));
            db.commit();
            assertThat(new StockRepository(db.entityManager()).usableOn(DAY, "d7", "4x10"))
                    .extracting(LotRow::getLotNumber)
                    .containsExactly("L-2", "L-1");
        }
    }

    @Test
    void expiredAndEmptyLotsAreNotOnHand() {
        try (TestSession db = TestSession.open()) {
            db.add(lot("L-1", 100, -1));
            db.add(lot("L-2", 0, 200));
            db.add(lot("L-3", 40, 200));
            db.commit();
            assertThat(new StockRepository(db.entityManager()).onHand(DAY, "d7", "4x10"))
                    .isEqualTo(40);
        }
    }

    @Test
    void theExpiredListOnlyNamesLotsWithFilmInThem() {
        try (TestSession db = TestSession.open()) {
            db.add(lot("L-1", 100, -1));
            db.add(lot("L-2", 0, -1));
            db.commit();
            assertThat(new StockRepository(db.entityManager()).expiredOn(DAY)).containsExactly("L-1");
        }
    }

    @Test
    void filmOfAnotherSizeIsNotOnHand() {
        try (TestSession db = TestSession.open()) {
            db.add(lot("L-1", 100, 200, "7x17"));
            db.commit();
            assertThat(new StockRepository(db.entityManager()).onHand(DAY, "d7", "4x10"))
                    .isZero();
        }
    }

    @Test
    void theFilmsOnOneJobComeBackInOrder() {
        try (TestSession db = TestSession.open()) {
            db.add(filed("25-0001-R0002"));
            db.add(filed("25-0001-R0001"));
            db.add(filed("25-0002-R0001", "25-0002", true));
            db.commit();
            assertThat(new ArchiveRepository(db.entityManager()).forJob("25-0001"))
                    .extracting(FiledRow::getRadiograph)
                    .containsExactly("25-0001-R0001", "25-0001-R0002");
        }
    }

    @Test
    void aFilmWithNoLocationIsReported() {
        try (TestSession db = TestSession.open()) {
            db.add(filed("25-0001-R0001", "25-0001", false));
            db.add(filed("25-0001-R0002"));
            db.commit();
            ArchiveRepository repository = new ArchiveRepository(db.entityManager());
            assertThat(repository.withoutALocation()).containsExactly("25-0001-R0001");
            assertThat(repository.find("25-0001-R0002").flatMap(FiledRow::getBox))
                    .contains("14");
        }
    }

    @Test
    void aWelderWhoHasGoneQuietIsLapsed() {
        try (TestSession db = TestSession.open()) {
            db.add(qualification("w-19", "SMAW", DAY.minusDays(200)));
            db.add(qualification("w-04", "SMAW", DAY.minusDays(10)));
            db.commit();
            assertThat(new QualificationRepository(db.entityManager()).lapsedOn(DAY))
                    .containsExactly(new QualificationRepository.Lapse("w-19", "SMAW"));
        }
    }

    @Test
    void theContinuityWindowIsAnArgument() {
        try (TestSession db = TestSession.open()) {
            db.add(qualification("w-19", "SMAW", DAY.minusDays(100)));
            db.commit();
            QualificationRepository repository = new QualificationRepository(db.entityManager());
            assertThat(repository.lapsedOn(DAY)).isEmpty();
            assertThat(repository.lapsedOn(DAY, 90)).containsExactly(new QualificationRepository.Lapse("w-19", "SMAW"));
            assertThatThrownBy(() -> repository.lapsedOn(DAY, 0)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void everyQualificationAWelderHoldsComesBack() {
        try (TestSession db = TestSession.open()) {
            db.add(qualification("w-19", "SMAW", DAY));
            db.add(qualification("w-19", "GTAW", DAY));
            db.commit();
            QualificationRepository repository = new QualificationRepository(db.entityManager());
            assertThat(repository.forWelder("w-19")).hasSize(2);
            assertThat(repository.forWelder("w-99")).isEmpty();
        }
    }
}
