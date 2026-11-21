package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Duplex.meets;
import static io.github.rkaushikdev.swagebay.imaging.Duplex.pairForResolution;
import static io.github.rkaushikdev.swagebay.imaging.Duplex.pairs;
import static io.github.rkaushikdev.swagebay.imaging.Duplex.resolutionFromReading;
import static io.github.rkaushikdev.swagebay.imaging.Duplex.unsharpnessBudget;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class DuplexTest {

    @Test
    void aPairResolvesToTwiceItsWireDiameter() {
        DuplexPair seven = new DuplexPair(7);
        assertThat(seven.wireDiameter()).isEqualTo(mm("0.20"));
        assertThat(seven.unsharpness()).isEqualTo(mm("0.40"));
    }

    @Test
    void theBasicSpatialResolutionIsHalfTheUnsharpness() {
        DuplexPair seven = new DuplexPair(7);
        assertThat(seven.basicSpatialResolution()).isEqualTo(mm("0.20"));
        assertThat(seven.basicSpatialResolution().times(2)).isEqualTo(seven.unsharpness());
    }

    @Test
    void aHigherPairNumberIsAFinerPair() {
        assertThat(pairs())
                .extracting(DuplexPair::basicSpatialResolution)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(pairs()).hasSize(13);
    }

    @Test
    void theGaugeRunsFromEightTenthsDownToFiveHundredths() {
        assertThat(new DuplexPair(1).wireDiameter()).isEqualTo(mm("0.80"));
        assertThat(new DuplexPair(13).wireDiameter()).isEqualTo(mm("0.05"));
        assertThat(new DuplexPair(4)).hasToString("D4");
    }

    @Test
    void aPairOutsideTheGaugeIsRefused() {
        assertThatThrownBy(() -> new DuplexPair(0)).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new DuplexPair(14)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aReadingConvertsStraightToAResolution() {
        assertThat(resolutionFromReading(7)).isEqualTo(mm("0.20"));
        assertThat(resolutionFromReading(10)).isEqualTo(mm("0.10"));
    }

    @Test
    void theCoarsestPairThatMeetsATargetIsTheOneToAskFor() {
        assertThat(pairForResolution(mm("0.20")).number()).isEqualTo(7);
        assertThat(pairForResolution(mm("0.25")).number()).isEqualTo(6);
        assertThat(pairForResolution(mm("0.19")).number()).isEqualTo(8);
    }

    @Test
    void aTargetFinerThanTheGaugeIsRefused() {
        assertThatThrownBy(() -> pairForResolution(mm("0.01")))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.context())
                        .containsEntry("target_mm", "0.01"));
        assertThatThrownBy(() -> pairForResolution(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void meetingARequirementIsAComparisonNotACount() {
        assertThat(meets(10, mm("0.20"))).isTrue();
        assertThat(meets(7, mm("0.20"))).isTrue();
        assertThat(meets(6, mm("0.20"))).isFalse();
        assertThat(meets(1, mm("0.20"))).isFalse();
    }

    @Test
    void theTwoUnsharpnessTermsAddInQuadrature() {
        Length total = unsharpnessBudget(mm("0.30"), mm("0.40"));
        assertThat(total).isEqualTo(mm("0.50"));
    }

    @Test
    void quadratureIsGentlerThanAStraightSum() {
        Length quadrature = unsharpnessBudget(mm("0.30"), mm("0.40"));
        assertThat(quadrature).isLessThan(mm("0.30").plus(mm("0.40")));
    }

    @Test
    void oneTermAloneIsTheWholeBudget() {
        assertThat(unsharpnessBudget(mm("0.40"), Length.ZERO)).isEqualTo(mm("0.40"));
        assertThat(unsharpnessBudget(Length.ZERO, mm("0.40"))).isEqualTo(mm("0.40"));
        assertThat(unsharpnessBudget(Length.ZERO, Length.ZERO)).isEqualTo(Length.ZERO);
    }

    @Test
    void theLargerTermDominatesTheBudget() {
        Length total = unsharpnessBudget(mm("0.40"), mm("0.10"));
        assertThat(total).isStrictlyBetween(mm("0.40"), mm("0.42"));
    }

    @Test
    void aNegativeUnsharpnessTermIsRefused() {
        assertThatThrownBy(() -> unsharpnessBudget(new Length(-1), mm("0.40"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> unsharpnessBudget(mm("0.40"), new Length(-1))).isInstanceOf(ValidationError.class);
    }
}
