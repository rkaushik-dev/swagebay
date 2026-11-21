package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Plates.plate;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.achievableResolution;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.effectiveSnr;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.faults;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.isOversampled;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.lowestWorkableGain;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.recommendedPitch;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.resolutionCeiling;
import static io.github.rkaushikdev.swagebay.imaging.Scanning.settingsFor;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTest {

    private static ScanSettings settings(String pitch, int gain, int latitude) {
        return new ScanSettings(mm(pitch), gain, latitude);
    }

    private static ScanSettings settings(String pitch, int gain) {
        return settings(pitch, gain, 3);
    }

    private static ScanSettings settings(String pitch) {
        return settings(pitch, 1);
    }

    private static ScanSettings atGain(int gain) {
        return settings("0.050", gain);
    }

    @Test
    void theResolutionCeilingIsTwoPixels() {
        assertThat(resolutionCeiling(settings("0.050"))).isEqualTo(mm("0.100"));
    }

    @Test
    void aCoarseScanThrowsAwayWhatAFinePlateHeld() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.basicSpatialResolution()).isEqualTo(mm("0.040"));
        assertThat(achievableResolution(fine, settings("0.100"))).isEqualTo(mm("0.200"));
    }

    @Test
    void aFineScanCannotImproveOnThePlate() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(achievableResolution(fine, settings("0.010"))).isEqualTo(mm("0.040"));
    }

    @Test
    void scanningFinerThanThePlateHoldsIsOversampling() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(isOversampled(fine, settings("0.010"))).isTrue();
        assertThat(isOversampled(fine, settings("0.020"))).isFalse();
        assertThat(isOversampled(fine, settings("0.100"))).isFalse();
    }

    @Test
    void theRecommendedPitchIsHalfThePlateResolution() {
        assertThat(recommendedPitch(plate("ip1_hd"))).isEqualTo(mm("0.020"));
        assertThat(recommendedPitch(plate("ip5_fast"))).isEqualTo(mm("0.065"));
    }

    @Test
    void gainOneCostsNothing() {
        ScanSettings quiet = atGain(1);
        assertThat(quiet.noisePenalty()).isEqualByComparingTo("1");
        assertThat(effectiveSnr(plate("ip1_hd"), quiet)).isEqualByComparingTo("130");
    }

    @Test
    void turningTheGainUpCostsSignalToNoise() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(effectiveSnr(fine, atGain(5))).isLessThan(effectiveSnr(fine, atGain(1)));
        assertThat(effectiveSnr(fine, atGain(10))).isLessThan(effectiveSnr(fine, atGain(5)));
    }

    @Test
    void fiveGainStepsCostAboutHalfTheSignalToNoise() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(effectiveSnr(fine, atGain(5))
                        .subtract(BigDecimal.valueOf(65))
                        .abs())
                .isLessThan(new BigDecimal("0.5"));
    }

    @Test
    void aSettingOutsideTheScannerIsRefused() {
        assertThatThrownBy(() -> atGain(0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> atGain(11)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> settings("0.050", 1, 6)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new ScanSettings(Length.ZERO, 1, 3)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aSoundScanOfAFinePlateReportsNothing() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(faults(fine, settings("0.020"), mm("0.050"), 120)).isEmpty();
    }

    @Test
    void aCoarseScanFailsTheResolutionRequirement() {
        PlateSystem fine = plate("ip1_hd");
        List<String> found = faults(fine, settings("0.100"), mm("0.050"), 120);
        assertThat(found).anyMatch(f -> f.contains("coarser than"));
    }

    @Test
    void aHighGainScanFailsTheNoiseRequirement() {
        PlateSystem fine = plate("ip1_hd");
        List<String> found = faults(fine, settings("0.020", 6), mm("0.050"), 120);
        assertThat(found).anyMatch(f -> f.contains("signal to noise"));
        assertThat(found).anyMatch(f -> f.contains("at gain 6"));
    }

    @Test
    void oversamplingIsReportedEvenWhenNothingElseIsWrong() {
        PlateSystem fine = plate("ip1_hd");
        List<String> found = faults(fine, settings("0.005"), mm("0.050"), 120);
        assertThat(found).anyMatch(f -> f.contains("costs time and gains nothing"));
    }

    @Test
    void everyFaultIsReportedNotJustTheFirst() {
        PlateSystem fine = plate("ip1_hd");
        List<String> found = faults(fine, settings("0.150", 8), mm("0.050"), 120);
        assertThat(found).hasSize(2);
    }

    @Test
    void theLowestWorkableGainIsTheOneToUse() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(lowestWorkableGain(fine, 110)).isEqualTo(1);
        assertThat(lowestWorkableGain(fine, 125)).isEqualTo(1);
        assertThat(lowestWorkableGain(fine, 60)).isEqualTo(1);
    }

    @Test
    void aCoarserPlateCanAffordMoreGain() {
        PlateSystem coarse = plate("ip5_fast");
        assertThat(lowestWorkableGain(coarse, 120)).isGreaterThanOrEqualTo(1);
        assertThat(effectiveSnr(coarse, atGain(1))).isGreaterThan(BigDecimal.valueOf(200));
    }

    @Test
    void aNoiseFloorNothingReachesIsRefused() {
        assertThatThrownBy(() -> lowestWorkableGain(plate("ip1_hd"), 500))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("required_snr", 500));
        assertThatThrownBy(() -> lowestWorkableGain(plate("ip1_hd"), 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theSettingsTheOfficeWouldWriteDown() {
        ScanSettings chosen = settingsFor(plate("ip1_hd"), 120);
        assertThat(chosen.pitch()).isEqualTo(mm("0.020"));
        assertThat(chosen.gain()).isEqualTo(1);
        assertThat(chosen.latitude()).isEqualTo(3);
    }

    @Test
    void theSettingsPrintForTheSheet() {
        assertThat(settings("0.020", 3).describe()).isEqualTo("0.020 mm pitch, gain 3, latitude 3");
    }
}
