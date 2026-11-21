package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Density.STEP_WEDGE_TOLERANCE;
import static io.github.rkaushikdev.swagebay.imaging.Density.grade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DensityTest {

    private static final LocalDateTime CHECKED = LocalDateTime.of(2024, 5, 6, 6, 0);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 5, 6, 9, 0);

    private static Calibration calibration(String error, LocalDateTime checked) {
        return new Calibration(
                checked, new BigDecimal("2.00"), new BigDecimal("2.00").add(new BigDecimal(error)), "X-Rite 301");
    }

    private static Calibration calibration(String error) {
        return calibration(error, CHECKED);
    }

    private static Calibration calibration() {
        return calibration("0.02");
    }

    private static List<Reading> readings(String indicator, String left, String right) {
        return List.of(
                new Reading("through the indicator", new BigDecimal(indicator), true),
                new Reading("weld left", new BigDecimal(left)),
                new Reading("weld right", new BigDecimal(right)));
    }

    private static List<Reading> readings() {
        return readings("2.50", "2.40", "2.60");
    }

    @Test
    void aSoundSetOfReadingsPasses() {
        DensityVerdict verdict = grade(readings(), true, calibration(), READ);
        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.reasons()).isEmpty();
        assertThat(verdict.indicatorDensity())
                .hasValueSatisfying(d -> assertThat(d).isEqualByComparingTo("2.50"));
        assertThat(verdict.describe()).isEqualTo("density acceptable");
    }

    @Test
    void theVariationWindowComesBackWithTheVerdict() {
        DensityVerdict verdict = grade(readings(), true, calibration(), READ);
        assertThat(verdict.low()).hasValueSatisfying(low -> assertThat(low).isEqualByComparingTo("2.125"));
        assertThat(verdict.high()).hasValueSatisfying(high -> assertThat(high).isEqualByComparingTo("3.25"));
    }

    @Test
    void aThinFilmFailsTheFloorOnGamma() {
        DensityVerdict verdict = grade(readings("1.90", "1.85", "1.95"), true, calibration(), READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).hasSize(3);
        assertThat(verdict.reasons().get(0)).contains("below the floor of 2.0");
    }

    @Test
    void theSameFilmWouldHavePassedOnXRays() {
        DensityVerdict verdict = grade(readings("1.90", "1.85", "1.95"), false, calibration(), READ);
        assertThat(verdict.passed()).isTrue();
    }

    @Test
    void aDenseFilmFailsTheCeiling() {
        DensityVerdict verdict = grade(readings("4.10", "4.05", "4.15"), true, calibration(), READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).allMatch(r -> r.contains("above the ceiling"));
    }

    @Test
    void aReadingInsideTheBandCanStillFailTheVariationRule() {
        DensityVerdict verdict = grade(readings("3.20", "2.10", "3.10"), true, calibration(), READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).anyMatch(r -> r.contains("outside the variation window"));
        assertThat(verdict.reasons()).noneMatch(r -> r.contains("floor"));
    }

    @Test
    void aSingleReadingCannotBeCheckedForVariation() {
        List<Reading> single = List.of(new Reading("middle", new BigDecimal("2.50"), true));
        DensityVerdict verdict = grade(single, true, calibration(), READ);
        assertThat(verdict.passed()).isTrue();
    }

    @Test
    void readingsWithNoIndicatorReadingAreRefusedOnThatGround() {
        List<Reading> plain =
                List.of(new Reading("middle", new BigDecimal("2.50")), new Reading("end", new BigDecimal("2.55")));
        DensityVerdict verdict = grade(plain, true, calibration(), READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).contains("exactly one reading has to be taken through the indicator");
    }

    @Test
    void twoIndicatorReadingsAreRefusedToo() {
        List<Reading> two =
                List.of(new Reading("a", new BigDecimal("2.50"), true), new Reading("b", new BigDecimal("2.55"), true));
        DensityVerdict verdict = grade(two, true, calibration(), READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.indicatorDensity()).isEmpty();
    }

    @Test
    void aCompositeViewHasAMuchLowerFloor() {
        List<Reading> thin = readings("1.40", "1.35", "1.45");
        assertThat(grade(thin, true, calibration(), READ).passed()).isFalse();
        assertThat(grade(thin, true, true, Optional.of(calibration()), Optional.of(READ))
                        .passed())
                .isTrue();
    }

    @Test
    void aMissingCalibrationIsItselfAReason() {
        DensityVerdict verdict = grade(readings(), true);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).contains("no densitometer calibration on record");
    }

    @Test
    void aStaleCalibrationIsAReason() {
        Calibration stale = calibration("0.02", CHECKED.minusDays(1));
        DensityVerdict verdict = grade(readings(), true, stale, READ);
        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasons()).contains("densitometer calibration is not current");
    }

    @Test
    void aFailedStepWedgeCheckIsNeverCurrent() {
        Calibration bad = calibration("0.09");
        assertThat(bad.passed()).isFalse();
        assertThat(bad.error().abs()).isGreaterThan(STEP_WEDGE_TOLERANCE);
        assertThat(bad.isCurrentAt(READ)).isFalse();
        assertThat(calibration("0.05").passed()).isTrue();
    }

    @Test
    void aCalibrationCoversOneShiftAndNoMore() {
        Calibration check = calibration();
        assertThat(check.isCurrentAt(CHECKED)).isTrue();
        assertThat(check.isCurrentAt(CHECKED.plusHours(7).plusMinutes(59))).isTrue();
        assertThat(check.isCurrentAt(CHECKED.plusHours(8))).isFalse();
        assertThat(check.isCurrentAt(CHECKED.minusMinutes(1))).isFalse();
    }

    @Test
    void aReadingNeedsALabelAndABelievableNumber() {
        assertThatThrownBy(() -> new Reading("  ", new BigDecimal("2.5"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Reading("a", BigDecimal.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Reading("a", BigDecimal.valueOf(9))).isInstanceOf(ValidationError.class);
    }

    @Test
    void gradingNothingIsRefused() {
        assertThatThrownBy(() -> grade(List.of(), true)).isInstanceOf(ValidationError.class);
    }
}
