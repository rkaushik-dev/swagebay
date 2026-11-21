package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.geometry.Magnification.MICROFOCUS_CEILING;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.PRACTICAL_CEILING;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.USEFUL_MAGNIFICATION;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.distancesFor;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.isMicrofocus;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.optimumMagnification;
import static io.github.rkaushikdev.swagebay.geometry.Magnification.theoreticalOptimum;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.Magnification.Distances;
import io.github.rkaushikdev.swagebay.geometry.Magnification.Setup;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MagnificationTest {

    private static Setup setup(long sod, long ofd) {
        return setup(sod, ofd, "0.1", "0.2");
    }

    private static Setup setup(long sod, long ofd, String focal, String detector) {
        return new Setup(mm(sod), mm(ofd), mm(focal), mm(detector));
    }

    @Test
    void theMagnificationIsTheRatioOfTheTwoDistances() {
        assertThat(setup(300, 300).magnification()).isEqualByComparingTo("2");
        assertThat(setup(300, 600).magnification()).isEqualByComparingTo("3");
    }

    @Test
    void aFilmHardAgainstThePartIsNotMagnified() {
        assertThat(setup(600, 0).magnification()).isEqualByComparingTo("1");
        assertThat(setup(600, 0).isWorthDoing()).isFalse();
    }

    @Test
    void aSmallAirGapIsNotWorthCallingMagnification() {
        assertThat(setup(600, 10).isWorthDoing()).isFalse();
        assertThat(setup(600, 200).isWorthDoing()).isTrue();
        assertThat(USEFUL_MAGNIFICATION).isEqualByComparingTo("1.2");
    }

    @Test
    void theDetectorBlurShrinksWhenItIsReferredBackToThePart() {
        Setup magnified = setup(300, 300);
        assertThat(magnified.effectiveDetectorUnsharpness()).isEqualTo(mm("0.1"));
        assertThat(magnified.effectiveDetectorUnsharpness()).isLessThan(magnified.detectorUnsharpness());
    }

    @Test
    void magnifyingACoarseDetectorBeatsShootingItInContact() {
        Setup contact = setup(600, 1);
        Setup magnified = setup(300, 300);
        assertThat(magnified.totalUnsharpness()).isLessThan(contact.totalUnsharpness());
    }

    @Test
    void magnifyingTooFarMakesItWorseAgain() {
        Setup best = setup(300, 1200);
        Setup tooFar = setup(100, 2900);
        assertThat(tooFar.magnification()).isGreaterThan(best.magnification());
        assertThat(tooFar.totalUnsharpness()).isGreaterThan(best.totalUnsharpness());
    }

    @Test
    void theOptimumIsWhereTheTwoBlursBalance() {
        assertThat(theoreticalOptimum(mm("0.1"), mm("0.2"))).isEqualByComparingTo("5");
        assertThat(theoreticalOptimum(mm("0.1"), mm("0.1"))).isEqualByComparingTo("2");
    }

    @Test
    void aFinerSpotEarnsAHigherOptimum() {
        BigDecimal fine = theoreticalOptimum(mm("0.02"), mm("0.2"));
        BigDecimal coarse = theoreticalOptimum(mm("0.1"), mm("0.2"));
        assertThat(fine).isGreaterThan(coarse);
    }

    @Test
    void thePracticalOptimumIsCappedWhereTheExposureStopsBeingWorthIt() {
        assertThat(theoreticalOptimum(mm("0.02"), mm("0.2"))).isGreaterThan(PRACTICAL_CEILING);
        assertThat(optimumMagnification(mm("0.02"), mm("0.2"))).isEqualByComparingTo(PRACTICAL_CEILING);
        assertThat(optimumMagnification(mm("0.1"), mm("0.2"))).isEqualByComparingTo("5");
    }

    @Test
    void aCoarserDetectorEarnsAHigherOptimumToo() {
        BigDecimal coarse = theoreticalOptimum(mm("0.1"), mm("0.4"));
        BigDecimal fine = theoreticalOptimum(mm("0.1"), mm("0.1"));
        assertThat(coarse).isGreaterThan(fine);
    }

    @Test
    void theOptimumReallyIsNearTheMinimumOfTheTotal() {
        Length focal = mm("0.1");
        Length detector = mm("0.2");
        BigDecimal best = theoreticalOptimum(focal, detector);
        Length sod = mm(300);
        Function<BigDecimal, Length> total = magnification -> {
            Length ofd = new Length(Decimals.truncate(
                    Decimals.multiply(BigDecimal.valueOf(sod.um()), Decimals.subtract(magnification, BigDecimal.ONE))));
            return new Setup(sod, ofd, focal, detector).totalUnsharpness();
        };
        BigDecimal two = BigDecimal.valueOf(2);
        assertThat(total.apply(best)).isLessThanOrEqualTo(total.apply(Decimals.subtract(best, two)));
        assertThat(total.apply(best)).isLessThanOrEqualTo(total.apply(Decimals.add(best, two)));
    }

    @Test
    void anIridiumPelletIsNotAMicrofocusSource() {
        assertThat(isMicrofocus(mm(3))).isFalse();
        assertThat(isMicrofocus(mm("0.1"))).isTrue();
        assertThat(isMicrofocus(MICROFOCUS_CEILING)).isTrue();
    }

    @Test
    void askingForAnOptimumOnAGammaSourceIsRefused() {
        assertThatThrownBy(() -> theoreticalOptimum(mm(3), mm("0.2")))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("ceiling_mm", "0.4"));
    }

    @Test
    void bothUnsharpnessTermsHaveToBeReal() {
        assertThatThrownBy(() -> theoreticalOptimum(Length.ZERO, mm("0.2"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> theoreticalOptimum(mm("0.1"), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aFixedDistanceSplitsToReachAWantedMagnification() {
        Distances split = distancesFor(BigDecimal.valueOf(2), mm(600));
        assertThat(split.sourceToObject()).isEqualTo(mm(300));
        assertThat(split.objectToFilm()).isEqualTo(mm(300));
        assertThat(new Setup(split.sourceToObject(), split.objectToFilm(), mm("0.1"), mm("0.2")).magnification())
                .isEqualByComparingTo("2");
    }

    @Test
    void aMagnificationOfOnePutsTheFilmAgainstThePart() {
        Distances split = distancesFor(BigDecimal.ONE, mm(600));
        assertThat(split.sourceToObject()).isEqualTo(mm(600));
        assertThat(split.objectToFilm()).isEqualTo(Length.ZERO);
    }

    @Test
    void aMagnificationUnderOneOrNoDistanceIsRefused() {
        assertThatThrownBy(() -> distancesFor(new BigDecimal("0.5"), mm(600))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> distancesFor(BigDecimal.valueOf(2), Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSetupNeedsASourceASpotAndADetector() {
        assertThatThrownBy(() -> setup(0, 300)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> setup(300, 300, "0", "0.2")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> setup(300, 300, "0.1", "0")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Setup(mm(300), new Length(-1), mm("0.1"), mm("0.2")))
                .isInstanceOf(ValidationError.class);
    }
}
