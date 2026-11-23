package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.safety.Dosimetry;
import io.github.rkaushikdev.swagebay.safety.WorkerClass;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One person's dose position for a year, at the moment it was taken.
 *
 * @param badge whose position it is
 * @param year the year it is for
 * @param taken everything on the ledger for that year
 * @param limit the annual limit the person is held to
 * @param workerClass the class of worker that limit comes from
 */
public record DosePosition(String badge, int year, Dose taken, Dose limit, WorkerClass workerClass) {

    /** A position. */
    public DosePosition {
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(workerClass, "workerClass");
    }

    /** The share of the limit taken. */
    public BigDecimal fraction() {
        return taken.fractionOf(limit);
    }

    /** {@code 2} at or over the second investigation level, {@code 1} at or over the first, else {@code 0}. */
    public int investigationLevel() {
        return Dosimetry.investigationLevel(fraction());
    }

    /** What is left of the limit, and nothing once it is reached. */
    public Dose remaining() {
        if (taken.compareTo(limit) >= 0) {
            return Dose.ZERO;
        }
        return limit.minus(taken);
    }

    /** The line on the board: {@code 0142 6.00 mSv of 50 mSv, level 1}. */
    public String describe() {
        return badge + " " + taken.formatMsv(2) + " of " + limit.formatMsv(0) + ", level " + investigationLevel();
    }
}
