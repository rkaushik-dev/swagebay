package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;

/**
 * One film, where it starts and where it ends along the weld.
 *
 * @param index the film's place in the plan, from one
 * @param startsAt where it starts
 * @param endsAt where it ends, past the start
 */
public record FilmSpan(int index, Length startsAt, Length endsAt) {

    /**
     * A film span.
     *
     * @throws ValidationError when it does not cover a positive length
     */
    public FilmSpan {
        if (endsAt.compareTo(startsAt) <= 0) {
            throw new ValidationError("a film covers a positive length", Context.of("index", index));
        }
    }

    /** How much weld the film covers. */
    public Length length() {
        return endsAt.minus(startsAt);
    }

    /** Whether a position lies on the film, both ends included. */
    public boolean contains(Length at) {
        return startsAt.compareTo(at) <= 0 && at.compareTo(endsAt) <= 0;
    }

    /** The markers whose images land on this film, in the order given. */
    public List<Marker> markersWithin(List<Marker> markers) {
        return markers.stream().filter(m -> contains(m.at())).toList();
    }
}
