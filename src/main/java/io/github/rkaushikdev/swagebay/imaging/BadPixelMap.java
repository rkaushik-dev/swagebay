package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The bad pixels on one panel, and why the count is the least interesting number in it.
 *
 * <p>Every panel ships with dead pixels and nobody minds. What matters is where they are and
 * whether they touch. A thousand isolated dead pixels across four million are invisible after
 * interpolation, because each is surrounded by eight good neighbours that can fill it in. Nine dead
 * pixels in a square are not: the middle one has no good neighbour, and a flaw that lands there is
 * gone. So the map is analysed as {@link Cluster}s rather than counted.
 *
 * @param columns the panel width in pixels
 * @param rows the panel height in pixels
 * @param pixels the bad pixels
 */
public record BadPixelMap(int columns, int rows, Set<Pixel> pixels) {

    private static final int[][] NEIGHBOURS = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

    private static final Comparator<Cluster> LARGEST_FIRST =
            Comparator.comparingInt(Cluster::size).reversed().thenComparing(Cluster::first);

    /**
     * A map; the pixels are copied and held in pixel order.
     *
     * @throws ValidationError for a panel with no size, or a pixel off it
     */
    public BadPixelMap {
        if (columns <= 0 || rows <= 0) {
            throw new ValidationError("a map needs a panel size");
        }
        pixels = Collections.unmodifiableSortedSet(new TreeSet<>(pixels));
        for (Pixel pixel : pixels) {
            if (pixel.column() < 0 || pixel.column() >= columns || pixel.row() < 0 || pixel.row() >= rows) {
                throw new ValidationError(
                        "a bad pixel is off the panel", Context.of("column", pixel.column(), "row", pixel.row()));
            }
        }
    }

    /** How many pixels are bad. */
    public int total() {
        return pixels.size();
    }

    /** The bad share of the whole panel. */
    public BigDecimal fraction() {
        return Decimals.divide(total(), (long) columns * rows);
    }

    /** The connected groups, joined eight ways, largest first and then by their first pixel. */
    public List<Cluster> clusters() {
        Set<Pixel> remaining = new HashSet<>(pixels);
        List<Cluster> found = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Pixel seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<Pixel> group = new HashSet<>();
            group.add(seed);
            Deque<Pixel> frontier = new ArrayDeque<>();
            frontier.push(seed);
            while (!frontier.isEmpty()) {
                Pixel current = frontier.pop();
                for (int[] step : NEIGHBOURS) {
                    Pixel neighbour = new Pixel(current.column() + step[0], current.row() + step[1]);
                    if (remaining.remove(neighbour)) {
                        group.add(neighbour);
                        frontier.push(neighbour);
                    }
                }
            }
            found.add(new Cluster(group));
        }
        found.sort(LARGEST_FIRST);
        return List.copyOf(found);
    }

    /** The biggest cluster, if there are any bad pixels at all. */
    public Optional<Cluster> largestCluster() {
        return clusters().stream().findFirst();
    }

    /** The clusters that are lines. */
    public List<Cluster> lines() {
        return clusters().stream().filter(Cluster::isLine).toList();
    }

    /** Everything the default policy objects to. */
    public List<String> violations() {
        return violations(BadPixelPolicy.DEFAULT);
    }

    /** Everything the policy objects to: the fraction first, then each cluster largest first. */
    public List<String> violations(BadPixelPolicy policy) {
        List<String> out = new ArrayList<>();
        BigDecimal fraction = fraction();
        if (fraction.compareTo(policy.maxFraction()) > 0) {
            out.add(total() + " bad pixels is " + Decimals.halfEven(fraction, 4).toPlainString()
                    + " of the panel, over " + policy.maxFraction());
        }
        for (Cluster cluster : clusters()) {
            if (cluster.isLine()) {
                if (cluster.length() > policy.maxLine()) {
                    out.add("a bad line of " + cluster.length() + " pixels at " + cluster.first());
                }
            } else if (cluster.size() > policy.maxCluster()) {
                out.add("a cluster of " + cluster.size() + " pixels at " + cluster.first());
            }
        }
        return List.copyOf(out);
    }

    /** Whether the default policy has no objection. */
    public boolean passes() {
        return passes(BadPixelPolicy.DEFAULT);
    }

    /** Whether the policy has no objection. */
    public boolean passes(BadPixelPolicy policy) {
        return violations(policy).isEmpty();
    }
}
