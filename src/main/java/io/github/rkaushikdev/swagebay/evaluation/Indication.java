package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing the reader found, located and measured the way the code will ask for it.
 *
 * <p>What the code wants is not what the reader wrote, it is three numbers: how long it is along
 * the weld, how wide it is across the weld, and where it starts. Undercut also wants how deep,
 * expressed as a fraction of the wall, because the code judges it that way rather than in
 * millimetres.
 *
 * <p>Two shapes behave differently and the type keeps them apart. An elongated indication has a
 * length and the length is what the code measures. A rounded one has a diameter, carried as its
 * width, and no meaningful length; giving it a length invites somebody to add it into an aggregate
 * it does not belong in.
 *
 * <p>Position is measured from the first location marker, which is the only origin that survives
 * the film being taken off the pipe. The clock position is carried alongside because that is what
 * a repair crew will actually use to find it.
 *
 * @param kind what it is
 * @param startsAt where it starts along the weld, from the first location marker
 * @param length how long it is along the weld, positive for an elongated kind
 * @param width how wide it is across the weld, positive for a rounded kind
 * @param depthFraction how deep, as a fraction of the wall in (0, 1], where measured
 * @param clock where it is round the pipe, where recorded
 * @param radiograph the number of the radiograph it was read off, empty when not recorded
 */
public record Indication(
        Kind kind,
        Length startsAt,
        Length length,
        Length width,
        Optional<BigDecimal> depthFraction,
        Optional<ClockPosition> clock,
        String radiograph) {

    /**
     * An indication.
     *
     * @throws ValidationError for a negative position or size, a rounded kind with no width, an
     *     elongated kind with no length, or a depth fraction outside (0, 1]
     */
    public Indication {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(depthFraction, "depthFraction");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(radiograph, "radiograph");
        if (startsAt.um() < 0) {
            throw new ValidationError("an indication is somewhere on the weld");
        }
        if (length.um() < 0 || width.um() < 0) {
            throw new ValidationError("an indication cannot have a negative size");
        }
        if (kind.isRounded()) {
            if (width.um() <= 0) {
                throw new ValidationError(
                        "a rounded indication is measured across, not along", Context.of("kind", kind.code()));
            }
        } else if (length.um() <= 0) {
            throw new ValidationError("an elongated indication needs a length", Context.of("kind", kind.code()));
        }
        if (depthFraction.isPresent()) {
            BigDecimal depth = depthFraction.get();
            if (depth.signum() <= 0 || depth.compareTo(BigDecimal.ONE) > 0) {
                throw new ValidationError("a depth fraction sits in (0, 1]", Context.of("depth", depth.toString()));
            }
        }
    }

    /** An indication with no width, depth, clock position or radiograph recorded. */
    public Indication(Kind kind, Length startsAt, Length length) {
        this(kind, startsAt, length, Length.ZERO);
    }

    /** An indication with no depth, clock position or radiograph recorded. */
    public Indication(Kind kind, Length startsAt, Length length, Length width) {
        this(kind, startsAt, length, width, Optional.empty(), Optional.empty(), "");
    }

    /** The same indication with its depth as a fraction of the wall. */
    public Indication withDepthFraction(BigDecimal value) {
        return new Indication(kind, startsAt, length, width, Optional.of(value), clock, radiograph);
    }

    /** The same indication at that position round the pipe. */
    public Indication withClock(ClockPosition value) {
        return new Indication(kind, startsAt, length, width, depthFraction, Optional.of(value), radiograph);
    }

    /** The same indication read off that radiograph. */
    public Indication withRadiograph(String value) {
        return new Indication(kind, startsAt, length, width, depthFraction, clock, value);
    }

    /** Where it ends along the weld; where it starts, for a rounded one. */
    public Length endsAt() {
        return startsAt.plus(length);
    }

    /** The dimension the code will judge: the width of a rounded one, the length of anything else. */
    public Length size() {
        return kind.isRounded() ? width : length;
    }

    /** Whether the two share any stretch of weld; touching ends do not count. */
    public boolean overlaps(Indication other) {
        return startsAt.compareTo(other.endsAt()) < 0 && other.startsAt.compareTo(endsAt()) < 0;
    }

    /** The clear distance between the two along the weld; nothing when they overlap or touch. */
    public Length gapTo(Indication other) {
        if (overlaps(other)) {
            return Length.ZERO;
        }
        if (endsAt().compareTo(other.startsAt) <= 0) {
            return other.startsAt.minus(endsAt());
        }
        return startsAt.minus(other.endsAt());
    }

    /** The indication as a report line reads: {@code ESI 25.0 mm from 100 mm at 3:00}. */
    public String describe() {
        String where = clock.map(position -> " at " + position).orElse("");
        return kind.code() + " " + size().formatMm(1) + " from " + startsAt.formatMm(0) + where;
    }
}
