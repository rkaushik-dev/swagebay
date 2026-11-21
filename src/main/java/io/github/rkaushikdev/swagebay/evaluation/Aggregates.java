package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The any continuous twelve inches rule, done properly.
 *
 * <p>API 1104 does not say the total length of slag in the weld. It says the total length in any
 * continuous twelve inches of weld, and the difference is the whole problem. A weld with eight
 * separate half inch inclusions spread evenly over three feet passes. The same eight bunched into
 * one foot fails. Adding the lengths up and comparing once gets the first case right and the second
 * wrong.
 *
 * <p>So the window has to be slid, and slid to the worst position rather than to convenient ones.
 * The worst window always starts where some indication starts or ends exactly where some
 * indication ends, because a window that touches neither can be moved until it does without the
 * total falling. That gives a finite candidate list and an exact answer, which beats stepping the
 * window along in millimetres and hoping.
 *
 * <p>Indications that straddle the edge of the window are clipped to the part inside it. That is
 * the stricter reading of two defensible ones and it is what the written practice says to do, so
 * a client cannot be surprised by it.
 *
 * <p>A weld shorter than the window is not measured this way at all. The code falls back to a
 * percentage of the weld length, because a twelve inch window on a nine inch weld would let a nine
 * inch defect through.
 */
public final class Aggregates {

    /** Twelve inches, which is what every aggregate rule in the code is written on. */
    public static final Length STANDARD_WINDOW = Length.fromInch(12);

    private record Span(long low, long high) {}

    private static final Comparator<Span> SPAN_ORDER =
            Comparator.comparingLong(Span::low).thenComparingLong(Span::high);

    private Aggregates() {}

    /** How much of one indication lies inside a window; nothing when it lies outside. */
    public static Length clippedLength(Indication indication, Length start, Length end) {
        Length low = max(indication.startsAt(), start);
        Length high = min(indication.endsAt(), end);
        if (high.compareTo(low) <= 0) {
            return Length.ZERO;
        }
        return high.minus(low);
    }

    /**
     * The total clipped length inside one window, and how many contributed.
     *
     * <p>Overlapping indications are merged before the lengths are added. Two readers marking the
     * same slag line, or one indication recorded twice from two films that both cover the joint,
     * would otherwise be counted twice and the aggregate could come out longer than the window it
     * sits in. What the code says is the length of weld affected, which is what somebody with a rule
     * against the film would measure.
     *
     * @throws ValidationError for a window with no width
     */
    public static WindowTotal aggregateIn(List<Indication> indications, Length start, Length end) {
        if (end.compareTo(start) <= 0) {
            throw new ValidationError("a window has a positive width");
        }
        List<Span> spans = new ArrayList<>();
        int count = 0;
        for (Indication indication : indications) {
            Length inside = clippedLength(indication, start, end);
            if (inside.um() <= 0) {
                continue;
            }
            count++;
            long low = Math.max(indication.startsAt().um(), start.um());
            spans.add(new Span(low, low + inside.um()));
        }
        return new WindowTotal(new Length(unionLength(spans)), count);
    }

    /** The window position with the most in it on a {@link #STANDARD_WINDOW}. */
    public static Window worstWindow(List<Indication> indications, Length weldLength) {
        return worstWindow(indications, weldLength, STANDARD_WINDOW);
    }

    /**
     * The window position with the most in it. Exact, not sampled; on a weld shorter than the
     * window the window is the whole weld, and the first of several equal positions wins.
     *
     * @throws ValidationError for a weld or a window with no length
     */
    public static Window worstWindow(List<Indication> indications, Length weldLength, Length window) {
        if (weldLength.um() <= 0) {
            throw new ValidationError("a weld has a length");
        }
        if (window.um() <= 0) {
            throw new ValidationError("a window has a positive width");
        }
        Length span = min(window, weldLength);
        if (indications.isEmpty()) {
            return new Window(Length.ZERO, span, Length.ZERO, 0);
        }
        SortedSet<Long> candidates = new TreeSet<>();
        candidates.add(0L);
        for (Indication indication : indications) {
            candidates.add(indication.startsAt().um());
            candidates.add(indication.endsAt().um() - span.um());
        }
        long limit = weldLength.um() - span.um();

        Window best = new Window(Length.ZERO, span, Length.ZERO, 0);
        for (long raw : candidates) {
            long startUm = Math.min(Math.max(raw, 0), Math.max(limit, 0));
            Length start = new Length(startUm);
            Length end = new Length(startUm + span.um());
            WindowTotal inside = aggregateIn(indications, start, end);
            if (inside.total().compareTo(best.aggregate()) > 0) {
                best = new Window(start, end, inside.total(), inside.count());
            }
        }
        return best;
    }

    /**
     * What the short weld rule compares against: the aggregate over the weld length.
     *
     * @throws ValidationError for a weld with no length
     */
    public static BigDecimal fractionOfWeld(Length aggregate, Length weldLength) {
        if (weldLength.um() <= 0) {
            throw new ValidationError("a weld has a length");
        }
        return aggregate.ratio(weldLength);
    }

    /** Whether the weld is long enough for the {@link #STANDARD_WINDOW} rule to be the right one. */
    public static boolean windowApplies(Length weldLength) {
        return windowApplies(weldLength, STANDARD_WINDOW);
    }

    /** Whether the weld is at least as long as the window. */
    public static boolean windowApplies(Length weldLength, Length window) {
        return weldLength.compareTo(window) >= 0;
    }

    /** The most indications any {@link #STANDARD_WINDOW} holds. */
    public static int countInWorstWindow(List<Indication> indications, Length weldLength) {
        return countInWorstWindow(indications, weldLength, STANDARD_WINDOW);
    }

    /** The most indications any window holds, for the rules that cap a number rather than a total. */
    public static int countInWorstWindow(List<Indication> indications, Length weldLength, Length window) {
        if (indications.isEmpty()) {
            return 0;
        }
        Length span = min(window, weldLength);
        SortedSet<Long> candidates = new TreeSet<>();
        candidates.add(0L);
        indications.forEach(indication -> candidates.add(indication.startsAt().um()));
        long limit = Math.max(weldLength.um() - span.um(), 0);
        int best = 0;
        for (long raw : candidates) {
            long startUm = Math.min(raw, limit);
            WindowTotal inside = aggregateIn(indications, new Length(startUm), new Length(startUm + span.um()));
            best = Math.max(best, inside.count());
        }
        return best;
    }

    /** The length the spans cover between them, counting overlap once. */
    private static long unionLength(List<Span> spans) {
        long total = 0;
        long reach = Long.MIN_VALUE;
        for (Span span : spans.stream().sorted(SPAN_ORDER).toList()) {
            if (span.low() > reach) {
                total += span.high() - span.low();
                reach = span.high();
            } else if (span.high() > reach) {
                total += span.high() - reach;
                reach = span.high();
            }
        }
        return total;
    }

    private static Length max(Length left, Length right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static Length min(Length left, Length right) {
        return left.compareTo(right) <= 0 ? left : right;
    }
}
