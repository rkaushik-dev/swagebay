package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of API 1104 section nine: the limits that apply to one kind of imperfection.
 *
 * <p>Four shapes of limit appear and a row can carry any combination of them. An individual limit
 * caps one imperfection: sometimes a length, sometimes a width, and for burn through and porosity
 * whichever of two numbers is smaller, one of which is a fraction of the wall. An aggregate limit
 * caps the total in any continuous twelve inches. A count limit caps how many there may be in the
 * same window regardless of size. A short weld limit replaces the window with a percentage of the
 * weld. Build one with {@link #builder}.
 *
 * @param kind the imperfection the row is for
 * @param clause the clause it comes from, {@code 9.3.8}
 * @param maxLength the fixed cap on the length of one
 * @param maxWidth the cap on the width of one
 * @param maxLengthFractionOfWall the cap on one as a fraction of the wall
 * @param maxAggregate the cap on the total in any continuous twelve inches
 * @param maxCount the cap on how many in the same window
 * @param shortWeldFraction the cap on the total as a fraction of a weld shorter than the window
 * @param note what the row says that no number captures, empty when nothing
 */
public record Criterion(
        Kind kind,
        String clause,
        Optional<Length> maxLength,
        Optional<Length> maxWidth,
        Optional<BigDecimal> maxLengthFractionOfWall,
        Optional<Length> maxAggregate,
        Optional<Integer> maxCount,
        Optional<BigDecimal> shortWeldFraction,
        String note) {

    /** A row. */
    public Criterion {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(clause, "clause");
        Objects.requireNonNull(maxLength, "maxLength");
        Objects.requireNonNull(maxWidth, "maxWidth");
        Objects.requireNonNull(maxLengthFractionOfWall, "maxLengthFractionOfWall");
        Objects.requireNonNull(maxAggregate, "maxAggregate");
        Objects.requireNonNull(maxCount, "maxCount");
        Objects.requireNonNull(shortWeldFraction, "shortWeldFraction");
        Objects.requireNonNull(note, "note");
    }

    /** A builder for the row of that kind and clause, every limit absent and no note. */
    public static Builder builder(Kind kind, String clause) {
        return new Builder(kind, clause);
    }

    /**
     * The cap on one imperfection, with the wall fraction folded in.
     *
     * <p>Where both a fixed length and a fraction of the wall are published the smaller of the two
     * governs, which means the fraction binds on thin wall and the fixed number binds on heavy wall.
     * That is the form that gets hand coded wrong: a shop that only ever sees one thickness never
     * finds out.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public Optional<Length> individualLengthLimit(Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        if (maxLengthFractionOfWall.isEmpty()) {
            return maxLength;
        }
        Length scaled = wall.times(maxLengthFractionOfWall.get());
        if (maxLength.isEmpty()) {
            return Optional.of(scaled);
        }
        Length fixed = maxLength.get();
        return Optional.of(fixed.compareTo(scaled) <= 0 ? fixed : scaled);
    }

    /** Assembles a {@link Criterion}; every limit starts absent and the note empty. */
    public static final class Builder {

        private final Kind kind;
        private final String clause;
        private Optional<Length> maxLength = Optional.empty();
        private Optional<Length> maxWidth = Optional.empty();
        private Optional<BigDecimal> maxLengthFractionOfWall = Optional.empty();
        private Optional<Length> maxAggregate = Optional.empty();
        private Optional<Integer> maxCount = Optional.empty();
        private Optional<BigDecimal> shortWeldFraction = Optional.empty();
        private String note = "";

        private Builder(Kind kind, String clause) {
            this.kind = kind;
            this.clause = clause;
        }

        /** The fixed cap on the length of one. */
        public Builder maxLength(Length value) {
            maxLength = Optional.of(value);
            return this;
        }

        /** The cap on the width of one. */
        public Builder maxWidth(Length value) {
            maxWidth = Optional.of(value);
            return this;
        }

        /** The cap on one as a fraction of the wall. */
        public Builder maxLengthFractionOfWall(BigDecimal value) {
            maxLengthFractionOfWall = Optional.of(value);
            return this;
        }

        /** The cap on the total in any continuous twelve inches. */
        public Builder maxAggregate(Length value) {
            maxAggregate = Optional.of(value);
            return this;
        }

        /** The cap on how many in the same window. */
        public Builder maxCount(int value) {
            maxCount = Optional.of(value);
            return this;
        }

        /** The cap on the total as a fraction of a short weld. */
        public Builder shortWeldFraction(BigDecimal value) {
            shortWeldFraction = Optional.of(value);
            return this;
        }

        /** What the row says that no number captures. */
        public Builder note(String value) {
            note = value;
            return this;
        }

        /** The row as assembled. */
        public Criterion build() {
            return new Criterion(
                    kind,
                    clause,
                    maxLength,
                    maxWidth,
                    maxLengthFractionOfWall,
                    maxAggregate,
                    maxCount,
                    shortWeldFraction,
                    note);
        }
    }
}
