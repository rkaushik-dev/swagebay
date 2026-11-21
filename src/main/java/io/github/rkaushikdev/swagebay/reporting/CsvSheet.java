package io.github.rkaushikdev.swagebay.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One CSV file of the report package: its name, its header, and where its rows come from.
 *
 * @param name the name the sheet is asked for by, and that its file name ends in
 * @param columns the header, which a client integration keys on
 * @param rows the rows of a package, each with a cell per column
 */
public record CsvSheet(String name, List<String> columns, Function<ReportPackage, List<List<String>>> rows) {

    /** A sheet; the columns are copied. */
    public CsvSheet {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rows, "rows");
        columns = List.copyOf(columns);
    }

    /**
     * Write the header and then every row of {@code reportPackage} to {@code stream}.
     *
     * @throws UncheckedIOException when the stream refuses the text
     */
    public void write(ReportPackage reportPackage, Appendable stream) {
        try {
            stream.append(Csv.row(columns));
            for (List<String> row : rows.apply(reportPackage)) {
                stream.append(Csv.row(row));
            }
        } catch (IOException refused) {
            throw new UncheckedIOException(refused);
        }
    }
}
