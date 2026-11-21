package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliShopTest {

    private static final String ASSAY = "2025-06-01T00:00:00";
    private static final String SWAP = "2025-08-14";
    private static final String DAY = "2025-08-18";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShopWithTwoCamerasAndTwoSources() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run("source", "add-device", "D-77", "880d");
        shop.run("source", "add-device", "D-99", "tk100");
        for (String[] source : new String[][] {{"IR-5512", "90"}, {"IR-6000", "80"}}) {
            shop.run("source", "register", source[0], "--isotope", "ir192", "--curies", source[1], "--assayed", ASSAY);
            shop.run("source", "wipe", source[0], "--at", ASSAY, "--by", "rp");
        }
    }

    private Shell.Result load(String device, String installed, String removed, String on) {
        List<String> args =
                new ArrayList<>(List.of("shop", "exchange", device, "--shipment", "QSA-1", "--on", on, "--by", "0142"));
        if (installed != null) {
            args.addAll(List.of("--in", installed));
        }
        if (removed != null) {
            args.addAll(List.of("--out", removed));
        }
        return shop.run(args.toArray(String[]::new));
    }

    private Shell.Result load() {
        return load("D-77", "IR-5512", null, SWAP);
    }

    private Shell.Result use(String source, String... more) {
        List<String> args = new ArrayList<>(
                List.of("shop", "use", "D-77", "--source", source, "--site", "Come By Chance", "--on", DAY));
        args.addAll(List.of(more));
        return shop.run(args.toArray(String[]::new));
    }

    private static void assertRefused(Shell.Result result, int status, String code) {
        assertThat(result.code()).isEqualTo(status);
        assertThat(result.failure()).isEqualTo(code);
    }

    @Test
    void aLoadIsRecordedAndReadsBack() {
        Shell.Result result = load();
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("IR-5512 into D-77");
        assertThat(shop.run("shop", "loading").out()).contains("IR-5512");
    }

    @Test
    void aDeviceHoldingNothingSaysSo() {
        assertThat(shop.run("shop", "loading").out()).contains("no device is holding a source");
    }

    @Test
    void aSecondLoadWithoutAnUnloadIsRefused() {
        load();
        assertRefused(load("D-77", "IR-6000", null, SWAP), Main.EXIT_CONFLICT, ConflictError.CODE);
    }

    @Test
    void aSourceAlreadyInAnotherDeviceIsRefused() {
        load();
        assertRefused(load("D-99", "IR-5512", null, SWAP), Main.EXIT_CONFLICT, ConflictError.CODE);
    }

    @Test
    void theWrongIsotopeForTheDeviceIsRefused() {
        assertRefused(load("D-99", "IR-5512", null, SWAP), Main.EXIT_CONFIGURATION, ConfigurationError.CODE);
    }

    @Test
    void aDeviceNobodyRegisteredIsNotFound() {
        assertRefused(load("D-11", "IR-5512", null, SWAP), Main.EXIT_NOT_FOUND, NotFoundError.CODE);
    }

    @Test
    void aSwapLeavesTheNewSourceIn() {
        load();
        shop.run("source", "wipe", "IR-6000", "--at", "2025-12-01T00:00:00", "--by", "rp");
        Shell.Result result = load("D-77", "IR-6000", "IR-5512", "2026-01-10");
        assertThat(result.code()).isZero();
        assertThat(shop.run("shop", "loading").out()).contains("IR-6000").doesNotContain("IR-5512");
    }

    @Test
    void theSourceThatCameOutIsInTransit() {
        load();
        load("D-77", null, "IR-5512", "2026-01-10");
        assertThat(shop.run("source", "list").out()).doesNotContain("IR-5512");
    }

    @Test
    void aDaysUseIsLogged() {
        load();
        Shell.Result result = use("IR-5512", "--by", "0142", "--exposures", "14");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("14 exposures");
    }

    @Test
    void aUseNamingASourceTheDeviceWasNotHoldingIsRefused() {
        load();
        Shell.Result result =
                shop.run("shop", "use", "D-77", "--source", "IR-6000", "--site", "site", "--on", DAY, "--by", "0142");
        assertRefused(result, Main.EXIT_CONFLICT, ConflictError.CODE);
    }

    @Test
    void aUseWithNobodyOnItIsAUsageError() {
        Shell.Result result = shop.run("shop", "use", "D-77", "--source", "IR-5512", "--site", "site", "--on", DAY);
        assertThat(result.code()).isNotZero();
    }

    @Test
    void exposuresCanBeToppedUp() {
        load();
        use("IR-5512", "--by", "0142", "--exposures", "14");
        Shell.Result result =
                shop.run("shop", "exposures", "D-77", "--site", "Come By Chance", "--on", DAY, "--add", "6");
        assertThat(result.out()).contains("20 exposures");
    }

    @Test
    void aWindowWithNoTripsHasNoGaps() {
        Shell.Result result = shop.run("shop", "gaps", "--since", "2025-08-01", "--until", "2025-08-31");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("nothing missing");
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        Shell.Result result = shop.run("shop", "gaps", "--since", "2025-08-31", "--until", "2025-08-01");
        assertRefused(result, Main.EXIT_VALIDATION, ValidationError.CODE);
    }

    @Test
    void aTripWithNothingLoggedShowsUpAsAGap() {
        load();
        shop.run("device", "inspect", "D-77", "--quarterly", "--on", "2025-08-01", "--by", "0303");
        shop.run("device", "inspect", "D-77", "--on", DAY, "--by", "0142");
        shop.run(
                "source",
                "send-out",
                "IR-5512",
                "--device",
                "D-77",
                "--crew",
                "0142",
                "--site",
                "Come By Chance",
                "--on",
                DAY);
        shop.run("source", "bring-back", "IR-5512", "--on", "2025-08-19", "--exposures", "14");
        Shell.Result result = shop.run("shop", "gaps", "--since", "2025-08-01", "--until", "2025-08-31");
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("D-77 on 2025-08-18", "D-77 on 2025-08-19");
    }
}
