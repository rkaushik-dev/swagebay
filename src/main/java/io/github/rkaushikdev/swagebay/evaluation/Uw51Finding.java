package io.github.rkaushikdev.swagebay.evaluation;

import java.util.List;

/**
 * One thing UW-51 objects to.
 *
 * @param rule the subclause, {@code b2}
 * @param reason the objection in words, with the numbers
 * @param indications the indications it is about
 */
public record Uw51Finding(String rule, String reason, List<Indication> indications) {

    /** A finding; the indications are copied. */
    public Uw51Finding {
        indications = List.copyOf(indications);
    }

    /** The finding as a report line: {@code UW-51(b2): ...}. */
    public String describe() {
        return "UW-51(" + rule + "): " + reason;
    }
}
