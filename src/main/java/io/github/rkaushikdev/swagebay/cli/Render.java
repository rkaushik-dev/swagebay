package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Printing tables and numbers so they line up in a terminal.
 *
 * <p>Nothing here uses colour and nothing uses box drawing characters. The output of this tool gets
 * pasted into emails, printed on a dot matrix in a site office, and read over a radio, and every one
 * of those turns a colour escape into garbage. Plain columns, two spaces between them, and a rule
 * made of dashes.
 *
 * <p>Numbers are right aligned and text is left aligned, which is the one formatting rule that is
 * worth the code because a column of right aligned exposure times can be scanned for the long one
 * and a column of left aligned ones cannot.
 *
 * <p>Widths come from the content rather than being fixed. A fixed width table with a long site name
 * in it wraps, and a wrapped table is worse than an ugly one. A width counts characters, not the
 * units a string is stored in, so a name with a character outside the basic plane still lines up.
 */
public final class Render {

    private static final String GAP = "  ";

    private Render() {}

    /**
     * The width of each column: its longest cell.
     *
     * @throws ValidationError when the rows do not all have the same number of columns
     */
    public static List<Integer> widths(List<? extends List<String>> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int columns = rows.getFirst().size();
        for (List<String> row : rows) {
            if (row.size() != columns) {
                throw new ValidationError(
                        "every row has the same number of columns", Context.of("expected", columns, "got", row.size()));
            }
        }
        List<Integer> sizes = new ArrayList<>(columns);
        for (int index = 0; index < columns; index++) {
            int widest = 0;
            for (List<String> row : rows) {
                widest = Math.max(widest, length(row.get(index)));
            }
            sizes.add(widest);
        }
        return List.copyOf(sizes);
    }

    /** {@link #table(List, List, Collection)} with every column left aligned. */
    public static String table(List<String> headings, List<? extends List<String>> rows) {
        return table(headings, rows, List.of());
    }

    /**
     * A plain table: the headings, a rule, and a line per row. The columns whose indexes are in
     * {@code numeric} are right aligned.
     *
     * @throws ValidationError for a table with no headings, or rows that do not all have as many
     *     columns as the headings
     */
    public static String table(List<String> headings, List<? extends List<String>> rows, Collection<Integer> numeric) {
        if (headings.isEmpty()) {
            throw new ValidationError("a table has headings");
        }
        Set<Integer> right = Set.copyOf(numeric);
        List<List<String>> everything = new ArrayList<>(rows.size() + 1);
        everything.add(headings);
        everything.addAll(rows);
        List<Integer> sizes = widths(everything);
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(line(headings, sizes, right));
        lines.add(sizes.stream().map(size -> "-".repeat(size)).collect(Collectors.joining(GAP)));
        for (List<String> row : rows) {
            lines.add(line(row, sizes, right));
        }
        return String.join("\n", lines);
    }

    private static String line(List<String> row, List<Integer> sizes, Set<Integer> right) {
        List<String> cells = new ArrayList<>(row.size());
        for (int index = 0; index < row.size(); index++) {
            String cell = row.get(index);
            String padding = " ".repeat(sizes.get(index) - length(cell));
            cells.add(right.contains(index) ? padding + cell : cell + padding);
        }
        return stripEnd(String.join(GAP, cells));
    }

    /** A two column label and value block, which is what a sheet prints as. Empty for no items. */
    public static String pairs(List<Map.Entry<String, String>> items) {
        if (items.isEmpty()) {
            return "";
        }
        int width = items.stream().mapToInt(item -> length(item.getKey())).max().orElseThrow();
        return items.stream()
                .map(item -> item.getKey() + " ".repeat(width - length(item.getKey())) + GAP + item.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** One line per item, each after a dash. */
    public static String bullets(List<String> items) {
        return bullets(items, "-");
    }

    /** One line per item, each after {@code marker}. */
    public static String bullets(List<String> items, String marker) {
        return items.stream().map(item -> marker + " " + item).collect(Collectors.joining("\n"));
    }

    /** The text, underlined with {@code =} to its own width. */
    public static String heading(String text) {
        return text + "\n" + "=".repeat(length(text));
    }

    /** A truth value as a word rather than a symbol. */
    public static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    /**
     * A decimal with the trailing zeros gone and no exponent: a hundred prints as {@code 100}, not
     * {@code 1E+2}, which is correct and is not what anybody wants on a wall chart.
     */
    public static String plain(BigDecimal value) {
        return Decimals.plain(value).toString();
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String stripEnd(String text) {
        int end = text.length();
        while (end > 0) {
            int point = text.codePointBefore(end);
            if (!Text.isSpace(point)) {
                break;
            }
            end -= Character.charCount(point);
        }
        return text.substring(0, end);
    }
}
