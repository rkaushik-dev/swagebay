package io.github.rkaushikdev.swagebay.imaging;

/**
 * Which side of the part an indicator lies on.
 *
 * <p>An indicator on the source side is further from the film, so its shadow is bigger and softer
 * than the same plaque lying on the film side. Source side is the default and film side needs a
 * reason: a film side plaque carries a lead F beside it, and a radiograph with an F on it and no note
 * explaining why the source side was inaccessible is a finding waiting to happen.
 */
public enum IndicatorSide {
    SOURCE("source side"),
    FILM("film side");

    private final String value;

    IndicatorSide(String value) {
        this.value = value;
    }

    /** The side as the report writes it. */
    public String value() {
        return value;
    }

    /** Whether an indicator on this side carries a lead F next to it. */
    public boolean needsMarking() {
        return this == FILM;
    }

    @Override
    public String toString() {
        return value;
    }
}
