package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDevicesTest {

    private static final String ASSAY = "2025-06-01T00:00:00";
    private static final String STRIPPED = "2025-06-02";
    private static final String MORNING = "2025-06-24";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShopWithAWipedSource() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run("source", "add-device", "D-77", "880d");
        shop.run("source", "register", "IR-5512", "--isotope", "ir192", "--curies", "90", "--assayed", ASSAY);
        shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp");
    }

    private void bothChecks() {
        shop.run("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303");
        shop.run("device", "inspect", "D-77", "--on", MORNING, "--by", "0142");
    }

    @Test
    void aCleanPreUseCheckIsRecorded() {
        Shell.Result result = shop.run("device", "inspect", "D-77", "--on", MORNING, "--by", "0142");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("pre use passed by 0142");
    }

    @Test
    void aQuarterlyIsRecordedAsAQuarterly() {
        Shell.Result result = shop.run("device", "inspect", "D-77", "--quarterly", "--on", STRIPPED, "--by", "0303");
        assertThat(result.out()).contains("quarterly passed by 0303");
    }

    @Test
    void aDefectOnASafetyItemTakesItOutOfService() {
        Shell.Result result = shop.run(
                "device",
                "inspect",
                "D-77",
                "--on",
                MORNING,
                "--by",
                "0142",
                "--defect",
                "drive_cable:three strands broken");
        assertThat(result.out()).contains("out of service", "pre use failed");
    }

    @Test
    void aDefectOnTheWheelsIsOnlyNoted() {
        Shell.Result result = shop.run(
                "device",
                "inspect",
                "D-77",
                "--quarterly",
                "--on",
                STRIPPED,
                "--by",
                "0303",
                "--defect",
                "wheels:one castor seized");
        assertThat(result.out()).contains("noted").doesNotContain("out of service");
    }

    @Test
    void anItemNothingInspectsIsRefusedWithTheOnesThatExist() {
        Shell.Result result = shop.run(
                "device", "inspect", "D-77", "--on", MORNING, "--by", "0142", "--defect", "flux_capacitor:humming");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
        assertThat(result.err()).containsPattern("known='[^']*drive cable");
    }

    @Test
    void aDefectWithNoAccountIsRefused() {
        Shell.Result result =
                shop.run("device", "inspect", "D-77", "--on", MORNING, "--by", "0142", "--defect", "wheels:");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void aDeviceWithNothingOnRecordSaysSo() {
        assertThat(shop.run("device", "history", "D-77").out()).contains("nothing on record");
    }

    @Test
    void theHistoryListsBothKindsAndTheNextDueDate() {
        bothChecks();
        String output = shop.run("device", "history", "D-77").out();
        assertThat(output).contains("quarterly", "pre use", "next quarterly due by 2025-09-02");
    }

    @Test
    void aLoadWithBothChecksClears() {
        bothChecks();
        Shell.Result result = shop.run("device", "clear", "D-77", "--source", "IR-5512", "--on", MORNING);
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("is clear for");
    }

    @Test
    void aLoadThatIsNotClearedExitsFourAndNamesEveryReason() {
        Shell.Result result = shop.run("device", "clear", "D-77", "--source", "IR-5512", "--on", MORNING);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("no quarterly inspection on record", "no pre use check that morning");
    }

    @Test
    void aSourceNobodyRegisteredShowsUpInTheReasons() {
        bothChecks();
        Shell.Result result = shop.run("device", "clear", "D-77", "--source", "IR-9999", "--on", MORNING);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("not on the inventory");
    }
}
