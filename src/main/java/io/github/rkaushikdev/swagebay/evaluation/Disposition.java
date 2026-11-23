package io.github.rkaushikdev.swagebay.evaluation;

import java.util.List;

/** What an acceptance code says should happen to a weld. */
public enum Disposition {
    ACCEPT("accept"),
    REPAIR("repair"),
    REJECT("reject");

    private final String value;

    Disposition(String value) {
        this.value = value;
    }

    /**
     * What a list of findings comes to: accept with none, reject when any is against a kind that is
     * always rejectable, repair otherwise. Every code reaches its disposition this way.
     */
    public static Disposition of(List<Finding> findings) {
        if (findings.isEmpty()) {
            return ACCEPT;
        }
        return findings.stream().anyMatch(finding -> finding.kind().isAlwaysRejectable()) ? REJECT : REPAIR;
    }

    /** The disposition as the report writes it. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
