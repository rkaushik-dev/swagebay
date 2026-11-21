package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.exposure.Corrections.collimatorCorrection;
import static io.github.rkaushikdev.swagebay.exposure.Corrections.distanceCorrection;
import static io.github.rkaushikdev.swagebay.exposure.Corrections.filmCorrection;
import static io.github.rkaushikdev.swagebay.exposure.Corrections.materialCorrection;
import static io.github.rkaushikdev.swagebay.exposure.Corrections.screenCorrection;
import static io.github.rkaushikdev.swagebay.imaging.Films.REFERENCE_FILM;
import static io.github.rkaushikdev.swagebay.imaging.Films.film;
import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.materials.Materials.material;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CorrectionsTest {

    @Test
    void shootingFurtherAwayMultipliesByTheSquare() {
        Correction doubled = distanceCorrection(inch(24), inch(48));
        assertThat(doubled.factor()).isEqualByComparingTo("4");
        assertThat(doubled.note()).contains("24 in");
    }

    @Test
    void shootingCloserDividesByTheSquare() {
        assertThat(distanceCorrection(inch(24), inch(12)).factor()).isEqualByComparingTo("0.25");
    }

    @Test
    void theSameDistanceLeavesTheReadingAlone() {
        assertThat(distanceCorrection(inch(24), inch(24)).factor()).isEqualByComparingTo("1");
    }

    @Test
    void aDistanceOfNothingIsRefused() {
        assertThatThrownBy(() -> distanceCorrection(Length.ZERO, inch(24))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> distanceCorrection(inch(24), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aFasterFilmShortensTheShot() {
        Correction faster = filmCorrection(REFERENCE_FILM, film("d8"));
        assertThat(faster.factor()).isLessThan(BigDecimal.ONE);
        assertThat(faster.factor()).isEqualByComparingTo(Decimals.divide(100, 180));
    }

    @Test
    void aSlowerFilmLengthensIt() {
        assertThat(filmCorrection(REFERENCE_FILM, film("d4")).factor()).isEqualByComparingTo(Decimals.divide(100, 32));
        assertThat(filmCorrection(REFERENCE_FILM, REFERENCE_FILM).factor()).isEqualByComparingTo("1");
    }

    @Test
    void theMaterialFactorIsReportedAndNotFoldedIn() {
        Correction alu = materialCorrection(STEEL, material("aluminium"), Quality.IR192);
        assertThat(alu.factor()).isEqualByComparingTo("0.35");
        assertThat(alu.note()).contains("aluminium read as carbon steel");
    }

    @Test
    void aMaterialTheChartWasDrawnForNeedsNoFactor() {
        assertThat(materialCorrection(STEEL, STEEL, Quality.IR192).factor()).isEqualByComparingTo("1");
    }

    @Test
    void screensHalveTheExposureWhenTheyAreFitted() {
        assertThat(screenCorrection(true).factor()).isEqualByComparingTo("0.5");
        assertThat(screenCorrection(false).factor()).isEqualByComparingTo("1");
        assertThat(screenCorrection(true, new BigDecimal("0.4")).factor()).isEqualByComparingTo("0.4");
    }

    @Test
    void aCollimatorCostsExposureRatherThanSavingIt() {
        assertThat(collimatorCorrection(new BigDecimal("0.8")).factor()).isEqualByComparingTo("1.25");
        assertThat(collimatorCorrection(BigDecimal.ONE).factor()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> collimatorCorrection(BigDecimal.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> collimatorCorrection(new BigDecimal("1.2"))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aCorrectionOfZeroOrLessIsRefused() {
        assertThatThrownBy(() -> new Correction("bad", BigDecimal.ZERO, "nothing"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Correction("bad", BigDecimal.valueOf(-1), "nothing"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void anEmptyChainLeavesTheReadingAlone() {
        CorrectionChain chain = new CorrectionChain();
        assertThat(chain.combined()).isEqualByComparingTo("1");
        assertThat(chain.appliedTo(BigDecimal.valueOf(30))).isEqualByComparingTo("30");
        assertThat(chain.describe()).isEqualTo("no corrections");
    }

    @Test
    void aChainMultipliesInTheOrderItWasBuilt() {
        CorrectionChain chain = new CorrectionChain()
                .withCorrection(distanceCorrection(inch(24), inch(48)))
                .withCorrection(filmCorrection(REFERENCE_FILM, film("d5")));
        assertThat(chain.names()).containsExactly("distance", "film");
        assertThat(chain.combined())
                .isEqualByComparingTo(Decimals.multiply(BigDecimal.valueOf(4), Decimals.divide(100, 55)));
        assertThat(chain.appliedTo(BigDecimal.valueOf(30))).isGreaterThan(BigDecimal.valueOf(200));
    }

    @Test
    void aChainIsImmutableSoASheetCannotBeEditedAfterTheFact() {
        CorrectionChain first = new CorrectionChain().withCorrection(screenCorrection(true));
        CorrectionChain second = first.withCorrection(collimatorCorrection(new BigDecimal("0.8")));
        assertThat(first.names()).containsExactly("screens");
        assertThat(second.names()).containsExactly("screens", "collimator");
    }

    @Test
    void opposingCorrectionsCancel() {
        CorrectionChain chain = new CorrectionChain()
                .withCorrection(distanceCorrection(inch(24), inch(48)))
                .withCorrection(screenCorrection(true, new BigDecimal("0.25")));
        assertThat(chain.combined()).isEqualByComparingTo("1");
    }

    @Test
    void aChainWillNotBeAppliedToNothing() {
        assertThatThrownBy(() -> new CorrectionChain().appliedTo(BigDecimal.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theChainPrintsTheArithmeticTheSheetShows() {
        CorrectionChain chain = new CorrectionChain().withCorrection(distanceCorrection(inch(24), inch(48)));
        assertThat(chain.describe()).isEqualTo("distance x4");
        assertThat(screenCorrection(true)).hasToString("screens x0.5");
    }
}
