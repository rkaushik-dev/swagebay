package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Films.REFERENCE_FILM;
import static io.github.rkaushikdev.swagebay.imaging.Films.film;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilmTest {

    @Test
    void theChartFilmIsTheReferenceAndNeedsNoScaling() {
        assertThat(REFERENCE_FILM.key()).isEqualTo("d7");
        assertThat(REFERENCE_FILM.timeFactorFrom(REFERENCE_FILM)).isEqualByComparingTo("1");
    }

    @Test
    void aFasterFilmNeedsLessTime() {
        FilmSystem fast = film("d8");
        assertThat(fast.timeFactorFrom(REFERENCE_FILM)).isLessThan(BigDecimal.ONE);
        assertThat(fast.speedIndex()).isGreaterThan(REFERENCE_FILM.speedIndex());
    }

    @Test
    void aSlowerFilmNeedsMoreTimeAndTheFactorIsTheSpeedRatio() {
        FilmSystem slow = film("d4");
        assertThat(slow.timeFactorFrom(REFERENCE_FILM)).isEqualTo(Decimals.divide(100, 32));
        assertThat(slow.timeFactorFrom(REFERENCE_FILM)).isGreaterThan(BigDecimal.valueOf(3));
    }

    @Test
    void speedIsBoughtWithGrain() {
        assertThat(film("d2").isFinerThan(film("d8"))).isTrue();
        assertThat(film("mx125").isFinerThan(film("aa400"))).isTrue();
        assertThat(film("hs800").isFinerThan(film("d2"))).isFalse();
    }

    @Test
    void theClassesSortTheWayTheStandardListsThem() {
        assertThat(film("d2").filmClass()).isSameAs(FilmClass.SPECIAL);
        assertThat(film("d4").filmClass()).isSameAs(FilmClass.CLASS_I);
        assertThat(film("d7").filmClass()).isSameAs(FilmClass.CLASS_II);
        assertThat(film("hs800").filmClass()).isSameAs(FilmClass.CLASS_III);
        assertThat(FilmClass.CLASS_II).hasToString("II");
    }

    @Test
    void filmsInAClassComeBackSlowestFirst() {
        List<FilmSystem> classOne = Films.filmsInClass(FilmClass.CLASS_I);
        assertThat(classOne).extracting(FilmSystem::key).containsExactly("d3", "mx125", "d4", "t200");
        assertThat(Films.filmsInClass(FilmClass.SPECIAL)).containsExactly(film("d2"));
    }

    @Test
    void aFilmWithNoSpeedIsRefused() {
        assertThatThrownBy(
                        () -> new FilmSystem("x", "x", "x", FilmClass.CLASS_I, BigDecimal.ZERO, new BigDecimal("0.02")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void gammaNeedsMoreDensityThanXRaysDo() {
        assertThat(Films.densityFloor(true)).isEqualByComparingTo("2.0");
        assertThat(Films.densityFloor(false)).isEqualByComparingTo("1.8");
        assertThat(Films.densityFloor(true, true)).isEqualByComparingTo("1.3");
        assertThat(Films.densityFloor(false, true)).isEqualByComparingTo("1.3");
    }

    @Test
    void theDensityGateIsHardAtBothEnds() {
        assertThat(Films.densityInRange(new BigDecimal("2.0"), true)).isTrue();
        assertThat(Films.densityInRange(new BigDecimal("1.99"), true)).isFalse();
        assertThat(Films.densityInRange(new BigDecimal("1.9"), false)).isTrue();
        assertThat(Films.densityInRange(Films.DENSITY_CEILING, true)).isTrue();
        assertThat(Films.densityInRange(new BigDecimal("4.01"), true)).isFalse();
    }

    @Test
    void aThinFilmThatWouldPassOnXRaysFailsOnIridium() {
        BigDecimal reading = new BigDecimal("1.85");
        assertThat(Films.densityInRange(reading, false)).isTrue();
        assertThat(Films.densityInRange(reading, true)).isFalse();
    }

    @Test
    void theVariationWindowIsNotSymmetric() {
        Films.Window window = Films.variationWindow(new BigDecimal("2.5"));
        assertThat(window.low()).isEqualByComparingTo("2.125");
        assertThat(window.high()).isEqualByComparingTo("3.25");
        BigDecimal through = new BigDecimal("2.5");
        assertThat(window.high().subtract(through)).isGreaterThan(through.subtract(window.low()));
    }

    @Test
    void variationIsReadAgainstTheDensityThroughTheIndicator() {
        BigDecimal throughIqi = new BigDecimal("2.5");
        assertThat(Films.variationAcceptable(throughIqi, new BigDecimal("2.5"))).isTrue();
        assertThat(Films.variationAcceptable(throughIqi, new BigDecimal("2.13")))
                .isTrue();
        assertThat(Films.variationAcceptable(throughIqi, new BigDecimal("2.0"))).isFalse();
        assertThat(Films.variationAcceptable(throughIqi, new BigDecimal("3.25")))
                .isTrue();
        assertThat(Films.variationAcceptable(throughIqi, new BigDecimal("3.3"))).isFalse();
    }

    @Test
    void aZeroIndicatorReadingIsRefusedRatherThanDividingByIt() {
        assertThatThrownBy(() -> Films.variationWindow(BigDecimal.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Films.variationAcceptable(BigDecimal.ZERO, BigDecimal.TWO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCatalogueIsClosed() {
        List<String> keys = Films.knownFilms();
        assertThat(keys).hasSize(10);
        assertThat(keys).contains("d7");
        assertThatThrownBy(() -> film("d9")).isInstanceOf(ConfigurationError.class);
    }
}
