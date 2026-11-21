package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything found on one weld, in order along it.
 *
 * @param weldLength the length of the weld, positive
 * @param indications what was found, sorted by where each starts; indications that start together
 *     keep the order they were given in
 */
public record IndicationSet(Length weldLength, List<Indication> indications) {

    /**
     * A set of indications, sorted along the weld.
     *
     * @throws ValidationError for a weld with no length, or an indication that runs off its end
     */
    public IndicationSet {
        Objects.requireNonNull(weldLength, "weldLength");
        if (weldLength.um() <= 0) {
            throw new ValidationError("a weld has a length");
        }
        for (Indication indication : indications) {
            if (indication.endsAt().compareTo(weldLength) > 0) {
                throw new ValidationError(
                        "an indication runs off the end of the weld",
                        Context.of(
                                "kind", indication.kind().code(),
                                "ends_at_mm", indication.endsAt().mm().toString()));
            }
        }
        indications = indications.stream()
                .sorted(Comparator.comparingLong(
                        indication -> indication.startsAt().um()))
                .toList();
    }

    /** Those of one kind, in order along the weld. */
    public List<Indication> ofKind(Kind kind) {
        return indications.stream()
                .filter(indication -> indication.kind() == kind)
                .toList();
    }

    /** The planar ones, in order along the weld. */
    public List<Indication> planar() {
        return indications.stream()
                .filter(indication -> indication.kind().isPlanar())
                .toList();
    }

    /** Every kind present. */
    public Set<Kind> kinds() {
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        indications.forEach(indication -> kinds.add(indication.kind()));
        return Collections.unmodifiableSet(kinds);
    }

    /** The lengths of one kind, added up. */
    public Length totalLengthOf(Kind kind) {
        Length total = Length.ZERO;
        for (Indication indication : ofKind(kind)) {
            total = total.plus(indication.length());
        }
        return total;
    }

    /**
     * Everything that overlaps a window along the weld.
     *
     * @throws ValidationError for a window with no width
     */
    public List<Indication> within(Length start, Length end) {
        if (end.compareTo(start) <= 0) {
            throw new ValidationError("a window has a positive width");
        }
        return indications.stream()
                .filter(indication ->
                        indication.startsAt().compareTo(end) < 0 && start.compareTo(indication.endsAt()) < 0)
                .toList();
    }

    /** The largest of one kind on the dimension the code judges, the first found on a tie. */
    public Optional<Indication> longestOf(Kind kind) {
        return ofKind(kind).stream()
                .max(Comparator.comparingLong(indication -> indication.size().um()));
    }

    /** How many indications there are. */
    public int size() {
        return indications.size();
    }
}
