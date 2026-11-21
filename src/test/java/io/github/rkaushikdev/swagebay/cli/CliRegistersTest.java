package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRegistersTest {

    private static final String FOUND = "2025-04-07T23:30:00";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void anEmptyShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
    }

    private Shell.Result incident() {
        return shop.run(
                "register",
                "incident",
                "INC-1",
                "--kind",
                "OVEREXPOSURE",
                "--found",
                FOUND,
                "--site",
                "Come By Chance",
                "--by",
                "0142",
                "--account",
                "the source hung up");
    }

    private Shell.Result receive(String lot, int sheets, String expires) {
        return shop.run(
                "register",
                "receive",
                lot,
                "--sheets",
                String.valueOf(sheets),
                "--received",
                "2025-04-01",
                "--expires",
                expires);
    }

    private Shell.Result receive() {
        return receive("L-1", 100, "2026-04-07");
    }

    private static void assertConflict(Shell.Result result) {
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(result.failure()).isEqualTo(ConflictError.CODE);
    }

    @Test
    void anIncidentIsRecordedAndPrintsWhenItIsDue() {
        Shell.Result result = incident();
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("INC-1", "due 2025-04-08 23:30");
    }

    @Test
    void theSameReferenceTwiceIsAConflict() {
        incident();
        assertConflict(incident());
    }

    /**
     * An occurrence is looked up by its name, and a name nothing publishes is not a refusal the
     * domain makes: it escapes the entry point as it is, with its stack trace and the general status.
     */
    @Test
    void anOccurrenceNobodyPublishesIsRefused() {
        assertThatThrownBy(() -> shop.run(
                        "register",
                        "incident",
                        "INC-2",
                        "--kind",
                        "SOMETHING_ELSE",
                        "--found",
                        FOUND,
                        "--site",
                        "s",
                        "--by",
                        "0142",
                        "--account",
                        "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOutstandingListNamesWhatIsUnreported() {
        incident();
        assertThat(shop.run("register", "outstanding").out()).contains("INC-1");
    }

    @Test
    void reportingItEmptiesTheOutstandingList() {
        incident();
        assertThat(shop.run("register", "notify", "INC-1", "--at", FOUND).out()).contains("reported");
        assertThat(shop.run("register", "outstanding").output().strip()).isEmpty();
    }

    @Test
    void reportingSomethingThatIsNotThereIsNotFound() {
        Shell.Result result = shop.run("register", "notify", "INC-9", "--at", FOUND);
        assertThat(result.code()).isEqualTo(Main.EXIT_NOT_FOUND);
        assertThat(result.failure()).isEqualTo(NotFoundError.CODE);
    }

    @Test
    void aCalibrationIsRecordedAndTheInstrumentStopsBeingExpired() {
        Shell.Result result = shop.run("register", "calibrate", "LU-1", "--on", "2025-04-01", "--due", "2026-04-01");
        assertThat(result.out()).contains("calibrated until 2026-04-01");
        assertThat(shop.run("register", "expired", "--on", "2025-05-01")
                        .output()
                        .strip())
                .isEmpty();
        assertThat(shop.run("register", "expired", "--on", "2026-05-01").out()).contains("LU-1");
    }

    @Test
    void theSameCalibrationTwiceIsAConflict() {
        String[] args = {"register", "calibrate", "LU-1", "--on", "2025-04-01", "--due", "2026-04-01"};
        shop.run(args);
        assertConflict(shop.run(args));
    }

    @Test
    void filmIsReceivedAndListed() {
        assertThat(receive().out()).contains("received L-1, 100 sheets");
        String listing = shop.run("register", "stock", "--on", "2025-04-07").out();
        assertThat(listing).contains("L-1", "100");
    }

    @Test
    void theStockListComesBackShortestDatedFirst() {
        receive("L-1", 100, "2026-06-01");
        receive("L-2", 100, "2025-06-01");
        List<String> lines = shop.run("register", "stock", "--on", "2025-04-07").lines();
        assertThat(lines.get(2)).startsWith("L-2");
    }

    @Test
    void issuingFilmNamesTheLotsAndWhatIsLeft() {
        receive("L-1", 60, "2025-06-01");
        receive("L-2", 100, "2026-06-01");
        String output = shop.run("register", "issue", "--sheets", "80", "--on", "2025-04-07")
                .out();
        assertThat(output).contains("L-1 60", "L-2 20", "80 sheets left");
    }

    @Test
    void askingForMoreThanThereIsIsAConflict() {
        receive("L-1", 20, "2026-04-07");
        assertConflict(shop.run("register", "issue", "--sheets", "80", "--on", "2025-04-07"));
    }

    @Test
    void anExpiredLotIsCalledOutOnTheStockList() {
        receive("L-1", 100, "2025-04-05");
        assertThat(shop.run("register", "stock", "--on", "2025-04-07").out()).contains("expired: L-1");
    }

    @Test
    void anEmptyRegisterPrintsNothingAtAll() {
        assertThat(shop.run("register", "outstanding").output().strip()).isEmpty();
        assertThat(shop.run("register", "expired", "--on", "2025-04-07")
                        .output()
                        .strip())
                .isEmpty();
        assertThat(shop.run("register", "stock", "--on", "2025-04-07").output().strip())
                .isEmpty();
    }
}
