package io.github.rkaushikdev.swagebay.imaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DensityLimitsTest {

    private static final LocalDateTime READ = LocalDateTime.of(2024, 5, 6, 9, 0);
    private static final Calibration CHECKED = new Calibration(
            LocalDateTime.of(2024, 5, 6, 6, 0), new BigDecimal("2.00"), new BigDecimal("2.02"), "X-Rite 301");
    private static final Viewer BRIGHT = Viewers.viewer("hi300");

    private static DensityVerdict iso(TechniqueClass techniqueClass, Viewer viewer, String... densities) {
        List<Reading> readings = Arrays.stream(densities)
                .map(density -> new Reading("weld at " + density, new BigDecimal(density)))
                .toList();
        return Density.grade(
                readings, DensityLimits.iso17636(techniqueClass, viewer), Optional.of(CHECKED), Optional.of(READ));
    }

    @Test
    void sectionFiveKeepsItsFloorsTheCeilingAndTheVariationRule() {
        DensityLimits gamma = DensityLimits.sectionV(true, false);
        assertThat(gamma.floor()).isEqualByComparingTo("2.0");
        assertThat(gamma.ceiling()).isEqualByComparingTo("4.0");
        assertThat(gamma.variationRule()).isTrue();
        assertThat(DensityLimits.sectionV(false, false).floor()).isEqualByComparingTo("1.8");
        assertThat(DensityLimits.sectionV(true, true).floor()).isEqualByComparingTo("1.3");
        assertThat(gamma.standard()).isEqualTo("ASME Section V");
    }

    @Test
    void isoSetsTheFloorByTheClassAndTheCeilingByTheViewer() {
        DensityLimits classA = DensityLimits.iso17636(TechniqueClass.A, BRIGHT);
        DensityLimits classB = DensityLimits.iso17636(TechniqueClass.B, Viewers.viewer("hi100"));
        assertThat(classA.floor()).isEqualByComparingTo("2.0");
        assertThat(classB.floor()).isEqualByComparingTo("2.3");
        assertThat(classA.ceiling()).isEqualByComparingTo("3.00");
        assertThat(classB.ceiling()).isEqualByComparingTo("2.52");
        assertThat(classB.variationRule()).isFalse();
        assertThat(classB.standard()).isEqualTo("ISO 17636-1 class B");
    }

    @Test
    void aClassBFilmUnderTwoPointThreeIsRefusedWhereClassAAcceptsIt() {
        assertThat(iso(TechniqueClass.A, BRIGHT, "2.2", "2.4").passed()).isTrue();
        DensityVerdict classB = iso(TechniqueClass.B, BRIGHT, "2.2", "2.4");
        assertThat(classB.passed()).isFalse();
        assertThat(classB.reasons()).containsExactly("weld at 2.2 reads 2.2 below the floor of 2.3");
    }

    @Test
    void isoNeedsNoReadingThroughTheIndicatorAndHasNoWindow() {
        DensityVerdict verdict = iso(TechniqueClass.A, BRIGHT, "2.1", "2.9");
        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.indicatorDensity()).isEmpty();
        assertThat(verdict.low()).isEmpty();
        assertThat(verdict.high()).isEmpty();
    }

    @Test
    void aFilmTooDenseForTheViewerIsOverTheIsoCeiling() {
        DensityVerdict verdict = iso(TechniqueClass.A, Viewers.viewer("hi100"), "2.6");
        assertThat(verdict.reasons()).containsExactly("weld at 2.6 reads 2.6 above the ceiling of 2.52");
        assertThat(iso(TechniqueClass.A, BRIGHT, "2.6").passed()).isTrue();
    }

    @Test
    void theCalibrationIsGradedUnderIsoToo() {
        DensityVerdict verdict = Density.grade(
                List.of(new Reading("weld", new BigDecimal("2.5"))),
                DensityLimits.iso17636(TechniqueClass.A, BRIGHT),
                Optional.empty(),
                Optional.empty());
        assertThat(verdict.reasons()).containsExactly("no densitometer calibration on record");
        assertThatThrownBy(() -> Density.grade(
                        List.of(),
                        DensityLimits.iso17636(TechniqueClass.A, BRIGHT),
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theOldEntryPointsStillReadSectionFive() {
        List<Reading> readings = List.of(
                new Reading("through the indicator", new BigDecimal("2.5"), true),
                new Reading("weld", new BigDecimal("3.4")));
        DensityVerdict old = Density.grade(readings, true, CHECKED, READ);
        DensityVerdict explicit =
                Density.grade(readings, DensityLimits.sectionV(true, false), Optional.of(CHECKED), Optional.of(READ));
        assertThat(old).isEqualTo(explicit);
        assertThat(old.reasons()).containsExactly("weld reads 3.4 outside the variation window");
    }
}
