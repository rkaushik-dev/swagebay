package io.github.rkaushikdev.swagebay.testing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Deterministic generated inputs for the property tests.
 *
 * <p>Each set is the edge cases a shrinking search lands on first (both ends of the range, zero,
 * one and minus one, and their neighbours) followed by values drawn from a {@link Random} seeded
 * from the range itself, so a failure names the same inputs on every run and every machine.
 */
public final class Samples {

    /** How many drawn values follow the edge cases. */
    public static final int DRAWN = 120;

    private Samples() {}

    /** Whole numbers in [min, max]. */
    public static List<Long> between(long min, long max) {
        Set<Long> values = new LinkedHashSet<>(edges(min, max));
        Random random = new Random(Objects.hash(min, max));
        for (int i = 0; i < DRAWN; i++) {
            values.add(draw(random, min, max));
        }
        return List.copyOf(values);
    }

    /** One argument per value in [min, max]. */
    public static Stream<Arguments> longs(long min, long max) {
        return between(min, max).stream().map(Arguments::of);
    }

    /** Two arguments, each in [min, max]. */
    public static Stream<Arguments> longPairs(long min, long max) {
        return longPairs(min, max, min, max);
    }

    /** Two arguments, the first in [minA, maxA] and the second in [minB, maxB]. */
    public static Stream<Arguments> longPairs(long minA, long maxA, long minB, long maxB) {
        List<Arguments> pairs = new ArrayList<>();
        for (long a : edges(minA, maxA)) {
            for (long b : edges(minB, maxB)) {
                pairs.add(Arguments.of(a, b));
            }
        }
        Random random = new Random(Objects.hash(minA, maxA, minB, maxB));
        for (int i = 0; i < DRAWN; i++) {
            pairs.add(Arguments.of(draw(random, minA, maxA), draw(random, minB, maxB)));
        }
        return pairs.stream();
    }

    /** Three arguments, each in [min, max]. */
    public static Stream<Arguments> longTriples(long min, long max) {
        List<Arguments> triples = new ArrayList<>();
        List<Long> edges = edges(min, max);
        for (long a : edges) {
            for (long b : edges) {
                triples.add(Arguments.of(a, b, edges.get(0)));
            }
        }
        Random random = new Random(Objects.hash(min, max, 3));
        for (int i = 0; i < DRAWN; i++) {
            triples.add(Arguments.of(draw(random, min, max), draw(random, min, max), draw(random, min, max)));
        }
        return triples.stream();
    }

    private static List<Long> edges(long min, long max) {
        Set<Long> edges = new LinkedHashSet<>();
        for (long candidate : new long[] {0, 1, -1, min, max, min + 1, max - 1, 2, 999, 1000, 1001}) {
            if (candidate >= min && candidate <= max) {
                edges.add(candidate);
            }
        }
        return List.copyOf(edges);
    }

    private static long draw(Random random, long min, long max) {
        if (random.nextInt(4) == 0) {
            long span = Math.min(max - min, 2000);
            return min + random.nextLong(span + 1);
        }
        return random.nextLong(min, max == Long.MAX_VALUE ? max : max + 1);
    }
}
