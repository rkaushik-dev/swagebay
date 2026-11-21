package io.github.rkaushikdev.swagebay.imaging;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A group of bad pixels connected side to side or corner to corner.
 *
 * <p>A diagonal pair counts as connected, because it still robs the pixel between them of two of
 * its neighbours. A cluster one pixel wide and longer than one pixel is a line, which is far worse
 * than a compact cluster of the same size because it cuts across the image rather than sitting in
 * it.
 *
 * @param pixels the pixels in the cluster, held in pixel order
 */
public record Cluster(Set<Pixel> pixels) {

    /**
     * The smallest rectangle holding the cluster, both ends included.
     *
     * @param left the lowest column
     * @param top the lowest row
     * @param right the highest column
     * @param bottom the highest row
     */
    public record Bounds(int left, int top, int right, int bottom) {}

    /** A cluster; the pixels are copied and sorted. */
    public Cluster {
        pixels = Collections.unmodifiableSortedSet(new TreeSet<>(pixels));
    }

    /** How many pixels it holds. */
    public int size() {
        return pixels.size();
    }

    /** The first pixel in pixel order, which is where a violation says the cluster is. */
    Pixel first() {
        return Collections.min(pixels);
    }

    /** The bounding rectangle. */
    public Bounds bounds() {
        return new Bounds(
                pixels.stream().mapToInt(Pixel::column).min().orElseThrow(),
                pixels.stream().mapToInt(Pixel::row).min().orElseThrow(),
                pixels.stream().mapToInt(Pixel::column).max().orElseThrow(),
                pixels.stream().mapToInt(Pixel::row).max().orElseThrow());
    }

    /** How many columns it spans. */
    public int width() {
        Bounds bounds = bounds();
        return bounds.right() - bounds.left() + 1;
    }

    /** How many rows it spans. */
    public int height() {
        Bounds bounds = bounds();
        return bounds.bottom() - bounds.top() + 1;
    }

    /** One pixel wide in either direction and longer than it is thick. */
    public boolean isLine() {
        return (width() == 1 || height() == 1) && size() > 1;
    }

    /** The longer of the two spans. */
    public int length() {
        return Math.max(width(), height());
    }
}
