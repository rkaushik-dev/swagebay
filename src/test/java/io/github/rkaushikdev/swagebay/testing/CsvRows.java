package io.github.rkaushikdev.swagebay.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma separated text read back into rows, the way a client's integration reads an export: a field
 * in double quotes may hold commas, line breaks and doubled double quotes, and a row ends at a line
 * feed, or a carriage return and a line feed, outside quotes.
 */
public final class CsvRows {

    private CsvRows() {}

    /** Every row of {@code text}, each a list of its fields. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (quoted) {
                if (c == '"' && at + 1 < text.length() && text.charAt(at + 1) == '"') {
                    field.append('"');
                    at++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
                started = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                started = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && at + 1 < text.length() && text.charAt(at + 1) == '\n') {
                    at++;
                }
                row.add(field.toString());
                rows.add(List.copyOf(row));
                row.clear();
                field.setLength(0);
                started = false;
            } else {
                field.append(c);
                started = true;
            }
        }
        if (started || !field.isEmpty()) {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }
}
