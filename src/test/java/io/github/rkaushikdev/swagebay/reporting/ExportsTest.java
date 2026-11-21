package io.github.rkaushikdev.swagebay.reporting;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.testing.CsvRows;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExportsTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 11, 1);

    private static final List<WeldLine> WELDS = List.of(
            new WeldLine("GW-104", mm("10.97"), "w-19", 1, "accept", List.of()),
            new WeldLine("GW-104R1", mm("10.97"), "w-19", 1, "repair", List.of("9.3.8")));

    private static final List<RadiographLine> RADIOGRAPHS = List.of(new RadiographLine(
            "24-0417-R0001", "GW-104", 1, OPENED, "RT-001 rev 1", "film", true, Optional.of("0142"), 0));

    private static ReportPackage aPackage(
            boolean complete,
            List<WeldLine> welds,
            List<RadiographLine> radiographs,
            List<Report> reports,
            List<StoredImage> files) {
        return new ReportPackage(
                new JobNumber(24, 417),
                "Maritime Pipelines",
                "Come By Chance",
                Code.API_1104,
                "RT-001",
                OPENED,
                Optional.empty(),
                welds,
                radiographs,
                complete ? List.of() : List.of("GW-110 has no radiographs"),
                reports,
                files);
    }

    private static ReportPackage aPackage() {
        return aPackage(true, WELDS, RADIOGRAPHS, List.of(), List.of());
    }

    private static ReportPackage withWelds(List<WeldLine> welds) {
        return aPackage(true, welds, RADIOGRAPHS, List.of(), List.of());
    }

    private static ReportPackage withReports(Report... reports) {
        return aPackage(true, WELDS, RADIOGRAPHS, List.of(reports), List.of());
    }

    private static ReportPackage withFiles(StoredImage... files) {
        return aPackage(true, WELDS, RADIOGRAPHS, List.of(), List.of(files));
    }

    private static List<List<String>> rows(String text) {
        return CsvRows.parse(text);
    }

    private static int column(String sheet, String name) {
        return Exports.sheetNamed(sheet).columns().indexOf(name);
    }

    private static Report aReport(boolean closed) {
        Report report = new Report("NCR-1", "24-0417", "GW-104", "API 1104 9.3.8", "elongated slag", OPENED, "0142");
        if (closed) {
            report.decide(Disposition.USE_AS_IS, "CON-1", "");
            report.close(LocalDate.of(2024, 11, 5), "0303");
        }
        return report;
    }

    private static StoredImage aFile(String number, int revision) {
        ImageFormat imageFormat = revision == 0 ? ImageFormat.DICONDE : ImageFormat.JPEG;
        Purpose purpose = revision == 0 ? Purpose.RECORD : Purpose.VIEWING;
        return new StoredImage(
                number,
                ImageStore.expectedName(number, imageFormat, revision),
                imageFormat,
                revision == 0 ? 16 : 8,
                2048,
                512,
                1024,
                ImageStore.digestOf((number + "-" + revision).getBytes(StandardCharsets.UTF_8)),
                LocalDateTime.of(2024, 11, 3, 22, 0),
                purpose);
    }

    private static StoredImage aFile() {
        return aFile("24-0417-R0001", 0);
    }

    @Test
    void everySheetStartsWithItsHeader() {
        for (String what : Exports.sheets()) {
            List<String> first = rows(Exports.toText(aPackage(), what)).get(0);
            assertThat(first).isEqualTo(Exports.columnsOf(what));
        }
    }

    @Test
    void theWeldSheetNamesItsUnitInTheColumn() {
        assertThat(Exports.sheetNamed("welds").columns()).contains("wall_mm");
        List<List<String>> body = rows(Exports.toText(aPackage(), "welds"));
        assertThat(body.get(1).get(column("welds", "wall_mm"))).isEqualTo("10.97");
    }

    @Test
    void theWeldSheetCarriesTheDispositionAndTheClauses() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "welds"));
        assertThat(body.get(1).get(column("welds", "disposition"))).isEqualTo("accept");
        assertThat(body.get(2).get(column("welds", "clauses"))).isEqualTo("9.3.8");
    }

    @Test
    void aWeldWithNoClausesLeavesTheCellEmpty() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "welds"));
        assertThat(body.get(1).get(column("welds", "clauses"))).isEmpty();
    }

    @Test
    void theRadiographSheetWritesYesAndNoRatherThanTrueAndFalse() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "radiographs"));
        assertThat(body.get(1).get(column("radiographs", "admissible"))).isEqualTo("yes");
    }

    @Test
    void anUnreadFilmLeavesTheReaderCellEmpty() {
        List<RadiographLine> unread = List.of(new RadiographLine(
                "24-0417-R0002", "GW-104", 2, OPENED, "RT-001 rev 1", "film", true, Optional.empty(), 0));
        List<List<String>> body =
                rows(Exports.toText(aPackage(true, WELDS, unread, List.of(), List.of()), "radiographs"));
        assertThat(body.get(1).get(column("radiographs", "read_by"))).isEmpty();
    }

    @Test
    void theSummaryIsOneRowUnderItsHeader() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "summary"));
        assertThat(body).hasSize(2);
        assertThat(body.get(1).get(column("summary", "job"))).isEqualTo("24-0417");
        assertThat(body.get(1).get(column("summary", "complete"))).isEqualTo("yes");
    }

    @Test
    void anIncompletePackageSaysSoInTheSummary() {
        List<List<String>> body =
                rows(Exports.toText(aPackage(false, WELDS, RADIOGRAPHS, List.of(), List.of()), "summary"));
        assertThat(body.get(1).get(column("summary", "complete"))).isEqualTo("no");
    }

    @Test
    void aRateOnTwoWeldsIsLeftBlankRatherThanStated() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "summary"));
        assertThat(body.get(1).get(column("summary", "repair_rate"))).isEmpty();
        assertThat(body.get(1).get(column("summary", "repair_rate_basis"))).isEqualTo("1 of 2");
    }

    @Test
    void aRateWithADenominatorBehindItIsWrittenToFourPlaces() {
        List<WeldLine> welds = IntStream.rangeClosed(1, 40)
                .mapToObj(index -> new WeldLine(
                        String.format(Locale.ROOT, "GW-%03d", index), mm("10.97"), "w-19", 1, "accept", List.of()))
                .toList();
        List<List<String>> body = rows(Exports.toText(withWelds(welds), "summary"));
        assertThat(body.get(1).get(column("summary", "repair_rate"))).isEqualTo("0.0000");
    }

    @Test
    void anOpenJobLeavesTheCloseDateEmpty() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "summary"));
        assertThat(body.get(1).get(column("summary", "closed_on"))).isEmpty();
    }

    @Test
    void aFieldWithACommaInItIsQuotedProperly() {
        List<WeldLine> awkward =
                List.of(new WeldLine("GW-104", mm(10), "w-19, second shift", 1, "accept", List.of("9.3.1", "9.3.8")));
        String text = Exports.toText(withWelds(awkward), "welds");
        assertThat(text).contains("\"w-19, second shift\"");
        assertThat(rows(text).get(1).get(column("welds", "welder"))).isEqualTo("w-19, second shift");
    }

    @Test
    void everyLineEndsTheSameWayWhateverThePlatformIs() {
        String text = Exports.toText(aPackage(), "welds");
        assertThat(text).doesNotContain("\r");
        assertThat(text).endsWith("\n");
    }

    @Test
    void anExportNobodyPublishesIsRefused() {
        assertThatThrownBy(() -> Exports.toText(aPackage(), "everything"))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(
                                (String) caught.context().get("known"))
                        .contains("welds"));
    }

    @Test
    void theFileNamesCarryTheJobNumber() {
        Map<String, String> files = Exports.toFiles(aPackage());
        assertThat(files)
                .containsOnlyKeys(
                        "24-0417-files.csv",
                        "24-0417-radiographs.csv",
                        "24-0417-reports.csv",
                        "24-0417-summary.csv",
                        "24-0417-welds.csv");
        assertThat(files.get("24-0417-summary.csv")).startsWith("job,client");
    }

    @Test
    void theSheetsAreListedInAStableOrder() {
        assertThat(Exports.sheets()).containsExactly("files", "radiographs", "reports", "summary", "welds");
    }

    @Test
    void everySheetWritesItsOwnHeader() {
        ReportPackage reportPackage = aPackage();
        for (String name : Exports.sheets()) {
            List<String> header = rows(Exports.toText(reportPackage, name)).get(0);
            assertThat(header).isEqualTo(Exports.columnsOf(name));
        }
    }

    @Test
    void aSheetNobodyKnowsAboutNamesTheOnesThatExist() {
        assertThatThrownBy(() -> Exports.sheetNamed("gizmo"))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(
                                (String) caught.context().get("known"))
                        .contains("welds"));
    }

    @Test
    void theSheetListAndTheLookupCannotDrift() {
        assertThat(Exports.sheets())
                .isEqualTo(Exports.SHEETS.stream().map(CsvSheet::name).sorted().toList());
    }

    @Test
    void theReportSheetCarriesTheConcession() {
        List<List<String>> body = rows(Exports.toText(withReports(aReport(true)), "reports"));
        assertThat(body.get(1).get(column("reports", "concession"))).isEqualTo("CON-1");
        assertThat(body.get(1).get(column("reports", "closed_by"))).isEqualTo("0303");
    }

    @Test
    void anOpenReportGoesOutWithTheClosingColumnsEmpty() {
        List<List<String>> body = rows(Exports.toText(withReports(aReport(false)), "reports"));
        assertThat(body.get(1).get(column("reports", "disposition"))).isEmpty();
        assertThat(body.get(1).get(column("reports", "closed_on"))).isEmpty();
    }

    @Test
    void aJobWithNoReportsStillWritesAHeader() {
        List<List<String>> body = rows(Exports.toText(aPackage(), "reports"));
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).isEqualTo(Exports.sheetNamed("reports").columns());
    }

    @Test
    void theFileSheetCarriesTheDigestTheClientChecksAgainst() {
        List<List<String>> body = rows(Exports.toText(withFiles(aFile()), "files"));
        assertThat(body.get(1).get(column("files", "sha256"))).hasSize(64);
    }

    @Test
    void theFileSheetSaysWhichCopyIsTheRecord() {
        List<List<String>> body = rows(Exports.toText(withFiles(aFile(), aFile("24-0417-R0001", 1)), "files"));
        Map<String, String> purposes = new HashMap<>();
        for (List<String> line : body.subList(1, body.size())) {
            purposes.put(line.get(0), line.get(column("files", "purpose")));
        }
        assertThat(purposes.get("24-0417-R0001.dcm")).isEqualTo("record");
        assertThat(purposes.get("24-0417-R0001-v1.jpg")).isEqualTo("viewing");
    }

    @Test
    void theManifestIsASheetLikeTheRestNow() {
        assertThat(Exports.sheets()).contains("files", "reports");
        assertThat(Exports.toFiles(aPackage())).hasSameSizeAs(Exports.sheets());
    }
}
