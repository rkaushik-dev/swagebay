package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliMorningTest {

    private static final String ASSAY = "2025-06-01T00:00:00";
    private static final String FRESH = "2025-07-01T00:00:00";
    private static final String DAY = "2025-07-10";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShopWithACameraAndASource() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run("source", "add-device", "D-77", "880d");
        shop.run("source", "register", "IR-5512", "--isotope", "ir192", "--curies", "90", "--assayed", ASSAY);
    }

    private void inspected() {
        shop.run("device", "inspect", "D-77", "--quarterly", "--on", "2025-06-20", "--by", "0303");
        shop.run("device", "inspect", "D-77", "--on", DAY, "--by", "0142");
    }

    private void ready() {
        shop.run("source", "wipe", "IR-5512", "--at", FRESH, "--by", "rp");
        inspected();
    }

    @Test
    void aQuietMorningExitsZero() {
        ready();
        Shell.Result result = shop.run("morning", "--on", DAY);
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("nothing outstanding");
    }

    @Test
    void aDeviceWithNoInspectionStopsTheDay() {
        shop.run("source", "wipe", "IR-5512", "--at", FRESH, "--by", "rp");
        Shell.Result result = shop.run("morning", "--on", DAY);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("stops work today", "no quarterly inspection on record");
    }

    @Test
    void aSourceWithNoWipeTestStopsTheDay() {
        inspected();
        Shell.Result result = shop.run("morning", "--on", DAY);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("no wipe test on record");
    }

    @Test
    void theLoadsFlagListsWhatCouldGoOut() {
        ready();
        Shell.Result result = shop.run("morning", "--on", DAY, "--loads");
        assertThat(result.out()).contains("ready to go out", "D-77", "IR-5512");
    }

    @Test
    void theLoadsFlagSaysSoWhenNothingCanGo() {
        Shell.Result result = shop.run("morning", "--on", DAY, "--loads");
        assertThat(result.out()).contains("nothing is cleared to leave the shop");
    }

    @Test
    void theLoadsAreNotListedWithoutTheFlag() {
        ready();
        assertThat(shop.run("morning", "--on", DAY).out()).doesNotContain("ready to go out");
    }

    @Test
    void anOverdueReportIsListedAsLateAndStillStopsTheScript() {
        ready();
        shop.run(
                "ncr",
                "raise",
                "NCR-1",
                "--job",
                "24-0001",
                "--item",
                "GW-104",
                "--requirement",
                "API 1104 9.3.8",
                "--account",
                "slag",
                "--on",
                "2025-05-01",
                "--by",
                "0142");
        Shell.Result result = shop.run("morning", "--on", DAY);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("running late").doesNotContain("stops work today");
    }
}
