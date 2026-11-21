package io.github.rkaushikdev.swagebay.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Generated maps and sets of whole numbers, the shapes {@link Samples} and {@link MoreSamples} do not
 * draw.
 *
 * <p>The same discipline holds: the edge cases come first (empty, a single entry at either end of
 * the range, as full as allowed, and for pairs of maps the same keys with the same and with other
 * values) and the rest are drawn from a {@link Random} seeded from the ranges themselves, so a
 * failure names the same inputs on every run. Maps and sets iterate in key order.
 */
public final class CollectionSamples {

    private CollectionSamples() {}

    /**
     * One map per case, of {@code minSize} to {@code maxSize} keys in [keyMin, keyMax], each to a
     * value in [valueMin, valueMax].
     */
    public static Stream<Arguments> maps(
            long keyMin, long keyMax, long valueMin, long valueMax, int minSize, int maxSize, int examples) {
        List<Map<Long, Long>> cases = new ArrayList<>();
        if (minSize == 0) {
            cases.add(Map.of());
        }
        cases.add(sorted(Map.of(keyMin, valueMin)));
        cases.add(sorted(Map.of(keyMax, valueMax)));
        cases.add(full(keyMin, keyMax, valueMin, valueMax, maxSize, 0));
        cases.add(full(keyMin, keyMax, valueMin, valueMax, maxSize, 1));
        Random random = new Random(Objects.hash(keyMin, keyMax, valueMin, valueMax, minSize, maxSize));
        while (cases.size() < examples) {
            cases.add(draw(random, keyMin, keyMax, valueMin, valueMax, minSize, maxSize));
        }
        return cases.stream().limit(examples).map(Arguments::of);
    }

    /**
     * Two maps per case, each of up to {@code maxSize} keys in [keyMin, keyMax] to values in
     * [valueMin, valueMax]. Half the drawn second maps reuse some of the first map's keys, half of
     * those with the first map's value, so shared keys with equal and with different values both turn
     * up often.
     */
    public static Stream<Arguments> mapPairs(
            long keyMin, long keyMax, long valueMin, long valueMax, int maxSize, int examples) {
        Map<Long, Long> full = full(keyMin, keyMax, valueMin, valueMax, maxSize, 0);
        Map<Long, Long> shifted = full(keyMin, keyMax, valueMin, valueMax, maxSize, 1);
        Map<Long, Long> half = new TreeMap<>();
        full.entrySet().stream()
                .limit(Math.max(1, maxSize / 2))
                .forEach(entry -> half.put(entry.getKey(), entry.getValue()));
        half.put(keyMax, valueMax);
        List<Arguments> cases = new ArrayList<>(List.of(
                Arguments.of(Map.of(), Map.of()),
                Arguments.of(Map.of(), full),
                Arguments.of(full, Map.of()),
                Arguments.of(full, full),
                Arguments.of(full, shifted),
                Arguments.of(full, sorted(half))));
        Random random = new Random(Objects.hash(keyMin, keyMax, valueMin, valueMax, maxSize, 2));
        while (cases.size() < examples) {
            Map<Long, Long> first = draw(random, keyMin, keyMax, valueMin, valueMax, 0, maxSize);
            Map<Long, Long> second = new TreeMap<>(draw(random, keyMin, keyMax, valueMin, valueMax, 0, maxSize));
            if (random.nextBoolean()) {
                for (Map.Entry<Long, Long> entry : first.entrySet()) {
                    if (second.size() >= maxSize) {
                        break;
                    }
                    if (random.nextBoolean()) {
                        second.put(
                                entry.getKey(),
                                random.nextBoolean() ? entry.getValue() : value(random, valueMin, valueMax));
                    }
                }
            }
            cases.add(Arguments.of(first, sorted(second)));
        }
        return cases.stream().limit(examples);
    }

    /** One set per case, of {@code minSize} to {@code maxSize} values in [min, max]. */
    public static Stream<Arguments> sets(long min, long max, int minSize, int maxSize, int examples) {
        return setsOf(min, max, minSize, maxSize, examples).stream().map(Arguments::of);
    }

    /**
     * Three arguments per case: a whole number in [minA, maxA], one in [minB, maxB], and a set of up
     * to {@code maxSize} values in [setMin, setMax]. Every pairing of the two ranges' ends meets an
     * empty set and a full one; drawn cases fill the rest.
     */
    public static Stream<Arguments> pairsWithASet(
            long minA, long maxA, long minB, long maxB, long setMin, long setMax, int maxSize, int examples) {
        List<Set<Long>> sets = setsOf(setMin, setMax, 0, maxSize, examples);
        Set<Long> full = sortedSet(lowest(setMin, setMax, maxSize));
        List<Arguments> cases = new ArrayList<>();
        for (long a : List.of(minA, maxA)) {
            for (long b : List.of(minB, maxB)) {
                cases.add(Arguments.of(a, b, Set.of()));
                cases.add(Arguments.of(a, b, full));
            }
        }
        Random random = new Random(Objects.hash(minA, maxA, minB, maxB, setMin, setMax, maxSize));
        int next = 0;
        while (cases.size() < examples) {
            cases.add(
                    Arguments.of(value(random, minA, maxA), value(random, minB, maxB), sets.get(next++ % sets.size())));
        }
        return cases.stream().limit(examples);
    }

    private static List<Set<Long>> setsOf(long min, long max, int minSize, int maxSize, int examples) {
        List<Set<Long>> cases = new ArrayList<>();
        if (minSize == 0) {
            cases.add(Set.of());
        }
        cases.add(sortedSet(List.of(min)));
        cases.add(sortedSet(List.of(max)));
        cases.add(sortedSet(lowest(min, max, maxSize)));
        Random random = new Random(Objects.hash(min, max, minSize, maxSize));
        while (cases.size() < examples) {
            long room = Math.min(maxSize, max - min + 1);
            int size = minSize + random.nextInt((int) (room - minSize) + 1);
            Set<Long> drawn = new TreeSet<>();
            while (drawn.size() < size) {
                drawn.add(value(random, min, max));
            }
            cases.add(Collections.unmodifiableSet(drawn));
        }
        return cases.subList(0, Math.min(examples, cases.size()));
    }

    /** The smallest {@code size} values in [min, max], in order. */
    private static List<Long> lowest(long min, long max, int size) {
        List<Long> values = new ArrayList<>();
        for (long candidate = min; candidate <= max && values.size() < size; candidate++) {
            values.add(candidate);
        }
        return values;
    }

    private static Map<Long, Long> full(long keyMin, long keyMax, long valueMin, long valueMax, int size, long offset) {
        Map<Long, Long> map = new TreeMap<>();
        long span = valueMax - valueMin + 1;
        for (long key = keyMin; key <= keyMax && map.size() < size; key++) {
            map.put(key, valueMin + Math.floorMod(key - keyMin + offset, span));
        }
        return sorted(map);
    }

    private static Map<Long, Long> draw(
            Random random, long keyMin, long keyMax, long valueMin, long valueMax, int minSize, int maxSize) {
        long room = Math.min(maxSize, keyMax - keyMin + 1);
        int size = minSize + random.nextInt((int) (room - minSize) + 1);
        Map<Long, Long> map = new TreeMap<>();
        while (map.size() < size) {
            map.put(value(random, keyMin, keyMax), value(random, valueMin, valueMax));
        }
        return sorted(map);
    }

    private static long value(Random random, long min, long max) {
        return random.nextLong(min, max + 1);
    }

    private static Map<Long, Long> sorted(Map<Long, Long> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(map)));
    }

    private static Set<Long> sortedSet(List<Long> values) {
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
