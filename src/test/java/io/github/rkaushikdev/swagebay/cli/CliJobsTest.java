package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_CONFLICT;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_NOT_FOUND;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_VALIDATION;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliJobsTest {

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void anEmptyShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
    }

    private Shell.Result openJob(String client) {
        return shop.run(
                "job",
                "open",
                "--client",
                client,
                "--site",
                "Come By Chance",
                "--procedure",
                "RT-001",
                "--on",
                "2024-10-30");
    }

    private Shell.Result openJob() {
        return openJob("Maritime Pipelines");
    }

    private Shell.Result addWeld() {
        return shop.run(
                "weld",
                "add",
                "24-0001",
                "GW-104",
                "--wall",
                "10.97 mm",
                "--diameter",
                "168.3 mm",
                "--cap",
                "2 mm",
                "--length",
                "529 mm",
                "--welder",
                "w-19",
                "--on",
                "2024-10-30");
    }

    @Test
    void aJobIsOpenedAndItsNumberPrinted() {
        Shell.Result result = openJob();
        assertThat(result.code()).isZero();
        assertThat(result.out().strip()).isEqualTo("opened 24-0001");
    }

    @Test
    void theNumbersRunOn() {
        openJob();
        assertThat(openJob("Other").out().strip()).isEqualTo("opened 24-0002");
    }

    @Test
    void anAcceptanceCodeNothingKnowsAboutIsRefused() {
        Shell.Result result = shop.run(
                "job",
                "open",
                "--client",
                "c",
                "--site",
                "s",
                "--procedure",
                "RT-001",
                "--code",
                "BS 2600",
                "--on",
                "2024-10-30");
        assertThat(result.code()).isEqualTo(EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void theOpenJobsAreListedAsATable() {
        openJob();
        List<String> lines = shop.run("job", "list", "--on", "2024-11-01").lines();
        assertThat(Text.words(lines.get(0))).containsExactly("job", "client", "site", "code", "welds");
        assertThat(lines.get(2)).contains("24-0001");
    }

    @Test
    void aJobIsShownWithWhatIsOutstanding() {
        openJob();
        Shell.Result result = shop.run("job", "show", "24-0001");
        assertThat(result.out())
                .contains("job        24-0001", "clear      no", "outstanding: the job has no welds on it");
    }

    @Test
    void aJobThatIsNotThereRaisesNotFound() {
        Shell.Result result = shop.run("job", "show", "24-0099");
        assertThat(result.code()).isEqualTo(EXIT_NOT_FOUND);
        assertThat(result.failure()).isEqualTo(NotFoundError.CODE);
    }

    @Test
    void aWeldIsAddedAndListed() {
        openJob();
        assertThat(addWeld().out().strip()).isEqualTo("added GW-104 to 24-0001");
        String listing = shop.run("weld", "list", "24-0001").out();
        assertThat(listing).contains("GW-104", "10.97 mm", "168.30 mm");
    }

    @Test
    void aPlateWeldPrintsPlateWhereTheDiameterWouldBe() {
        openJob();
        shop.run(
                "weld",
                "add",
                "24-0001",
                "GW-200",
                "--wall",
                "12 mm",
                "--length",
                "800 mm",
                "--welder",
                "w-19",
                "--on",
                "2024-10-30");
        assertThat(shop.run("weld", "list", "24-0001").out()).contains("plate");
    }

    @Test
    void theSameWeldTwiceIsAConflict() {
        openJob();
        addWeld();
        Shell.Result again = addWeld();
        assertThat(again.code()).isEqualTo(EXIT_CONFLICT);
        assertThat(again.failure()).isEqualTo(ConflictError.CODE);
    }

    @Test
    void aWallThatWillNotParseIsRefused() {
        openJob();
        Shell.Result result = shop.run(
                "weld", "add", "24-0001", "GW-104", "--wall", "ten mm", "--length", "529 mm", "--welder", "w-19");
        assertThat(result.code()).isEqualTo(EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void theWeldCountOnTheJobFollowsWhatWasAdded() {
        openJob();
        addWeld();
        assertThat(shop.run("job", "show", "24-0001").out()).contains("welds      1");
    }

    @Test
    void aJobWithNothingDoneRefusesToClose() {
        openJob();
        Shell.Result result = shop.run("job", "close", "24-0001", "--on", "2024-11-05");
        assertThat(result.code()).isEqualTo(EXIT_CONFLICT);
        assertThat(result.failure()).isEqualTo(ConflictError.CODE);
    }

    @Test
    void theExitStatusesAreTheOnesTheModulePublishes() {
        assertThat(List.of(EXIT_VALIDATION, EXIT_NOT_FOUND, EXIT_CONFLICT)).doesNotHaveDuplicates();
    }
}
