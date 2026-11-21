package io.github.rkaushikdev.swagebay.stock;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One batch of film as it came from the supplier, counted in sheets.
 *
 * <p>Quantities are sheets rather than boxes: a box is a different number of sheets depending on
 * the size, and a crew that asks for two boxes and gets a hundred sheets of the wrong size has still
 * run out. The count goes down as film is issued.
 */
public final class Lot {

    /** Inside this many days of its expiry a lot is short dated and issued first. */
    public static final int SHORT_DATED_DAYS = 60;

    private final String lotNumber;
    private final String filmKey;
    private final String size;
    private int sheets;
    private final LocalDate receivedOn;
    private final LocalDate expiresOn;

    /**
     * A lot.
     *
     * @throws ValidationError without a lot number, for a negative count, or for an expiry on or
     *     before the day it arrived
     */
    public Lot(String lotNumber, String filmKey, String size, int sheets, LocalDate receivedOn, LocalDate expiresOn) {
        if (Text.strip(lotNumber).isEmpty()) {
            throw new ValidationError("a lot has a number");
        }
        if (sheets < 0) {
            throw new ValidationError("a lot cannot hold a negative number of sheets");
        }
        if (!expiresOn.isAfter(receivedOn)) {
            throw new ValidationError("a lot expires after it arrived", Context.of("lot", lotNumber));
        }
        this.lotNumber = lotNumber;
        this.filmKey = Objects.requireNonNull(filmKey, "filmKey");
        this.size = Objects.requireNonNull(size, "size");
        this.sheets = sheets;
        this.receivedOn = receivedOn;
        this.expiresOn = expiresOn;
    }

    /** The supplier's lot number. */
    public String lotNumber() {
        return lotNumber;
    }

    /** The film it is, {@code d7}. */
    public String filmKey() {
        return filmKey;
    }

    /** The sheet size, {@code 4x10}. */
    public String size() {
        return size;
    }

    /** The sheets left in it. */
    public int sheets() {
        return sheets;
    }

    /** The day it arrived. */
    public LocalDate receivedOn() {
        return receivedOn;
    }

    /** The first day it may no longer be issued. */
    public LocalDate expiresOn() {
        return expiresOn;
    }

    /** Whether it has expired by that day. */
    public boolean isExpiredOn(LocalDate day) {
        return !day.isBefore(expiresOn);
    }

    /** Whether it is still in date on that day but expires within {@link #SHORT_DATED_DAYS}. */
    public boolean isShortDatedOn(LocalDate day) {
        return !isExpiredOn(day) && daysLeftOn(day) <= SHORT_DATED_DAYS;
    }

    /** The days from that day to the expiry, negative once it has passed. */
    public long daysLeftOn(LocalDate day) {
        return ChronoUnit.DAYS.between(day, expiresOn);
    }

    /** The shelf label: {@code L-1 d7 4x10, 100 sheets}. */
    public String describe() {
        return lotNumber + " " + filmKey + " " + size + ", " + sheets + " sheets";
    }

    /** Take sheets out of it; the store has already checked there are that many. */
    void take(int count) {
        sheets -= count;
    }
}
