package io.github.rkaushikdev.swagebay.procedure;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every revision of every procedure, answerable as of any date.
 *
 * <p>A radiograph shot in March has to stay defensible in a review three years later, and the
 * question the reviewer asks is not what the procedure says now but what it said that morning. So
 * revisions are never edited and never deleted. A new revision supersedes an old one from its
 * effective date forward, and the old one stays resolvable forever.
 */
public final class ProcedureRegister {

    private static final Comparator<Revision> ORDER = Comparator.comparing(Revision::number)
            .thenComparing(Revision::effectiveOn)
            .thenComparingInt(Revision::revision);

    private final List<Revision> revisions = new ArrayList<>();

    /**
     * A register holding those revisions.
     *
     * @throws ValidationError when the same revision of a procedure is there twice
     */
    public ProcedureRegister(List<Revision> revisions) {
        this.revisions.addAll(sound(revisions));
    }

    /** Every revision, by procedure number, then effective date, then revision number. */
    public List<Revision> revisions() {
        return Collections.unmodifiableList(revisions);
    }

    /**
     * Register another revision. A refused revision leaves the register as it was.
     *
     * @throws ValidationError when that revision of that procedure is already registered
     */
    public void add(Revision revision) {
        List<Revision> grown = new ArrayList<>(revisions);
        grown.add(revision);
        List<Revision> sorted = sound(grown);
        revisions.clear();
        revisions.addAll(sorted);
    }

    /** Every revision of one procedure, in the order they took effect. */
    public List<Revision> history(String number) {
        return revisions.stream()
                .filter(revision -> revision.number().equals(number))
                .toList();
    }

    /**
     * The approved revision that governed work done that day.
     *
     * @throws ConfigurationError when no approved revision of the procedure was in force
     */
    public Revision effectiveOn(String number, LocalDate day) {
        return history(number).stream()
                .filter(revision ->
                        revision.isApproved() && !revision.effectiveOn().isAfter(day))
                .reduce((best, next) -> later(next, best) ? next : best)
                .orElseThrow(() -> new ConfigurationError(
                        "no approved revision of that procedure was in force",
                        Context.of("number", number, "day", Times.iso(day))));
    }

    /** The revision in force today. */
    public Revision current(String number, LocalDate today) {
        return effectiveOn(number, today);
    }

    /**
     * Whether another revision had taken over from this one by that day.
     *
     * @throws ConfigurationError when no approved revision of the procedure was in force
     */
    public boolean isSuperseded(Revision revision, LocalDate day) {
        return !effectiveOn(revision.number(), day).equals(revision);
    }

    /** The procedure numbers the register holds, sorted. */
    public List<String> numbers() {
        return revisions.stream().map(Revision::number).distinct().sorted().toList();
    }

    private static boolean later(Revision next, Revision best) {
        int byDay = next.effectiveOn().compareTo(best.effectiveOn());
        return byDay > 0 || (byDay == 0 && next.revision() > best.revision());
    }

    private static List<Revision> sound(List<Revision> revisions) {
        Set<Key> seen = new HashSet<>();
        for (Revision revision : revisions) {
            if (!seen.add(new Key(revision.number(), revision.revision()))) {
                throw new ValidationError(
                        "the same revision is registered twice", Context.of("label", revision.label()));
            }
        }
        List<Revision> sorted = new ArrayList<>(revisions);
        sorted.sort(ORDER);
        return sorted;
    }

    /** A procedure number and a revision of it, which the register holds once. */
    private record Key(String number, int revision) {}
}
