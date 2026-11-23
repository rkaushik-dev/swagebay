package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ASME Section VIII UW-51, which is a different shape of rule entirely.
 *
 * <p>A pipeline crew that has only ever worked to API 1104 gets caught by this the first time they
 * shoot a vessel, because the two codes disagree about almost everything except cracks. API judges
 * each kind of imperfection against its own table. UW-51 does not care what caused the
 * imperfection at all. It asks one question about shape, planar or rounded, and then judges the
 * planar ones purely on length against a limit that slides with the wall.
 *
 * <p>The limit is a three part function. Up to three quarters of an inch of wall it is a flat
 * quarter inch. From two and a quarter inches it is a flat three quarters. Between them it is a
 * third of the wall, and the two flat sections join the sloping one exactly at the ends, which is
 * worth checking because a transcription error there is invisible on any single thickness.
 *
 * <p>Alignment is the other difference. UW-51 groups indications that line up and totals them over
 * a window of twelve wall thicknesses, not twelve inches, so the window itself moves with the wall.
 * And a group breaks whenever the gap between two of its members exceeds six times the longest one,
 * which means a group is not simply everything inside the window.
 */
public final class AsmeUw51 {

    /** The wall at or under which the flat quarter inch applies. */
    public static final Length THIN_LIMIT = Length.fromInch("0.75");

    /** The wall at or over which the flat three quarters applies. */
    public static final Length THICK_LIMIT = Length.fromInch("2.25");

    /** The longest planar indication allowed on thin wall. */
    public static final Length SHORT_ALLOWANCE = Length.fromInch("0.25");

    /** The longest planar indication allowed on heavy wall. */
    public static final Length LONG_ALLOWANCE = Length.fromInch("0.75");

    /** The aligned window, in wall thicknesses. */
    public static final int WINDOW_IN_WALLS = 12;

    /** A gap over this many times the longest member breaks a group. */
    public static final int GROUP_GAP_MULTIPLE = 6;

    private AsmeUw51() {}

    /**
     * Indications the code treats as one for the aggregate check.
     *
     * @param members the members, in order along the weld
     */
    public record Group(List<Indication> members) {

        /** A group; the members are copied. */
        public Group {
            members = List.copyOf(members);
        }

        /** The members' lengths added up. */
        public Length aggregate() {
            Length total = Length.ZERO;
            for (Indication member : members) {
                total = total.plus(member.length());
            }
            return total;
        }

        /** The longest member's length; nothing for an empty group. */
        public Length longest() {
            return longestOf(members);
        }

        /** From the first member's start to the last member's end; nothing for an empty group. */
        public Length span() {
            if (members.isEmpty()) {
                return Length.ZERO;
            }
            return members.get(members.size() - 1).endsAt().minus(members.get(0).startsAt());
        }

        /** How many members there are. */
        public int size() {
            return members.size();
        }
    }

    /**
     * The longest single planar indication UW-51 allows at that wall.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length elongatedLimit(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        if (wall.compareTo(THIN_LIMIT) <= 0) {
            return SHORT_ALLOWANCE;
        }
        if (wall.compareTo(THICK_LIMIT) >= 0) {
            return LONG_ALLOWANCE;
        }
        return wall.dividedBy(3);
    }

    /**
     * Twelve wall thicknesses, which is the window this code totals over.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Length windowFor(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        return wall.times(WINDOW_IN_WALLS);
    }

    /**
     * Break a run of indications wherever the gap exceeds six times the longest.
     *
     * <p>The break rule reads backwards at first. The gap is compared against the longest
     * indication in the group so far, so one long imperfection makes the group harder to break and
     * pulls more of its neighbours in with it.
     */
    public static List<Group> groupAligned(List<Indication> indications) {
        List<Indication> ordered = indications.stream()
                .sorted(Comparator.comparingLong(
                        indication -> indication.startsAt().um()))
                .toList();
        if (ordered.isEmpty()) {
            return List.of();
        }
        List<List<Indication>> groups = new ArrayList<>();
        groups.add(new ArrayList<>(List.of(ordered.get(0))));
        for (Indication indication : ordered.subList(1, ordered.size())) {
            List<Indication> current = groups.get(groups.size() - 1);
            Length longest = longestOf(current);
            Length gap = current.get(current.size() - 1).gapTo(indication);
            if (gap.compareTo(longest.times(GROUP_GAP_MULTIPLE)) > 0) {
                groups.add(new ArrayList<>(List.of(indication)));
            } else {
                current.add(indication);
            }
        }
        return groups.stream().map(Group::new).toList();
    }

