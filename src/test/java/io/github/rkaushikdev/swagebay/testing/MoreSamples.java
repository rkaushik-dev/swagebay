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
 * Generated inputs of shapes {@link Samples} does not draw: three independent ranges, and runs laid
 * along a line.
 *
 * <p>The same discipline holds: the edge cases come first and the rest are drawn from a
 * {@link Random} seeded from the ranges themselves, so a failure names the same inputs on every run.
 */
public final class MoreSamples {

    /**
     * A stretch along a line.
     *
     * @param start where it starts, from the start of the line
     * @param span how long it is
     */
    public record Run(long start, long span) {}

    private MoreSamples() {}

    /**
     * Three arguments, the first in [minA, maxA], the second in [minB, maxB] and the third in
     * [minC, maxC]: every pairing of the first two ranges' edges, the third cycling through its
     * own, then {@link Samples#DRAWN} drawn triples.
     */
    public static Stream<Arguments> longTriples(long minA, long maxA, long minB, long maxB, long minC, long maxC) {
        List<Arguments> triples = new ArrayList<>();
        List<Long> edgesA = edges(minA, maxA);
        List<Long> edgesB = edges(minB, maxB);
        List<Long> edgesC = edges(minC, maxC);
        int next = 0;
        for (long a : edgesA) {
            for (long b : edgesB) {
                triples.add(Arguments.of(a, b, edgesC.get(next++ % edgesC.size())));
            }
        }
        Random random = new Random(Objects.hash(minA, maxA, minB, maxB, minC, maxC));
        for (int i = 0; i < Samples.DRAWN; i++) {
            triples.add(Arguments.of(draw(random, minA, maxA), draw(random, minB, maxB), draw(random, minC, maxC)));
        }
        return triples.stream();
    }

    /**
     * Lines of a length in [minLine, maxLine], each carrying up to {@code maxRuns} runs that lie on
     * it: a run starts in [0, max(line - 2 * minRun, 1)] and spans [minRun, min(maxRun, line -
     * start)]. The edge cases are bare lines, a single shortest run at either end, runs piled on one
     * spot, and runs packed end to end; drawn cases fill the rest.
     *
     * @return {@code examples} pairs of the line length and its list of {@link Run}s
     */
    public static Stream<Arguments> runsOnALine(
            long minLine, long maxLine, int maxRuns, long minRun, long maxRun, int examples) {
        List<Arguments> cases = new ArrayList<>();
        for (long line : List.of(minLine, maxLine)) {
            long lastStart = Math.max(line - 2 * minRun, 1);
            long longest = Math.min(maxRun, line);
            cases.add(Arguments.of(line, List.of()));
            cases.add(Arguments.of(line, List.of(new Run(0, minRun))));
            cases.add(Arguments.of(line, List.of(new Run(lastStart, Math.min(maxRun, line - lastStart)))));
            List<Run> piled = new ArrayList<>();
            List<Run> packed = new ArrayList<>();
            for (int i = 0; i < maxRuns; i++) {
                piled.add(new Run(0, longest));
                packed.add(new Run(i * minRun, minRun));
            }
            cases.add(Arguments.of(line, List.copyOf(piled)));
            cases.add(Arguments.of(line, List.copyOf(packed)));
        }
        Random random = new Random(Objects.hash(minLine, maxLine, maxRuns, minRun, maxRun));
        while (cases.size() < examples) {
            long line = draw(random, minLine, maxLine);
            int count = random.nextInt(maxRuns + 1);
            List<Run> runs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                long start = draw(random, 0, Math.max(line - 2 * minRun, 1));
                long span = draw(random, minRun, Math.min(maxRun, line - start));
                runs.add(new Run(start, span));
            }
            cases.add(Arguments.of(line, List.copyOf(runs)));
        }
        return cases.stream().limit(examples);
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
        return random.nextLong(min, max + 1);
    }
}
