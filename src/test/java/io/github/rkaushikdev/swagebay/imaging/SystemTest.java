package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Detectors.detector;
import static io.github.rkaushikdev.swagebay.imaging.Films.film;
import static io.github.rkaushikdev.swagebay.imaging.ImagingSystem.FILM_RESOLUTION;
import static io.github.rkaushikdev.swagebay.imaging.ImagingSystem.densityApplies;
import static io.github.rkaushikdev.swagebay.imaging.ImagingSystem.noiseApplies;
import static io.github.rkaushikdev.swagebay.imaging.Plates.plate;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemTest {

    @Test
    void aFilmSystemCarriesItsSpeedAndNoNoiseFigure() {
        ImagingSystem system = ImagingSystem.fromFilm(film("d7"));
        assertThat(system.medium()).isSameAs(Medium.FILM);
        assertThat(system.speedIndex())
                .hasValueSatisfying(speed -> assertThat(speed).isEqualByComparingTo("100"));
        assertThat(system.normalisedSnr()).isEmpty();
    }

    @Test
    void aPlateCarriesItsNoiseFigureAndNoSpeed() {
        ImagingSystem system = ImagingSystem.fromPlate(plate("ip2_std"));
        assertThat(system.medium()).isSameAs(Medium.PLATE);
        assertThat(system.normalisedSnr()).contains(150);
        assertThat(system.speedIndex()).isEmpty();
    }

    @Test
    void aPanelCarriesItsOwnResolution() {
        ImagingSystem system = ImagingSystem.fromDetector(detector("dxr250"));
        assertThat(system.medium()).isSameAs(Medium.PANEL);
        assertThat(system.basicSpatialResolution()).isEqualTo(mm("0.250"));
    }

    @Test
    void filmIsFinerThanAnythingDigitalInTheShop() {
        ImagingSystem onFilm = ImagingSystem.fromFilm(film("d4"));
        assertThat(onFilm.basicSpatialResolution()).isEqualTo(FILM_RESOLUTION);
        for (String key : List.of("ip1_hd", "ip2_std")) {
            assertThat(onFilm.basicSpatialResolution())
                    .isLessThan(ImagingSystem.fromPlate(plate(key)).basicSpatialResolution());
        }
    }

    @Test
    void theMediumDecidesWhichGateRuns() {
        ImagingSystem onFilm = ImagingSystem.fromFilm(film("d7"));
        ImagingSystem onPanel = ImagingSystem.fromDetector(detector("dxr250"));
        assertThat(densityApplies(onFilm)).isTrue();
        assertThat(noiseApplies(onFilm)).isFalse();
        assertThat(noiseApplies(onPanel)).isTrue();
        assertThat(densityApplies(onPanel)).isFalse();
    }

    @Test
    void theGatesAreNamedSoAReportCanListThem() {
        assertThat(ImagingSystem.fromFilm(film("d7")).gates())
                .containsExactly("density", "density variation", "indicator");
        assertThat(ImagingSystem.fromPlate(plate("ip1_hd")).gates())
                .containsExactly("normalised snr", "basic spatial resolution", "indicator");
    }

    @Test
    void theIndicatorGateRunsOnEverything() {
        for (ImagingSystem system : List.of(
                ImagingSystem.fromFilm(film("d7")),
                ImagingSystem.fromPlate(plate("ip1_hd")),
                ImagingSystem.fromDetector(detector("dxr250")))) {
            assertThat(system.gates()).contains("indicator");
        }
    }

    @Test
    void bothPlateAndPanelCountAsDigital() {
        assertThat(Medium.PLATE.isDigital()).isTrue();
        assertThat(Medium.PANEL.isDigital()).isTrue();
        assertThat(Medium.FILM.isDigital()).isFalse();
    }

    @Test
    void aSystemResolvesAFeatureAtOrAboveItsResolution() {
        ImagingSystem system = ImagingSystem.fromDetector(detector("dxr250"));
        assertThat(system.resolves(mm("0.250"))).isTrue();
        assertThat(system.resolves(mm("0.400"))).isTrue();
        assertThat(system.resolves(mm("0.100"))).isFalse();
    }

    @Test
    void aFilmSystemWithoutASpeedIndexIsRefused() {
        assertThatThrownBy(
                        () -> new ImagingSystem("x", "x", Medium.FILM, mm("0.03"), Optional.empty(), Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aDigitalSystemWithoutANoiseFigureIsRefused() {
        assertThatThrownBy(
                        () -> new ImagingSystem("x", "x", Medium.PANEL, mm("0.2"), Optional.empty(), Optional.empty()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(
                        () -> new ImagingSystem("x", "x", Medium.PLATE, mm("0.06"), Optional.empty(), Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSystemWithNoResolutionIsRefused() {
        assertThatThrownBy(() -> new ImagingSystem(
                        "x", "x", Medium.FILM, Length.ZERO, Optional.of(BigDecimal.valueOf(100)), Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSystemPrintsWhatItIsAndWhatItIsOn() {
        assertThat(ImagingSystem.fromFilm(film("d7"))).hasToString("Structurix D7 on film");
        assertThat(ImagingSystem.fromPlate(plate("ip1_hd"))).hasToString("HD-IP on computed radiography");
    }
}
