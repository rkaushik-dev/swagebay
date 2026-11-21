package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.reporting.Exports;
import io.github.rkaushikdev.swagebay.testing.CsvRows;
import io.github.rkaushikdev.swagebay.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliReportsTest {

    private static final String OPENED = "2024-12-01";

    @TempDir
    Path tmp;

    private Shell shop;

    /** A shop with job 24-0001 open and weld GW-104 on it, nothing shot. */
    @BeforeEach
    void aJobWithOneWeld() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run(
                "job",
                "open",
                "--client",
                "Maritime Pipelines",
                "--site",
                "Come By Chance",
                "--procedure",
                "RT-001",
                "--on",
                OPENED);
        shop.run(
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
                OPENED);
    }

    private static int firstLine(List<String> lines, Predicate<String> wanted) {
        return IntStream.range(0, lines.size())
                .filter(at -> wanted.test(lines.get(at)))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void thePackagePrintsTheFaultsBeforeTheSummary() {
        List<String> lines = shop.run("report", "package", "24-0001").lines();
        int faults = firstLine(lines, line -> line.contains("no radiographs"));
        int summary = firstLine(lines, line -> line.startsWith("client"));
        assertThat(faults).isLessThan(summary);
    }

    @Test
    void thePackageCarriesTheJobDetails() {
        String output = shop.run("report", "package", "24-0001").out();
        assertThat(output).contains("report package 24-0001");
        assertThat(output).contains("client          Maritime Pipelines");
        assertThat(output).contains("complete        no");
    }

    @Test
    void theRepairRateIsPrintedWithTheCountItCameFrom() {
        assertThat(shop.run("report", "package", "24-0001").out()).contains("repair rate     0 of 0, too few to rate");
    }

    @Test
    void theWeldTableListsEveryWeld() {
        List<String> lines = shop.run("report", "welds", "24-0001").lines();
        assertThat(Text.words(lines.get(0)).subList(0, 3)).containsExactly("weld", "wall", "welder");
        assertThat(lines.get(2)).startsWith("GW-104");
    }

    @Test
    void aWeldWithNoFilmsHasNoDisposition() {
        assertThat(shop.run("report", "welds", "24-0001").out()).contains("none");
    }

    @Test
    void theTraceabilityCommandSaysSoWhenNothingIsBroken() {
        assertThat(Text.strip(shop.run("report", "traceability", "24-0001").out()))
                .isEqualTo("every chain holds");
    }

    @Test
    void theExportWritesAFileForEverySheetAndNamesThem() {
        Path into = tmp.resolve("out");
        String output = shop.run("report", "export", "24-0001", "--into", into.toString())
                .out();
        List<String> written =
                output.lines().map(Text::strip).filter(line -> !line.isEmpty()).toList();
        assertThat(written).hasSameSizeAs(Exports.sheets());
        assertThat(written).allMatch(name -> name.endsWith(".csv"));
        assertThat(into.resolve("24-0001-summary.csv")).exists();
        assertThat(into.resolve("24-0001-reports.csv")).exists();
        assertThat(into.resolve("24-0001-files.csv")).exists();
    }

    @Test
    void theExportMakesTheDirectoryIfItIsNotThere() {
        Path into = tmp.resolve("deep").resolve("nested");
        shop.run("report", "export", "24-0001", "--into", into.toString());
        assertThat(into).isDirectory();
    }

    @Test
    void oneSheetCanBePrintedToStandardOutput() {
        List<List<String>> rows =
                CsvRows.parse(shop.run("report", "sheet", "24-0001", "summary").out());
        assertThat(rows.get(0).get(0)).isEqualTo("job");
        assertThat(rows.get(1).get(0)).isEqualTo("24-0001");
    }

    @Test
    void aSheetNobodyPublishesIsRefusedByTheChoice() {
        assertThat(shop.run("report", "sheet", "24-0001", "everything").code()).isNotZero();
    }

    @Test
    void aJobThatIsNotThereIsNotFound() {
        Shell.Result result = shop.run("report", "package", "24-0099");
        assertThat(result.code()).isEqualTo(EXIT_NOT_FOUND);
        assertThat(result.failure()).isEqualTo(NotFoundError.CODE);
    }

    @Test
    void theExportedCsvHasNoCarriageReturnsInIt() throws IOException {
        Path into = tmp.resolve("out");
        shop.run("report", "export", "24-0001", "--into", into.toString());
        String raw = new String(Files.readAllBytes(into.resolve("24-0001-welds.csv")), StandardCharsets.UTF_8);
        assertThat(raw).isNotEmpty().doesNotContain("\r");
    }
}
