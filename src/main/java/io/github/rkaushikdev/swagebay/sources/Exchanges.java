package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.clock.Moments;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import java.time.LocalDate;

/**
 * What a device used up of a source between two exchanges.
 *
 * <p>An exchange is recorded as a day because that is what the paperwork carries, and decay wants a
 * moment, so each day is read as its midnight.
 */
public final class Exchanges {

    private Exchanges() {}

    /**
     * How much of a source's activity decayed away over its time in a device.
     *
     * @throws ValidationError for a source that came out before it went in
     */
    public static Activity activityLost(SealedSource source, LocalDate wentIn, LocalDate cameOut) {
        if (cameOut.isBefore(wentIn)) {
            throw new ValidationError("a source cannot come out before it went in");
        }
        return source.activityAt(Moments.startOf(wentIn)).minus(source.activityAt(Moments.startOf(cameOut)));
    }
}
