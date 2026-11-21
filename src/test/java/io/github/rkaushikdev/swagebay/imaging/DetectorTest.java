package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Detectors.MAX_ACCEPTABLE_BURN_IN;
import static io.github.rkaushikdev.swagebay.imaging.Detectors.detector;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectorTest {

    /** A sound panel with the named parts replaced, the way a characterization report is amended. */
    private record Panel(Length pitch, Length resolution, BigDecimal lag, BigDecimal burnIn, int columns) {

        static Panel sound() {
            return new Panel(mm("0.200"), mm("0.250"), new BigDecimal("0.01"), new BigDecimal("0.001"), 1024);
        }

        Panel pitch(Length value) {
            return new Panel(value, resolution, lag, burnIn, columns);
        }

        Panel lag(BigDecimal value) {
            return new Panel(pitch, resolution, value, burnIn, columns);
        }

        Panel burnIn(BigDecimal value) {
            return new Panel(pitch, resolution, lag, value, columns);
        }

        Panel columns(int value) {
            return new Panel(pitch, resolution, lag, burnIn, value);
        }

        DetectorArray build(String key) {
            return new DetectorArray(
                    key, key, "x", pitch, resolution, 200, new BigDecimal("0.7"), lag, burnIn, columns, 1024);
        }
    }

    @Test
    void resolutionIsAlwaysWorseThanThePitch() {
        for (String key : Detectors.knownDetectors()) {
            DetectorArray panel = detector(key);
            assertThat(panel.basicSpatialResolution()).isGreaterThan(panel.pixelPitch());
            assertThat(panel.resolutionPenalty()).isGreaterThan(BigDecimal.ONE);
        }
    }

    @Test
    void theDatasheetLieIsMeasurable() {
        DetectorArray panel = detector("flex7");
        assertThat(panel.pixelPitch()).isEqualTo(mm("0.127"));
        assertThat(panel.basicSpatialResolution()).isEqualTo(mm("0.200"));
        assertThat(panel.resolutionPenalty()).isStrictlyBetween(new BigDecimal("1.5"), new BigDecimal("1.6"));
    }

    @Test
    void aPanelReportsItsPixelCountAndItsActiveArea() {
        DetectorArray panel = detector("dxr250");
        assertThat(panel.pixels()).isEqualTo(1024L * 1024);
        DetectorArray.Area area = panel.activeArea();
        assertThat(area.width()).isEqualTo(mm("204.8"));
        assertThat(area.height()).isEqualTo(mm("204.8"));
    }

    @Test
    void aRectangularPanelHasARectangularArea() {
        DetectorArray.Area area = detector("flex7").activeArea();
        assertThat(area.width()).isGreaterThan(area.height());
        assertThat(detector("flex7").pixels()).isEqualTo(2048L * 1536);
    }

    @Test
    void lagInsideTheLimitPassesAndOutsideItDoesNot() {
        assertThat(detector("dxr250").lagIsAcceptable()).isTrue();
        assertThat(detector("xrd1611").lagIsAcceptable()).isTrue();
        assertThat(detector("flex7").lagIsAcceptable()).isFalse();
    }

    @Test
    void burnInIsWhatScrapsAPanel() {
        assertThat(detector("dxr250").isScrap()).isFalse();
        DetectorArray scrap = new DetectorArray(
                "tired",
                "tired",
                "x",
                mm("0.200"),
                mm("0.250"),
                200,
                new BigDecimal("0.7"),
                new BigDecimal("0.01"),
                MAX_ACCEPTABLE_BURN_IN.add(new BigDecimal("0.001")),
                1024,
                1024);
        assertThat(scrap.isScrap()).isTrue();
        assertThat(scrap.shortfalls(mm(1), 100)).anyMatch(s -> s.contains("burn in"));
    }

    @Test
    void aPanelCanFailOnLagAloneWhileMeetingTheSpecification() {
        DetectorArray panel = detector("flex7");
        List<String> shortfalls = panel.shortfalls(mm("0.200"), 150);
        assertThat(shortfalls).hasSize(1);
        assertThat(shortfalls.get(0)).contains("image lag");
    }

    @Test
    void aPanelThatClearsEverythingReportsNothing() {
        assertThat(detector("dxr250").shortfalls(mm("0.250"), 200)).isEmpty();
    }

    @Test
    void bothTheResolutionAndTheNoiseShortfallAreReported() {
        List<String> shortfalls = detector("dxr250").shortfalls(mm("0.160"), 300);
        assertThat(shortfalls).hasSize(2);
        assertThat(shortfalls.get(0)).contains("coarser");
        assertThat(shortfalls.get(1)).contains("normalised snr");
    }

    @Test
    void theStockThatMeetsASpecificationComesBackCoarsestFirst() {
        List<DetectorArray> fits = Detectors.detectorsMeeting(mm("0.250"), 180);
        assertThat(fits).extracting(DetectorArray::key).containsExactly("dxr250", "xrd1611");
        assertThat(Detectors.detectorsMeeting(mm("0.050"), 100)).isEmpty();
    }

    @Test
    void aPanelClaimingResolutionFinerThanItsPitchIsRefused() {
        assertThatThrownBy(() -> new DetectorArray(
                        "liar",
                        "liar",
                        "x",
                        mm("0.200"),
                        mm("0.100"),
                        200,
                        new BigDecimal("0.7"),
                        new BigDecimal("0.01"),
                        new BigDecimal("0.001"),
                        1024,
                        1024))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("pitch_um", 200L));
    }

    @Test
    void aPanelWithNoPitchOrNoSizeIsRefused() {
        assertThat(Panel.sound().build("x")).isNotNull();
        assertThatThrownBy(() -> Panel.sound().pitch(Length.ZERO).build("x")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Panel.sound().columns(0).build("x")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Panel.sound().lag(new BigDecimal("1.5")).build("x"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Panel.sound().burnIn(BigDecimal.valueOf(-1)).build("x"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCatalogueIsClosed() {
        assertThat(Detectors.knownDetectors()).containsExactly("dxr250", "flex7", "xrd1611");
        assertThatThrownBy(() -> detector("dxr999")).isInstanceOf(ConfigurationError.class);
        assertThat(detector("dxr250")).hasToString("DXR250C 1024x1024");
    }
}
