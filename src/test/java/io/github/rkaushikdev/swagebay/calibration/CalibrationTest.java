package io.github.rkaushikdev.swagebay.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalibrationTest {

    private static final LocalDate DONE = LocalDate.of(2024, 6, 1);

    private static Certificate certificate(LocalDate done, LocalDate due, String lab) {
        return new Certificate(done, due, lab, "C-1188");
    }

    private static Certificate certificate(LocalDate done) {
        return certificate(done, done.plusDays(365), "NRC");
    }

    private static Certificate certificate() {
        return certificate(DONE);
    }

    private static Register register() {
        Register reg = new Register();
        reg.add(new Item("LU-3-1188", Instrument.SURVEY_METER));
        reg.add(new Item("XR-301", Instrument.DENSITOMETER));
        reg.add(new Item("SW-14", Instrument.STEP_WEDGE));
        return reg;
    }

    @Test
    void anInstrumentWithNoCertificateIsOverdueRatherThanUnknown() {
        Register reg = register();
        assertThat(reg.overdueOn(DONE)).containsExactly("LU-3-1188", "SW-14", "XR-301");
        assertThat(reg.get("XR-301").dueOn()).isEmpty();
    }

    @Test
    void aCalibratedInstrumentDropsOffTheOverdueList() {
        Register reg = register();
        reg.calibrate("XR-301", certificate());
        assertThat(reg.overdueOn(DONE.plusDays(30))).doesNotContain("XR-301");
    }

    @Test
    void itComesBackWhenTheCertificateRunsOut() {
        Register reg = register();
        reg.calibrate("XR-301", certificate());
        assertThat(reg.overdueOn(DONE.plusDays(364))).doesNotContain("XR-301");
        assertThat(reg.overdueOn(DONE.plusDays(365))).contains("XR-301");
    }

    @Test
    void theCertificateDateWinsOverTheInterval() {
        Register reg = register();
        Certificate shortOne = certificate(DONE, DONE.plusDays(90), "NRC");
        reg.calibrate("XR-301", shortOne);
        assertThat(reg.get("XR-301").dueOn()).hasValue(DONE.plusDays(90));
        assertThat(reg.defaultDue("XR-301", DONE)).isEqualTo(DONE.plusDays(365));
    }

    @Test
    void theLatestCertificateIsTheOneThatCounts() {
        Register reg = register();
        reg.calibrate("XR-301", certificate(DONE));
        reg.calibrate("XR-301", certificate(DONE.plusDays(300)));
        assertThat(reg.get("XR-301").dueOn()).hasValue(DONE.plusDays(665));
    }

    @Test
    void aCertificateThatExpiresBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> new Certificate(DONE, DONE, "NRC", "C-1")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Certificate(DONE, DONE.minusDays(1), "NRC", "C-1"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aCertificateNamesTheLaboratory() {
        assertThatThrownBy(() -> certificate(DONE, DONE.plusDays(365), "  ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void anInstrumentNeedsASerial() {
        assertThatThrownBy(() -> new Item("  ", Instrument.SURVEY_METER)).isInstanceOf(ValidationError.class);
    }

    @Test
    void nothingGoesOnTheRegisterTwice() {
        Register reg = register();
        assertThatThrownBy(() -> reg.add(new Item("XR-301", Instrument.DENSITOMETER)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anInstrumentThatIsNotRegisteredCannotBeCalibrated() {
        Register reg = register();
        assertThatThrownBy(() -> reg.calibrate("XR-999", certificate())).isInstanceOf(ConflictError.class);
    }

    @Test
    void theIntervalsAreNotAllTheSame() {
        assertThat(Instrument.SURVEY_METER.defaultInterval()).isEqualTo(Duration.ofDays(365));
        assertThat(Instrument.STEP_WEDGE.defaultInterval()).isEqualTo(Duration.ofDays(730));
        assertThat(Instrument.VIEWER.defaultInterval()).isGreaterThan(Instrument.DOSIMETER.defaultInterval());
    }

    @Test
    void aStepWedgeIsReplacedRatherThanCalibrated() {
        assertThat(Instrument.STEP_WEDGE.isReplacedRatherThanCalibrated()).isTrue();
        assertThat(Instrument.SURVEY_METER.isReplacedRatherThanCalibrated()).isFalse();
    }

    @Test
    void theLookAheadListsWhatCanStillBeBooked() {
        Register reg = register();
        reg.calibrate("XR-301", certificate());
        LocalDate day = DONE.plusDays(340);
        assertThat(reg.dueWithin(day, 30)).containsExactly("XR-301");
        assertThat(reg.dueWithin(day, 10)).isEmpty();
    }

    @Test
    void somethingAlreadyOverdueIsNotOnTheLookAhead() {
        Register reg = register();
        reg.calibrate("XR-301", certificate());
        LocalDate late = DONE.plusDays(400);
        assertThat(reg.overdueOn(late)).contains("XR-301");
        assertThat(reg.dueWithin(late, 30)).isEmpty();
    }

    @Test
    void aNegativeLookAheadIsRefused() {
        assertThatThrownBy(() -> register().dueWithin(DONE, -1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theDaysLeftRunDownAndGoNegative() {
        Register reg = register();
        reg.calibrate("XR-301", certificate());
        assertThat(reg.get("XR-301").daysLeftOn(DONE)).hasValue(365);
        assertThat(reg.get("XR-301").daysLeftOn(DONE.plusDays(400))).hasValue(-35);
        assertThat(reg.get("SW-14").daysLeftOn(DONE)).isEmpty();
    }

    @Test
    void anInstrumentPrintsAsTheThingItIs() {
        assertThat(Instrument.SURVEY_METER).hasToString("survey meter");
        assertThat(Instrument.ALARM).hasToString("alarming ratemeter");
    }
}
