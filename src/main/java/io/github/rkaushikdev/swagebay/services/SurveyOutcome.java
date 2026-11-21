package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.safety.SurveyStage;
import java.util.List;
import java.util.Objects;

/**
 * What recording a survey established.
 *
 * @param stage which of the two surveys on the exposure it was
 * @param faults everything wrong with it, in the order found
 * @param hottestLabel the label of the highest reading
 * @param hottestReading the highest reading
 */
public record SurveyOutcome(SurveyStage stage, List<String> faults, String hottestLabel, DoseRate hottestReading) {

    /** An outcome; the faults are copied. */
    public SurveyOutcome {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(hottestLabel, "hottestLabel");
        Objects.requireNonNull(hottestReading, "hottestReading");
        faults = List.copyOf(faults);
    }

    /** Whether nothing is wrong with the survey. */
    public boolean isClear() {
        return faults.isEmpty();
    }
}