    /**
     * Every UW-51 objection to a set of indications at that wall: cracks under b1, planar
     * indications over the sliding limit under b2, aligned groups over the wall under b3, and
     * rounded indications over the Appendix 4 limits under b4.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static List<Uw51Finding> evaluate(List<Indication> indications, Length wall) {
        Length limit = elongatedLimit(wall);
        List<Indication> planar = indications.stream()
                .filter(indication -> indication.kind().isPlanar())
                .toList();
        List<Uw51Finding> out = new ArrayList<>(cracks(indications));
        out.addAll(overTheSlidingLimit(planar, limit, wall));
        out.addAll(alignedOverTheWall(planar, wall));
        out.addAll(RoundedIndications.evaluate(indications, wall));
        return List.copyOf(out);
    }

    /** b1: every crack, whatever its length. */
    private static List<Uw51Finding> cracks(List<Indication> indications) {
        return indications.stream()
                .filter(indication -> indication.kind().isAlwaysRejectable())
                .map(indication ->
                        new Uw51Finding("b1", "a crack is not acceptable at any length", List.of(indication)))
                .toList();
    }

    /** b2: planar indications other than cracks longer than the limit at that wall. */
    private static List<Uw51Finding> overTheSlidingLimit(List<Indication> planar, Length limit, Length wall) {
        return planar.stream()
                .filter(indication -> !indication.kind().isAlwaysRejectable())
                .filter(indication -> indication.length().compareTo(limit) > 0)
                .map(indication -> new Uw51Finding(
                        "b2",
                        indication.length().formatMm(1) + " is over the " + limit.formatMm(1) + " allowed at "
                                + wall.formatMm(1) + " wall",
                        List.of(indication)))
                .toList();
    }

    /** b3: aligned groups of two or more inside the window whose lengths total more than the wall. */
    private static List<Uw51Finding> alignedOverTheWall(List<Indication> planar, Length wall) {
        Length window = windowFor(wall);
        return groupAligned(planar).stream()
                .filter(group -> group.size() >= 2)
                .filter(group -> group.span().compareTo(window) <= 0)
                .filter(group -> group.aggregate().compareTo(wall) > 0)
                .map(group -> new Uw51Finding(
                        "b3",
                        group.size() + " aligned indications total "
                                + group.aggregate().formatMm(1) + " in " + window.formatMm(1) + ", over the "
                                + wall.formatMm(1) + " allowed",
                        group.members()))
                .toList();
    }

    /** Whether the three part limit joins up at both ends, to the micrometre. */
    public static boolean slidingLimitIsContinuous() {
        Length atThin = elongatedLimit(THIN_LIMIT);
        Length atThick = elongatedLimit(THICK_LIMIT);
        Length justOverThin = elongatedLimit(new Length(THIN_LIMIT.um() + 1));
        Length justUnderThick = elongatedLimit(new Length(THICK_LIMIT.um() - 1));
        return Math.abs(atThin.um() - justOverThin.um()) <= 1 && Math.abs(atThick.um() - justUnderThick.um()) <= 1;
    }

    /**
     * An indication's length as a fraction of the wall, which is what the report quotes.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static BigDecimal fractionOfWall(Indication indication, Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        return indication.length().ratio(wall);
    }

    private static Length longestOf(List<Indication> members) {
        return members.stream()
                .map(Indication::length)
                .max(Comparator.naturalOrder())
                .orElse(Length.ZERO);
    }
}
