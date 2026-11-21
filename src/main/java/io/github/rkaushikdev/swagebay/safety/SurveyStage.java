package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.util.Repr;

/** Which of the two surveys on every exposure a record is. */
public enum SurveyStage {
    BEFORE("before the exposure"),
    AFTER("after the exposure");

    private final String value;

    SurveyStage(String value) {
        this.value = value;
    }

    /**
     * The stage with that stored phrase.
     *
     * @throws IllegalArgumentException for a phrase that names no stage
     */
    public static SurveyStage fromValue(String value) {
        for (SurveyStage stage : values()) {
            if (stage.value.equals(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("no survey stage is stored as " + Repr.of(value));
    }

    /** The stored phrase, {@code after the exposure}. */
    public String value() {
        return value;
    }

    /** The stored phrase. */
    @Override
    public String toString() {
        return value;
    }
}
