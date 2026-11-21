package io.github.rkaushikdev.swagebay.reporting;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Rows of comma separated text, the way a client's spreadsheet or integration reads them back.
 *
 * <p>A field is written as it is unless it holds a comma, a double quote, a carriage return or a
 * line feed; then it goes in double quotes, with every double quote in it written twice. A row with
 * one empty field is written as a quoted empty field, so that it is still a row when it is read
 * back. Every row ends in a line feed and nothing else, whatever machine wrote it.
 */
final class Csv {

    /** What ends every row. */
    static final String END_OF_ROW = "\n";

    private Csv() {}

    /** One row, its line feed included. */
    static String row(List<String> fields) {
        if (fields.size() == 1 && fields.get(0).isEmpty()) {
            return "\"\"" + END_OF_ROW;
        }
        return fields.stream().map(Csv::field).collect(Collectors.joining(",")) + END_OF_ROW;
    }

    private static String field(String value) {
        boolean quoted = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
        return quoted ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
