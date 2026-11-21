package io.github.rkaushikdev.swagebay.imaging;

import java.util.Comparator;

/**
 * One pixel on a panel, by column and row. Pixels order by column, then row.
 *
 * @param column the column, from zero
 * @param row the row, from zero
 */
public record Pixel(int column, int row) implements Comparable<Pixel> {

    private static final Comparator<Pixel> ORDER =
            Comparator.comparingInt(Pixel::column).thenComparingInt(Pixel::row);

    @Override
    public int compareTo(Pixel other) {
        return ORDER.compare(this, other);
    }

    /** {@code (20, 30)}, the way a violation names where a cluster starts. */
    @Override
    public String toString() {
        return "(" + column + ", " + row + ")";
    }
}
