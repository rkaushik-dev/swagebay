package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rounded indications on a vessel weld, which UW-51(b)(4) sends to Appendix 4 of Section VIII.
 *
 * <p>The elongated rules in UW-51 never look at porosity, and a reader who has only shot pipe is
 * used to porosity being judged on how wide one pore is. Appendix 4 asks three things instead.
 * Whether the pore is big enough to count at all, because the smallest ones on the film are below
 * what the appendix calls relevant at that wall. How big one may be, which is a quarter of the wall
 * capped at five thirty seconds of an inch, or a third of the wall capped at a quarter inch for one
 * that stands an inch clear of its neighbours. And how much lines up, which is the diameters added
 * along twelve walls of weld held under one wall.
 *
 * <p>The cluster charts are not here. They are a picture to lay over the film, and the reader
 * compares against them before the reading is recorded.
 */
public final class RoundedIndications {

    /** An indication this far from every other rounded one is isolated and allowed more. */
    public static final Length ISOLATION = Length.fromInch(1);

    /** The wall up to which the relevance threshold is a tenth of the wall. */
    public static final Length VERY_THIN_WALL = Length.fromInch("0.125");

    /** The wall over which the heavy wall allowances apply. */
    public static final Length HEAVY_WALL = Length.fromInch(2);

    /** The largest random rounded indication allowed at any wall. */
    public static final Length RANDOM_CAP = Length.fromInch("0.15625");

    /** The largest isolated rounded indication allowed up to two inches of wall. */
    public static final Length ISOLATED_CAP = Length.fromInch("0.25");

    /** The largest isolated rounded indication allowed over two inches of wall. */
    public static final Length HEAVY_ISOLATED_CAP = Length.fromInch("0.375");

    private RoundedIndications() {}

    /**
     * The smallest rounded indication that counts at that wall: a tenth of the wall under an eighth
     * of an inch, then a sixty fourth, a thirty second over a quarter inch, and a sixteenth over two.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length relevantFrom(Length wall) {
        requirePositive(wall);
        if (wall.compareTo(VERY_THIN_WALL) < 0) {
            return wall.dividedBy(10);
        }
        if (wall.compareTo(Length.fromInch("0.25")) <= 0) {
            return Length.fromInch("0.015625");
        }
        if (wall.compareTo(HEAVY_WALL) <= 0) {
            return Length.fromInch("0.03125");
        }
        return Length.fromInch("0.0625");
    }

    /**
     * The largest rounded indication allowed among others: a quarter of the wall or five thirty
     * seconds of an inch, whichever is smaller.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length randomLimit(Length wall) {
        requirePositive(wall);
        return smaller(wall.dividedBy(4), RANDOM_CAP);
    }

    /**
     * The largest isolated rounded indication allowed: a third of the wall or a quarter inch,
     * whichever is smaller, and three eighths flat over two inches of wall.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length isolatedLimit(Length wall) {
        requirePositive(wall);
        if (wall.compareTo(HEAVY_WALL) > 0) {
            return HEAVY_ISOLATED_CAP;
        }
        return smaller(wall.dividedBy(3), ISOLATED_CAP);
    }

    /** The rounded indications that count at that wall, in order along the weld. */
    public static List<Indication> relevant(List<Indication> indications, Length wall) {
        Length threshold = relevantFrom(wall);
        return indications.stream()
                .filter(indication -> indication.kind().isRounded())
                .filter(indication -> indication.width().compareTo(threshold) > 0)
                .toList();
    }

    /** Whether no other relevant rounded indication sits within an inch of this one. */
    public static boolean isIsolated(Indication indication, List<Indication> relevant) {
        return relevant.stream()
                .filter(other -> other != indication)
                .allMatch(other -> indication.gapTo(other).compareTo(ISOLATION) >= 0);
    }

    /**
     * Every Appendix 4 objection at that wall, each quoting UW-51(b4).
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static List<Uw51Finding> evaluate(List<Indication> indications, Length wall) {
        List<Indication> relevant = relevant(indications, wall);
        List<Uw51Finding> out = new ArrayList<>();
        for (Indication indication : relevant) {
            boolean isolated = isIsolated(indication, relevant);
            Length limit = isolated ? isolatedLimit(wall) : randomLimit(wall);
            if (indication.width().compareTo(limit) > 0) {
                out.add(new Uw51Finding(
                        "b4",
                        (isolated ? "an isolated " : "a ") + indication.width().formatMm(1)
                                + " rounded indication is over the " + limit.formatMm(1) + " allowed at "
                                + wall.formatMm(1) + " wall",
                        List.of(indication)));
            }
        }
        alignedOverTheWall(relevant, wall).ifPresent(out::add);
        return List.copyOf(out);
    }

    /** The worst twelve walls of weld when the diameters in it add up to the wall or more. */
    private static Optional<Uw51Finding> alignedOverTheWall(List<Indication> relevant, Length wall) {
        Length window = AsmeUw51.windowFor(wall);
        Length worst = Length.ZERO;
        List<Indication> worstMembers = List.of();
        for (Indication first : relevant) {
            Length end = first.startsAt().plus(window);
            List<Indication> inside = relevant.stream()
                    .filter(other -> other.startsAt().compareTo(first.startsAt()) >= 0
                            && other.startsAt().compareTo(end) < 0)
                    .toList();
            Length total = inside.stream().map(Indication::width).reduce(Length.ZERO, Length::plus);
            if (total.compareTo(worst) > 0) {
                worst = total;
                worstMembers = inside;
            }
        }
        if (worstMembers.size() < 2 || worst.compareTo(wall) < 0) {
            return Optional.empty();
        }
        return Optional.of(new Uw51Finding(
                "b4",
                worstMembers.size() + " rounded indications add up to " + worst.formatMm(1) + " in "
                        + window.formatMm(1) + ", not under the " + wall.formatMm(1) + " wall",
                worstMembers));
    }

    private static Length smaller(Length left, Length right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static void requirePositive(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
    }
}
