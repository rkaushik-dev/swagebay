package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.services.DoctorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDoctorTest {

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void anEmptyShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
    }

    private void anUnknownDeviceInspected() {
        shop.run("device", "inspect", "D-99", "--on", "2025-08-12", "--by", "0142");
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    @Test
    void aCleanDatabaseExitsZeroAndCountsTheChecks() {
        Shell.Result result = shop.run("doctor");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains(DoctorService.checkNames().size() + " checks, nothing found");
    }

    @Test
    void aFindingExitsFourAndNamesTheCheck() {
        anUnknownDeviceInspected();
        Shell.Result result = shop.run("doctor");
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("unknown device", "D-99");
    }

    @Test
    void onlyTheNamedChecksAreRun() {
        anUnknownDeviceInspected();
        Shell.Result result = shop.run("doctor", "--check", "self closed");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("1 checks, nothing found");
    }

    @Test
    void aCheckNothingKnowsAboutIsRefused() {
        assertThat(shop.run("doctor", "--check", "vibes").code()).isNotZero();
    }

    @Test
    void moreThanOneCheckCanBeNamed() {
        anUnknownDeviceInspected();
        Shell.Result result = shop.run("doctor", "--check", "self closed", "--check", "unknown device");
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("2 checks, 1 findings");
    }

    @Test
    void theDoctorWritesNothing() throws IOException, NoSuchAlgorithmException {
        anUnknownDeviceInspected();
        Path file = tmp.resolve("shop.mv.db");
        String before = sha256(file);
        shop.run("doctor");
        assertThat(sha256(file)).isEqualTo(before);
    }
}
