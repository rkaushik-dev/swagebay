package io.github.rkaushikdev.swagebay.evaluation;

/** What an acceptance code says should happen to a weld. */
public enum Disposition {
    ACCEPT("accept"),
    REPAIR("repair"),
    REJECT("reject");

    private final String value;

    Disposition(String value) {
        this.value = value;
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
