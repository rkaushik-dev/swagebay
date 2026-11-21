package io.github.rkaushikdev.swagebay.sources;

/** Where a sealed source is in its life, which decides whether it may be booked out. */
public enum SourceState {
    IN_SERVICE("in service"),
    IN_TRANSIT("in transit"),
    STORED("stored"),
    AWAITING_DISPOSAL("awaiting disposal"),
    DISPOSED("disposed");

    private final String value;

    SourceState(String value) {
        this.value = value;
    }

    /** The stored phrase, {@code awaiting disposal}. */
    public String value() {
        return value;
    }

    /** Whether a source in this state may be taken to a job: in service, or stored in the shop. */
    public boolean canBeUsed() {
        return this == IN_SERVICE || this == STORED;
    }
}
