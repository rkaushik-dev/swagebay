package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Noise.REFERENCE_RESOLUTION;
import static io.github.rkaushikdev.swagebay.imaging.Noise.denormalise;
import static io.github.rkaushikdev.swagebay.imaging.Noise.meetsRequirement;
import static io.github.rkaushikdev.swagebay.imaging.Noise.normalise;
import static io.github.rkaushikdev.swagebay.imaging.Noise.requiredNormalisedSnr;
import static io.github.rkaushikdev.swagebay.imaging.Noise.shortfall;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoiseTest {

    private static final TechniqueClass A = TechniqueClass.A;
    private static final TechniqueClass B = TechniqueClass.B;

    private static NoiseMeasurement measurement(String mean, String noise, String resolution) {
        return new NoiseMeasurement("parent metal", new BigDecimal(mean), new BigDecimal(noise), mm(resolution));
    }

    private static NoiseMeasurement measurement(String mean, String noise) {
        return measurement(mean, noise, "0.0886");
    }

    private static NoiseMeasurement measurement(String resolution) {
        return measurement("12000", "100", resolution);
    }

    private static NoiseMeasurement measurement() {
        return measurement("0.0886");
    }

    @Test
    void signalToNoiseIsTheMeanOverTheDeviation() {
        assertThat(measurement().signalToNoise()).isEqualByComparingTo("120");
    }

    @Test
    void aSystemAtTheReferenceResolutionNeedsNoScaling() {
        assertThat(measurement().resolution()).isEqualTo(REFERENCE_RESOLUTION);
        assertThat(measurement().normalised().subtract(BigDecimal.valueOf(120)).abs())
                .isLessThan(new BigDecimal("0.01"));
    }

    @Test
    void aCoarseSystemIsScaledDown() {
        NoiseMeasurement coarse = measurement("0.200");
        assertThat(coarse.signalToNoise()).isEqualByComparingTo("120");
        assertThat(coarse.normalised()).isLessThan(BigDecimal.valueOf(90));
    }

    @Test
    void aFineSystemIsScaledUp() {
        NoiseMeasurement fine = measurement("0.040");
        assertThat(fine.normalised()).isGreaterThan(BigDecimal.valueOf(170));
    }

    @Test
    void aCoarsePanelFlatteringItselfIsCaughtByTheNormalisation() {
        NoiseMeasurement coarse =
                new NoiseMeasurement("a", BigDecimal.valueOf(20000), BigDecimal.valueOf(100), mm("0.200"));
        NoiseMeasurement fine =
                new NoiseMeasurement("b", BigDecimal.valueOf(12000), BigDecimal.valueOf(100), mm("0.050"));
        assertThat(coarse.signalToNoise()).isGreaterThan(fine.signalToNoise());
        assertThat(coarse.normalised()).isLessThan(fine.normalised());
    }

    @Test
    void normalisingAndBackAgainReturnsTheRawFigure() {
        BigDecimal raw = BigDecimal.valueOf(120);
        Length resolution = mm("0.160");
        assertThat(denormalise(normalise(raw, resolution), resolution)
                        .subtract(raw)
                        .abs())
                .isLessThan(new BigDecimal("0.0001"));
    }

    @Test
    void theScalingNeedsRealNumbers() {
        assertThatThrownBy(() -> normalise(BigDecimal.ZERO, mm("0.1"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> normalise(BigDecimal.valueOf(120), Length.ZERO))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> denormalise(BigDecimal.ZERO, mm("0.1"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> denormalise(BigDecimal.valueOf(120), Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aMeasurementNeedsALabelASignalAndSomeNoise() {
        assertThatThrownBy(() -> new NoiseMeasurement("  ", BigDecimal.valueOf(1000), BigDecimal.TEN, mm("0.1")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new NoiseMeasurement("a", BigDecimal.ZERO, BigDecimal.TEN, mm("0.1")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new NoiseMeasurement("a", BigDecimal.valueOf(1000), BigDecimal.ZERO, mm("0.1")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new NoiseMeasurement("a", BigDecimal.valueOf(1000), BigDecimal.TEN, Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theRequirementRisesWithThickness() {
        List<Integer> floors = List.of(5, 15, 30, 80, 200).stream()
                .map(t -> requiredNormalisedSnr(mm(t), A))
                .toList();
        assertThat(floors).containsExactly(70, 80, 90, 100, 110);
    }

    @Test
    void classBAsksForMoreAtEveryThickness() {
        for (int thickness : List.of(5, 15, 30, 80, 200)) {
            assertThat(requiredNormalisedSnr(mm(thickness), B)).isGreaterThan(requiredNormalisedSnr(mm(thickness), A));
        }
    }

    @Test
    void theBandsAreClosedAtTheTop() {
        assertThat(requiredNormalisedSnr(mm(10), A)).isEqualTo(70);
        assertThat(requiredNormalisedSnr(mm("10.1"), A)).isEqualTo(80);
    }

    @Test
    void theTableRunsOut() {
        assertThatThrownBy(() -> requiredNormalisedSnr(mm(300), A))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("technique_class", "A"));
        assertThatThrownBy(() -> requiredNormalisedSnr(Length.ZERO, A)).isInstanceOf(ValidationError.class);
    }

    @Test
    void anImageThatClearsTheFloorHasNoShortfall() {
        NoiseMeasurement good = measurement("12000", "100");
        assertThat(meetsRequirement(good, mm(10), A)).isTrue();
        assertThat(shortfall(good, mm(10), A)).isEqualByComparingTo("0");
    }

    @Test
    void anImageUnderTheFloorReportsHowFarUnder() {
        NoiseMeasurement thin = measurement("6000", "100");
        assertThat(meetsRequirement(thin, mm(10), B)).isFalse();
        BigDecimal gap = shortfall(thin, mm(10), B);
        assertThat(gap).isStrictlyBetween(BigDecimal.valueOf(39), BigDecimal.valueOf(41));
    }

    @Test
    void theSameImageCanPassClassAAndFailClassB() {
        NoiseMeasurement borderline = measurement("8500", "100");
        assertThat(meetsRequirement(borderline, mm(10), A)).isTrue();
        assertThat(meetsRequirement(borderline, mm(10), B)).isFalse();
    }

    @Test
    void aMeasurementPrintsBothFigures() {
        String text = measurement().toString();
        assertThat(text).contains("snr 120.0");
        assertThat(text).contains("normalised 120.0");
    }
}
