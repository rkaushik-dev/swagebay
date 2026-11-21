package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What each person has taken this year, tracked against their limit and the investigation levels. */
public final class DoseLedger {

    private final int year;
    private final Map<String, Dose> doses = new LinkedHashMap<>();

    /** An empty ledger for that year. */
    public DoseLedger(int year) {
        this.year = year;
    }

    /** A ledger for that year holding those totals. */
    public DoseLedger(int year, Map<String, Dose> doses) {
        this.year = year;
        this.doses.putAll(doses);
    }

    /** The year the ledger is for. */
    public int year() {
        return year;
    }

    /** What each badge has taken, in the order the badges first appeared. */
    public Map<String, Dose> doses() {
        return Collections.unmodifiableMap(doses);
    }

    /**
     * Add a dose to a badge's total.
     *
     * @throws ValidationError without a badge
     */
    public void record(String badge, Dose taken) {
        if (Text.strip(badge).isEmpty()) {
            throw new ValidationError("a dose is recorded against a badge");
        }
        doses.put(badge, takenBy(badge).plus(taken));
    }

    /** What a badge has taken; nothing for a badge not on the ledger. */
    public Dose takenBy(String badge) {
        return doses.getOrDefault(badge, Dose.ZERO);
    }

    /** The share of their limit the person has taken, which is what the monthly report prints. */
    public BigDecimal fractionFor(Assignment assignment) {
        return takenBy(assignment.badge()).fractionOf(assignment.workerClass().limit());
    }

    /** Zero, one or two, which is what the monthly report is keyed on. */
    public int investigationLevel(Assignment assignment) {
        BigDecimal fraction = fractionFor(assignment);
        if (fraction.compareTo(Dosimetry.INVESTIGATION_LEVEL_TWO) >= 0) {
            return 2;
        }
        if (fraction.compareTo(Dosimetry.INVESTIGATION_LEVEL_ONE) >= 0) {
            return 1;
        }
        return 0;
    }

    /** Whether the person has taken more than their limit. */
    public boolean overLimit(Assignment assignment) {
        return takenBy(assignment.badge()).compareTo(assignment.workerClass().limit()) > 0;
    }

    /** What is left of the person's limit for the year; nothing once it is used. */
    public Dose remaining(Assignment assignment) {
        Dose limit = assignment.workerClass().limit();
        Dose taken = takenBy(assignment.badge());
        if (taken.compareTo(limit) >= 0) {
            return Dose.ZERO;
        }
        return limit.minus(taken);
    }

    /** The badges on the ledger, sorted. */
    public List<String> badges() {
        return doses.keySet().stream().sorted().toList();
    }
}
