package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each person has taken this year on each quantity the badge reports, tracked against their
 * limits and the investigation levels.
 *
 * <p>A dose recorded without a quantity is a deep dose, which is what most of the trade means by a
 * dose. The investigation level and the limit are judged on whichever quantity is furthest along,
 * because a hand dose at a third of its limit wants the same conversation as a whole body one.
 */
public final class DoseLedger {

    private final int year;
    private final Map<String, Map<DoseQuantity, Dose>> doses = new LinkedHashMap<>();

    /** An empty ledger for that year. */
    public DoseLedger(int year) {
        this.year = year;
    }

    /** A ledger for that year holding those deep dose totals. */
    public DoseLedger(int year, Map<String, Dose> doses) {
        this.year = year;
        doses.forEach((badge, taken) -> record(badge, DoseQuantity.DEEP, taken));
    }

    /** The year the ledger is for. */
    public int year() {
        return year;
    }

    /** What each badge has taken on the deep dose, in the order the badges first appeared. */
    public Map<String, Dose> doses() {
        Map<String, Dose> deep = new LinkedHashMap<>();
        doses.keySet().forEach(badge -> deep.put(badge, takenBy(badge)));
        return Collections.unmodifiableMap(deep);
    }

    /**
     * Add a deep dose to a badge's total.
     *
     * @throws ValidationError without a badge
     */
    public void record(String badge, Dose taken) {
        record(badge, DoseQuantity.DEEP, taken);
    }

    /**
     * Add a dose on that quantity to a badge's total.
     *
     * @throws ValidationError without a badge
     */
    public void record(String badge, DoseQuantity quantity, Dose taken) {
        if (Text.strip(badge).isEmpty()) {
            throw new ValidationError("a dose is recorded against a badge");
        }
        doses.computeIfAbsent(badge, ignored -> new EnumMap<>(DoseQuantity.class))
                .merge(quantity, taken, Dose::plus);
    }

    /** What a badge has taken on the deep dose; nothing for a badge not on the ledger. */
    public Dose takenBy(String badge) {
        return takenBy(badge, DoseQuantity.DEEP);
    }

    /** What a badge has taken on that quantity; nothing for a badge not on the ledger. */
    public Dose takenBy(String badge, DoseQuantity quantity) {
        return doses.getOrDefault(badge, Map.of()).getOrDefault(quantity, Dose.ZERO);
    }

    /** The share of their deep dose limit the person has taken, which is what the monthly report prints. */
    public BigDecimal fractionFor(Assignment assignment) {
        return fractionFor(assignment, DoseQuantity.DEEP);
    }

    /** The share of their limit on that quantity the person has taken. */
    public BigDecimal fractionFor(Assignment assignment, DoseQuantity quantity) {
        return takenBy(assignment.badge(), quantity)
                .fractionOf(assignment.workerClass().limit(quantity));
    }

    /** The quantity the person is furthest along on, the deep dose first on a tie. */
    public DoseQuantity governing(Assignment assignment) {
        DoseQuantity worst = DoseQuantity.DEEP;
        for (DoseQuantity quantity : DoseQuantity.values()) {
            if (fractionFor(assignment, quantity).compareTo(fractionFor(assignment, worst)) > 0) {
                worst = quantity;
            }
        }
        return worst;
    }

    /** Zero, one or two on the governing quantity, which is what the monthly report is keyed on. */
    public int investigationLevel(Assignment assignment) {
        return Dosimetry.investigationLevel(fractionFor(assignment, governing(assignment)));
    }

    /** Whether the person has taken more than their limit on any quantity. */
    public boolean overLimit(Assignment assignment) {
        return Arrays.stream(DoseQuantity.values())
                .anyMatch(quantity -> takenBy(assignment.badge(), quantity)
                                .compareTo(assignment.workerClass().limit(quantity))
                        > 0);
    }

    /** What is left of the person's deep dose limit for the year; nothing once it is used. */
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
