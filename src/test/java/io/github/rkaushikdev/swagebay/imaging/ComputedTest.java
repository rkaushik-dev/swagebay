package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Plates.SCAN_DEADLINE;
import static io.github.rkaushikdev.swagebay.imaging.Plates.SCAN_WITHOUT_CORRECTION;
import static io.github.rkaushikdev.swagebay.imaging.Plates.plate;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComputedTest {

    @Test
    void aPlateCarriesBothHalvesOfItsCharacterization() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.basicSpatialResolution()).isEqualTo(mm("0.040"));
        assertThat(fine.normalisedSnr()).isEqualTo(130);
        assertThat(fine.ipClass()).isEqualTo("IP1");
    }

    @Test
    void aFinerPlateIsNoisierAtTheSameExposure() {
        PlateSystem fine = plate("ip1_hd");
        PlateSystem fast = plate("ip5_fast");
        assertThat(fine.basicSpatialResolution()).isLessThan(fast.basicSpatialResolution());
        assertThat(fine.normalisedSnr()).isLessThan(fast.normalisedSnr());
    }

    @Test
    void aPlateScannedAtOnceHasLostAlmostNothing() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.remainingSignal(Duration.ZERO)).isEqualByComparingTo("1");
        assertThat(fine.remainingSignal(minutes(10))).isGreaterThan(new BigDecimal("0.99"));
    }

    @Test
    void aPlateLeftAHalfLifeHasLostHalfTheImage() {
        PlateSystem fine = plate("ip1_hd");
        BigDecimal left = fine.remainingSignal(fine.fadeHalfLife());
        assertThat(left.subtract(new BigDecimal("0.5")).abs()).isLessThan(new BigDecimal("1e-20"));
    }

    @Test
    void aPlateLeftOvernightHasLostMostOfIt() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.remainingSignal(Duration.fromHours(12))).isLessThan(new BigDecimal("0.7"));
        assertThat(fine.remainingSignal(Duration.fromHours(40))).isLessThan(new BigDecimal("0.3"));
    }

    @Test
    void thePromptWindowIsHalfAnHour() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.scanIsPrompt(minutes(29))).isTrue();
        assertThat(fine.scanIsPrompt(SCAN_WITHOUT_CORRECTION)).isTrue();
        assertThat(fine.scanIsPrompt(minutes(31))).isFalse();
    }

    @Test
    void pastTheDeadlineThePlateIsScrapped() {
        PlateSystem fine = plate("ip1_hd");
        assertThat(fine.scanIsUsable(Duration.fromHours(7))).isTrue();
        assertThat(fine.scanIsUsable(SCAN_DEADLINE)).isTrue();
        assertThat(fine.scanIsUsable(Duration.fromHours(9))).isFalse();
    }

    @Test
    void aLateScanIsStillUsableWhileNotBeingPrompt() {
        PlateSystem fine = plate("ip1_hd");
        Duration waited = Duration.fromHours(4);
        assertThat(fine.scanIsPrompt(waited)).isFalse();
        assertThat(fine.scanIsUsable(waited)).isTrue();
    }

    @Test
    void aSlowerPlateHoldsItsImageLonger() {
        assertThat(plate("ip5_fast").fadeHalfLife())
                .isGreaterThan(plate("ip1_hd").fadeHalfLife());
    }

    @Test
    void aPlateCanMeetOneHalfOfASpecificationAndFailTheOther() {
        PlateSystem coarseButQuiet = plate("ip5_fast");
        assertThat(coarseButQuiet.meetsNoise(200)).isTrue();
        assertThat(coarseButQuiet.meetsResolution(mm("0.080"))).isFalse();
        List<String> shortfalls = coarseButQuiet.shortfalls(mm("0.080"), 200);
        assertThat(shortfalls).hasSize(1);
        assertThat(shortfalls.get(0)).contains("coarser");
    }

    @Test
    void bothShortfallsAreReportedWhenBothFail() {
        PlateSystem fine = plate("ip1_hd");
        List<String> shortfalls = fine.shortfalls(mm("0.030"), 200);
        assertThat(shortfalls).hasSize(2);
        assertThat(shortfalls.get(1)).contains("normalised snr 130 is under 200");
    }

    @Test
    void aPlateThatClearsBothReportsNothing() {
        assertThat(plate("ip2_std").shortfalls(mm("0.080"), 140)).isEmpty();
    }

    @Test
    void theStockThatMeetsASpecificationComesBackCoarsestFirst() {
        List<PlateSystem> fits = Plates.platesMeeting(mm("0.080"), 150);
        assertThat(fits).extracting(PlateSystem::key).containsExactly("ip3_gp", "ip2_std");
        assertThat(Plates.platesMeeting(mm("0.010"), 100)).isEmpty();
    }

    @Test
    void aPlateWithNoResolutionOrNoNoiseFigureIsRefused() {
        assertThatThrownBy(() -> new PlateSystem("x", "x", "x", "IP1", Length.ZERO, 100, Duration.fromHours(20)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new PlateSystem("x", "x", "x", "IP1", mm("0.04"), 0, Duration.fromHours(20)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new PlateSystem("x", "x", "x", "IP1", mm("0.04"), 100, Duration.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCatalogueIsClosed() {
        assertThat(Plates.knownPlates()).containsExactly("ip1_hd", "ip2_std", "ip3_gp", "ip5_fast");
        assertThatThrownBy(() -> plate("ip9")).isInstanceOf(ConfigurationError.class);
        assertThat(plate("ip1_hd")).hasToString("HD-IP (IP1)");
    }
}
