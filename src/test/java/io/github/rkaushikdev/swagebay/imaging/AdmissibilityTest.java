package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Admissibility.assess;
import static io.github.rkaushikdev.swagebay.imaging.Admissibility.densityIsApplicable;
import static io.github.rkaushikdev.swagebay.imaging.Admissibility.noiseFloorFor;
import static io.github.rkaushikdev.swagebay.imaging.Films.film;
import static io.github.rkaushikdev.swagebay.imaging.Plates.plate;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdmissibilityTest {

    private static final LocalDateTime CHECKED = LocalDateTime.of(2024, 5, 20, 6, 0);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 5, 20, 9, 0);
    private static final ImagingSystem ON_FILM = ImagingSystem.fromFilm(film("d7"));
    private static final ImagingSystem ON_PLATE = ImagingSystem.fromPlate(plate("ip1_hd"));

    private static IndicatorEvidence holeSeen(boolean visible) {
        return IndicatorEvidence.hole(new HoleIndicator(20), HoleSize.TWO_T, visible);
    }

    private static IndicatorEvidence wireSeen(int finest) {
        return IndicatorEvidence.wire(new WireIndicator(13), finest);
    }

    private static DensityVerdict goodDensity() {
        return Density.grade(
                List.of(
                        new Reading("indicator", new BigDecimal("2.50"), true),
                        new Reading("weld", new BigDecimal("2.60"))),
                true,
                new Calibration(CHECKED, new BigDecimal("2.00"), new BigDecimal("2.01"), "X-Rite"),
                READ);
    }

    private static NoiseMeasurement quietNoise(String mean) {
        return new NoiseMeasurement("parent", new BigDecimal(mean), BigDecimal.valueOf(100), mm("0.040"));
    }

    private static Evidence.Builder filmEvidence() {
        return Evidence.builder(ON_FILM, mm(12), TechniqueClass.A, holeSeen(true))
                .density(goodDensity());
    }

    private static Evidence.Builder plateEvidence() {
        return Evidence.builder(ON_PLATE, mm(12), TechniqueClass.A, wireSeen(13))
                .noise(quietNoise("12000"));
    }

    @Test
    void aSoundFilmRadiographIsAdmissible() {
        Admissibility verdict = assess(filmEvidence().build());
        assertThat(verdict.admissible()).isTrue();
        assertThat(verdict.describe()).isEqualTo("admissible");
        assertThat(verdict.gatesRun()).containsExactly("indicator", "density");
    }

    @Test
    void theNoiseGateNeverRunsOnFilm() {
        Admissibility verdict = assess(filmEvidence().noise(Optional.empty()).build());
        assertThat(verdict.admissible()).isTrue();
        assertThat(verdict.gatesRun()).doesNotContain("normalised snr");
    }

    @Test
    void theDensityGateNeverRunsOnAPlate() {
        Admissibility verdict = assess(plateEvidence().density(Optional.empty()).build());
        assertThat(verdict.admissible()).isTrue();
        assertThat(verdict.gatesRun()).doesNotContain("density");
        assertThat(densityIsApplicable(ON_PLATE)).isFalse();
        assertThat(densityIsApplicable(ON_FILM)).isTrue();
    }

    @Test
    void filmWithNoDensityReadingsFailsOnThat() {
        Admissibility verdict = assess(filmEvidence().density(Optional.empty()).build());
        assertThat(verdict.admissible()).isFalse();
        assertThat(verdict.reasons()).contains("no density readings on record");
    }

    @Test
    void aPlateWithNoNoiseMeasurementFailsOnThat() {
        Admissibility verdict = assess(plateEvidence().noise(Optional.empty()).build());
        assertThat(verdict.reasons()).contains("no signal to noise measurement on record");
    }

    @Test
    void anInvisibleEssentialHoleSinksARadiographOnItsOwn() {
        Admissibility verdict = assess(filmEvidence().indicator(holeSeen(false)).build());
        assertThat(verdict.admissible()).isFalse();
        assertThat(verdict.reasons()).containsExactly("the 2T hole in the 20 plaque is not visible");
    }

    @Test
    void aWireCoarserThanRequiredSinksItToo() {
        Admissibility verdict = assess(plateEvidence().indicator(wireSeen(11)).build());
        assertThat(verdict.admissible()).isFalse();
        assertThat(verdict.reasons()).contains("the finest wire visible is W11, W13 is required");
    }

    @Test
    void noWireVisibleAtAllIsReportedDifferently() {
        IndicatorEvidence evidence = IndicatorEvidence.wire(new WireIndicator(13), Optional.empty());
        assertThat(evidence.satisfied()).isFalse();
        assertThat(evidence.describeFailure()).contains("no wire is visible");
    }

    @Test
    void aFinerWireThanRequiredIsFine() {
        assertThat(wireSeen(15).satisfied()).isTrue();
        assertThat(wireSeen(13).satisfied()).isTrue();
    }

    @Test
    void everyFailureIsCollectedNotJustTheFirst() {
        DensityVerdict thin = Density.grade(
                List.of(
                        new Reading("indicator", new BigDecimal("1.40"), true),
                        new Reading("weld", new BigDecimal("1.45"))),
                true);
        Admissibility verdict =
                assess(filmEvidence().indicator(holeSeen(false)).density(thin).build());
        assertThat(verdict.reasons()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(verdict.reasons()).anyMatch(r -> r.contains("hole"));
        assertThat(verdict.reasons()).anyMatch(r -> r.contains("floor"));
    }

    @Test
    void aDigitalImageUnderTheNoiseFloorFails() {
        Admissibility verdict = assess(plateEvidence()
                .techniqueClass(TechniqueClass.B)
                .noise(quietNoise("3000"))
                .build());
        assertThat(verdict.admissible()).isFalse();
        assertThat(verdict.reasons().get(0)).contains("under the floor of 110");
    }

    @Test
    void theNoiseFloorIsReadOffTheClassAndTheThickness() {
        assertThat(noiseFloorFor(
                        plateEvidence().techniqueClass(TechniqueClass.B).build()))
                .isEqualByComparingTo("110");
        assertThat(noiseFloorFor(plateEvidence().build())).isEqualByComparingTo("80");
    }

    @Test
    void theResolutionGateOnlyRunsWhenARequirementIsStated() {
        assertThat(assess(plateEvidence().build()).admissible()).isTrue();
        Admissibility stated =
                assess(plateEvidence().requiredResolution(mm("0.100")).build());
        assertThat(stated.admissible()).isFalse();
        assertThat(stated.reasons()).contains("no duplex gauge reading on record");
    }

    @Test
    void aDuplexReadingCoarserThanRequiredFails() {
        Admissibility verdict = assess(
                plateEvidence().requiredResolution(mm("0.100")).duplexReading(6).build());
        assertThat(verdict.admissible()).isFalse();
        assertThat(verdict.reasons().get(0)).contains("coarser");
    }

    @Test
    void aDuplexReadingThatMeetsTheRequirementPasses() {
        Admissibility verdict = assess(plateEvidence()
                .requiredResolution(mm("0.100"))
                .duplexReading(10)
                .build());
        assertThat(verdict.admissible()).isTrue();
        assertThat(verdict.gatesRun()).containsExactly("indicator", "normalised snr", "basic spatial resolution");
    }

    @Test
    void aRadiographIsGradedOnOneFamilyOfIndicatorNotBoth() {
        assertThatThrownBy(() -> new IndicatorEvidence(
                        Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new IndicatorEvidence(
                        Optional.of(new HoleIndicator(20)),
                        Optional.of(HoleSize.TWO_T),
                        false,
                        Optional.of(new WireIndicator(13)),
                        Optional.empty()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new IndicatorEvidence(
                        Optional.of(new HoleIndicator(20)),
                        Optional.empty(),
                        false,
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }
}
