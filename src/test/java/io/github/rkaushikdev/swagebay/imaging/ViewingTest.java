package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Viewers.MAXIMUM_AMBIENT_FRACTION;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.MINIMUM_TRANSMITTED;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.faults;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.knownViewers;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.maximumAmbient;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.viewer;
import static io.github.rkaushikdev.swagebay.imaging.Viewers.viewersSupporting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewingTest {

    private static BigDecimal d(String text) {
        return new BigDecimal(text);
    }

    @Test
    void theSupportedDensityFallsOutOfTheLuminance() {
        assertThat(viewer("hi300").supportedDensity()).isEqualByComparingTo("3");
        assertThat(viewer("bench").supportedDensity()).isEqualByComparingTo("2");
    }

    @Test
    void aBrighterViewerSupportsADenserFilm() {
        assertThat(viewer("hi300").supportedDensity())
                .isGreaterThan(viewer("hi100").supportedDensity());
        assertThat(viewer("hi100").supportedDensity())
                .isGreaterThan(viewer("bench").supportedDensity());
    }

    @Test
    void aFieldViewerWillNotReadACodeDensityFilm() {
        assertThat(viewer("field").supports(d("2.0"))).isFalse();
        assertThat(viewer("field").supports(d("1.5"))).isTrue();
    }

    @Test
    void theTransmittedLightFallsByADecadePerUnitOfDensity() {
        Viewer bright = viewer("hi300");
        assertThat(bright.transmittedAt(BigDecimal.ZERO)).isEqualByComparingTo("30000");
        assertThat(bright.transmittedAt(BigDecimal.ONE)).isEqualByComparingTo("3000");
        assertThat(bright.transmittedAt(BigDecimal.valueOf(2))).isEqualByComparingTo("300");
        assertThat(bright.transmittedAt(BigDecimal.valueOf(3))).isEqualByComparingTo(MINIMUM_TRANSMITTED);
    }

    @Test
    void aNegativeDensityIsRefused() {
        assertThatThrownBy(() -> viewer("hi300").transmittedAt(BigDecimal.valueOf(-1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aViewerWithNoLuminanceIsRefused() {
        assertThatThrownBy(() -> new Viewer("x", "x", 0, true)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theRoomHasToBeAHundredthOfWhatComesThrough() {
        BigDecimal allowed = maximumAmbient(viewer("hi300"), BigDecimal.valueOf(2));
        assertThat(allowed).isEqualByComparingTo("3");
        assertThat(allowed)
                .isEqualByComparingTo(Decimals.multiply(
                        viewer("hi300").transmittedAt(BigDecimal.valueOf(2)), MAXIMUM_AMBIENT_FRACTION));
    }

    @Test
    void aDenserFilmNeedsADarkerRoom() {
        Viewer bright = viewer("hi300");
        assertThat(maximumAmbient(bright, BigDecimal.valueOf(3)))
                .isLessThan(maximumAmbient(bright, BigDecimal.valueOf(2)));
    }

    @Test
    void aSoundCombinationReportsNoFaults() {
        assertThat(faults(viewer("hi300"), d("2.5"), d("0.5"))).isEmpty();
    }

    @Test
    void aFilmTooDenseForTheViewerIsAFault() {
        List<String> found = faults(viewer("bench"), d("3.0"), d("0.01"));
        assertThat(found).anyMatch(f -> f.contains("this viewer supports"));
    }

    @Test
    void aBrightRoomIsAFaultOnItsOwn() {
        List<String> found = faults(viewer("hi300"), d("2.5"), BigDecimal.valueOf(100));
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("ambient light");
    }

    @Test
    void aThinFilmOnAnUnmaskedViewerIsAFaultToo() {
        List<String> found = faults(viewer("bench"), d("1.8"), d("0.001"));
        assertThat(found).anyMatch(f -> f.contains("washed out by the surround"));
    }

    @Test
    void aThinFilmOnAMaskedViewerIsNot() {
        assertThat(faults(viewer("hi100"), d("1.8"), d("0.001"))).isEmpty();
    }

    @Test
    void everyFaultIsReportedNotJustTheFirst() {
        List<String> found = faults(viewer("field"), d("1.9"), BigDecimal.valueOf(1000));
        assertThat(found).hasSize(3);
    }

    @Test
    void theViewersThatCanReadADensityComeBackDimmestFirst() {
        List<Viewer> fits = viewersSupporting(d("2.0"));
        assertThat(fits).extracting(Viewer::key).containsExactly("bench", "hi100", "hi300");
        assertThat(viewersSupporting(d("4.0"))).isEmpty();
    }

    @Test
    void theCatalogueIsClosed() {
        assertThat(knownViewers()).containsExactly("bench", "field", "hi100", "hi300");
        assertThatThrownBy(() -> viewer("hi999")).isInstanceOf(ConfigurationError.class);
        assertThat(viewer("hi300")).hasToString("HiLight 300 30000 cd/m2 masked");
    }
}
