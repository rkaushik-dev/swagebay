package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writing the report package out as CSV, which is what the client's system reads.
 *
 * <p>Two rules and both come from having sent a file that came back.
 *
 * <p>Every file starts with a header row and the header names are stable. A client integration keys
 * on them, so renaming a column is a breaking change even though nothing in this package would
 * notice.
 *
 * <p>A rate goes out with the count it came from, and goes out blank when the count is too small to
 * carry a percentage. That is what the {@code repair_rate} column holds, deliberately, because four
 * decimal places off a denominator of two were once sent to a client.
 *
 * <p>Lengths go out in millimetres to two places and the column name says so. A column called wall
 * with 10970 in it has been read as millimetres by somebody every time it has ever been sent, and
 * putting the unit in the name is the only thing that has ever stopped it.
 *
 * <p>Every row ends in a line feed whatever machine wrote it, and a sheet writes to any text stream
 * rather than to a path, which is what makes this testable without a temporary directory and what
 * lets the API stream a response.
 *
 * <p>A sheet is one {@link CsvSheet}: its name, its header and its rows together, and every lookup is
 * derived from the one list of them, so adding a sheet is adding it to {@link #SHEETS} and nowhere
 * else.
 */
public final class Exports {

    /** Every sheet the package is published as. */
    public static final List<CsvSheet> SHEETS = List.of(
            new CsvSheet(
                    "welds",
                    List.of("weld", "wall_mm", "welder", "radiographs", "disposition", "clauses"),
                    Exports::weldRows),
            new CsvSheet(
                    "radiographs",
                    List.of(
                            "radiograph",
                            "weld",
                            "exposure",
                            "exposed_on",
                            "technique",
                            "medium",
                            "admissible",
                            "read_by",
                            "indications"),
                    Exports::radiographRows),
            new CsvSheet(
                    "summary",
                    List.of(
                            "job",
                            "client",
                            "site",
                            "code",
                            "procedure",
                            "opened_on",
                            "closed_on",
                            "welds",
                            "welds_shot",
                            "welds_repaired",
                            "repair_rate",
                            "repair_rate_basis",
                            "radiographs",
                            "complete"),
                    Exports::summaryRows),
            new CsvSheet(
                    "reports",
                    List.of(
                            "reference",
                            "item",
                            "requirement",
                            "disposition",
                            "concession",
                            "removal",
                            "raised_on",
                            "raised_by",
                            "closed_on",
                            "closed_by"),
                    Exports::reportRows),
            new CsvSheet(
                    "files",
                    List.of(
                            "filename",
                            "radiograph",
                            "format",
                            "purpose",
                            "revision",
                            "bit_depth",
                            "width",
                            "height",
                            "bytes",
                            "sha256"),
                    Exports::fileRows));

    private static final Map<String, CsvSheet> BY_NAME = byName();

    private Exports() {}

    /**
     * The sheet published under that name.
     *
     * @throws ValidationError for a name no sheet is published under, naming the ones that are
     */
    public static CsvSheet sheetNamed(String what) {
        CsvSheet sheet = BY_NAME.get(what);
        if (sheet == null) {
            throw new ValidationError("no such export", Context.of("what", what, "known", String.join(", ", sheets())));
        }
        return sheet;
    }

    /**
     * One named sheet of the package, as CSV text.
     *
     * @throws ValidationError for a name no sheet is published under
     */
    public static String toText(ReportPackage reportPackage, String what) {
        StringBuilder text = new StringBuilder();
        sheetNamed(what).write(reportPackage, text);
        return text.toString();
    }

    /** The name of every published sheet, sorted. */
    public static List<String> sheets() {
        return BY_NAME.keySet().stream().sorted().toList();
    }

    /** Every sheet as CSV text, by the file name it is saved under, {@code 24-0417-summary.csv}, in name order. */
    public static Map<String, String> toFiles(ReportPackage reportPackage) {
        Map<String, String> files = new LinkedHashMap<>();
        for (String what : sheets()) {
            files.put(reportPackage.job() + "-" + what + ".csv", toText(reportPackage, what));
        }
        return Collections.unmodifiableMap(files);
    }

    /**
     * The header of a named sheet.
     *
     * @throws ValidationError for a name no sheet is published under
     */
    public static List<String> columnsOf(String what) {
        return sheetNamed(what).columns();
    }

    private static Map<String, CsvSheet> byName() {
        Map<String, CsvSheet> named = new LinkedHashMap<>();
        SHEETS.forEach(sheet -> named.put(sheet.name(), sheet));
        return Collections.unmodifiableMap(named);
    }

    /** Millimetres to two places, ties to even. */
    private static String millimetres(Length value) {
        return Decimals.halfEven(value.mm(), 2).toPlainString();
    }

    /**
     * The rate to four places, or nothing at all when the denominator cannot carry one. An empty cell
     * is the honest answer to a rate on four welds: a number there would be read, and read as a trend.
     */
    private static String rate(Rate rate) {
        return rate.isReportable() ? Decimals.halfEven(rate.value(), 4).toPlainString() : "";
    }

    private static String day(Optional<LocalDate> day) {
        return day.map(Times::iso).orElse("");
    }

    private static List<List<String>> weldRows(ReportPackage reportPackage) {
        return reportPackage.welds().stream()
                .map(line -> List.of(
                        line.weld(),
                        millimetres(line.wall()),
                        line.welder(),
                        String.valueOf(line.radiographs()),
                        line.disposition(),
                        String.join(" ", line.clauses())))
                .toList();
    }

    private static List<List<String>> radiographRows(ReportPackage reportPackage) {
        return reportPackage.radiographs().stream()
                .map(line -> List.of(
                        line.number(),
                        line.weld(),
                        String.valueOf(line.exposureIndex()),
                        Times.iso(line.exposedOn()),
                        line.techniqueReference(),
                        line.medium(),
                        line.admissible() ? "yes" : "no",
                        line.readBy().orElse(""),
                        String.valueOf(line.indications())))
                .toList();
    }

    private static List<List<String>> reportRows(ReportPackage reportPackage) {
        return reportPackage.reports().stream().map(Exports::reportRow).toList();
    }

    private static List<String> reportRow(Report report) {
        return List.of(
                report.reference(),
                report.item(),
                report.requirement(),
                report.disposition().map(Disposition::toString).orElse(""),
                report.concession(),
                report.removal(),
                Times.iso(report.raisedOn()),
                report.raisedBy(),
                day(report.closedOn()),
                report.closedBy());
    }

    private static List<List<String>> fileRows(ReportPackage reportPackage) {
        return reportPackage.files().stream().map(Exports::fileRow).toList();
    }

    private static List<String> fileRow(StoredImage image) {
        return List.of(
                image.filename(),
                image.radiographNumber(),
                image.imageFormat().toString(),
                image.purpose().toString(),
                String.valueOf(image.revision()),
                String.valueOf(image.bitDepth()),
                String.valueOf(image.width()),
                String.valueOf(image.height()),
                String.valueOf(image.byteSize()),
                image.digest());
    }

    private static List<List<String>> summaryRows(ReportPackage reportPackage) {
        Rate repairs = reportPackage.repairRate();
        return List.of(List.of(
                reportPackage.job().toString(),
                reportPackage.client(),
                reportPackage.site(),
                reportPackage.code().value(),
                reportPackage.procedureNumber(),
                Times.iso(reportPackage.openedOn()),
                day(reportPackage.closedOn()),
                String.valueOf(reportPackage.welds().size()),
                String.valueOf(reportPackage.weldsShot()),
                String.valueOf(reportPackage.weldsRepaired()),
                rate(repairs),
                repairs.repaired() + " of " + repairs.shot(),
                String.valueOf(reportPackage.radiographs().size()),
                reportPackage.isComplete() ? "yes" : "no"));
    }
}
