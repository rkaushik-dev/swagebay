package io.github.rkaushikdev.swagebay.geometry;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The weld map: every arc on one girth weld, the sketch that proves the whole circumference was shot.
 *
 * <p>A girth weld is a circle and the films are arcs of it. Complete coverage means the arcs, taken
 * together, close the circle with overlap at every join. That is easy to say and awkward to check,
 * because the arcs wrap: an arc from three hundred and thirty degrees to thirty degrees crosses
 * twelve o'clock and is one arc, not two.
 *
 * <p>So everything here works in degrees clockwise from twelve, arcs carry their own wrap, and the
 * gap check walks the circle once from a fixed start rather than sorting and comparing neighbours.
 * Sorting arcs that wrap puts the one crossing twelve o'clock in the wrong place and produces a gap
 * report that says the weld is uncovered at the top when it is the only part that was shot twice.
 *
 * <p>The map is also what a repair crew reads. An indication at four o'clock on film two has to be
 * findable on the pipe, and the only thing that makes that work is the film knowing which arc it
 * covers.
 *
 * @param arcs the films, in the order they were laid out
 */
public record WeldMap(List<Arc> arcs) {

    /** One turn, in degrees. */
    public static final BigDecimal FULL_CIRCLE = BigDecimal.valueOf(360);

    /** The degree sign, U+00B0, written as a code point so the source stays plain ASCII. */
    public static final String DEGREE = Character.toString(0x00B0);

    private static final BigDecimal DEFAULT_STEP = new BigDecimal("0.5");
    private static final BigDecimal DEFAULT_OVERLAP = BigDecimal.TEN;
    private static final BigDecimal LARGEST_STEP = BigDecimal.TEN;

    /** A map; the arcs are copied. */
    public WeldMap {
        arcs = List.copyOf(arcs);
    }

    /**
     * Fold any bearing into zero up to but not including three sixty.
     *
     * <p>A decimal remainder takes the sign of the dividend, so a bearing of minus ten comes back
     * from it as minus ten rather than as three hundred and fifty, and is turned once more. Missing
     * that is the bug that made an arc crossing twelve read as two arcs.
     */
    public static BigDecimal wrap(BigDecimal degrees) {
        BigDecimal turned = degrees.remainder(FULL_CIRCLE, Decimals.CONTEXT);
        return turned.signum() < 0 ? Decimals.add(turned, FULL_CIRCLE) : turned;
    }

    /** The usual layout with a ten degree overlap at every join. */
    public static WeldMap evenArcs(int count) {
        return evenArcs(count, DEFAULT_OVERLAP);
    }

    /**
     * The usual layout: equal films, labelled from one, with a stated overlap at every join; a span
     * that would pass a full turn is held to one.
     *
     * @throws ValidationError for fewer than one film or a negative overlap
     */
    public static WeldMap evenArcs(int count, BigDecimal overlapDegrees) {
        if (count < 1) {
            throw new ValidationError("a weld takes at least one film", Context.of("count", count));
        }
        if (overlapDegrees.signum() < 0) {
            throw new ValidationError("an overlap is not negative");
        }
        BigDecimal step = Decimals.divide(FULL_CIRCLE, count);
        BigDecimal span = Decimals.add(step, overlapDegrees);
        if (span.compareTo(FULL_CIRCLE) > 0) {
            span = FULL_CIRCLE;
        }
        List<Arc> arcs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            arcs.add(new Arc(String.valueOf(i + 1), wrap(Decimals.multiply(step, BigDecimal.valueOf(i))), span));
        }
        return new WeldMap(arcs);
    }

    /** The labels of the films that reach a bearing, in map order. */
    public List<String> covers(BigDecimal bearing) {
        return arcs.stream()
                .filter(arc -> arc.contains(bearing))
                .map(Arc::label)
                .toList();
    }

    /** What nothing reaches, walked in half degrees. */
    public List<Gap> gaps() {
        return gaps(DEFAULT_STEP);
    }

    /**
     * Walk the circle once from twelve and collect what nothing reaches.
     *
     * @throws ValidationError for a step that is not positive or is over ten degrees
     */
    public List<Gap> gaps(BigDecimal step) {
        if (step.signum() <= 0 || step.compareTo(LARGEST_STEP) > 0) {
            throw new ValidationError("a walk step is small and positive", Context.of("step", step.toString()));
        }
        return walk(step, covering -> covering == 0);
    }

    /** Whether the arcs close the circle. */
    public boolean isComplete() {
        return gaps().isEmpty();
    }

    /** The stretches only one film reaches, walked in half degrees. */
    public List<Gap> singleCovered() {
        return singleCovered(DEFAULT_STEP);
    }

    /** The stretches only one film reaches, which have no overlap to prove them. */
    public List<Gap> singleCovered(BigDecimal step) {
        return walk(step, covering -> covering == 1);
    }

    /** The runs of bearings where the number of films reaching them passes the test. */
    private List<Gap> walk(BigDecimal step, IntPredicate wanted) {
        List<Gap> found = new ArrayList<>();
        BigDecimal bearing = BigDecimal.ZERO;
        BigDecimal runStart = null;
        while (bearing.compareTo(FULL_CIRCLE) < 0) {
            boolean inRun = wanted.test(covers(bearing).size());
            if (inRun && runStart == null) {
                runStart = bearing;
            } else if (!inRun && runStart != null) {
                found.add(new Gap(runStart, Decimals.subtract(bearing, runStart)));
                runStart = null;
            }
            bearing = Decimals.add(bearing, step);
        }
        if (runStart != null) {
            found.add(new Gap(runStart, Decimals.subtract(FULL_CIRCLE, runStart)));
        }
        return stitch(found);
    }

    /**
     * Join a run that ends at three sixty to one that starts at zero.
     *
     * <p>The walk starts at twelve, so a stretch straddling twelve comes out as two runs, one at each
     * end of the walk. They are one stretch on the pipe, and reporting them as two is how a report
     * ends up claiming a weld has a gap at the top and another at the top.
     */
    private static List<Gap> stitch(List<Gap> found) {
        if (found.size() < 2) {
            return List.copyOf(found);
        }
        Gap first = found.get(0);
        Gap last = found.get(found.size() - 1);
        if (first.startsAt().signum() != 0
                || wrap(Decimals.add(last.startsAt(), last.span())).signum() != 0) {
            return List.copyOf(found);
        }
        List<Gap> stitched = new ArrayList<>(found.subList(1, found.size() - 1));
        stitched.add(new Gap(last.startsAt(), Decimals.add(last.span(), first.span())));
        return List.copyOf(stitched);
    }
}
