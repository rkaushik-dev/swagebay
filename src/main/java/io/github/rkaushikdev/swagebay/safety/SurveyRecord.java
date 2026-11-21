package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One walk round with a meter.
 *
 * @param stage before or after the exposure
 * @param performedAt when the walk was made
 * @param performedBy the badge of whoever signed it, never blank
 * @param instrument the survey meter used
 * @param instrumentCalibrationDue when the meter's calibration runs out
 * @param points the readings, at least one
 */
public record SurveyRecord(
        SurveyStage stage,
        LocalDateTime performedAt,
        String performedBy,
        String instrument,
        LocalDateTime instrumentCalibrationDue,
        List<SurveyPoint> points) {

    /**
     * A survey; the points are copied.
     *
     * @throws ValidationError without readings, or unsigned
     */
    public SurveyRecord {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(performedAt, "performedAt");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(instrumentCalibrationDue, "instrumentCalibrationDue");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new ValidationError("a survey with no readings is not a survey");
        }
        if (Text.strip(performedBy).isEmpty()) {
            throw new ValidationError("a survey is signed");
        }
    }

    /** Whether the meter was inside its calibration when the survey was made. */
    public boolean instrumentIsCurrent() {
        return performedAt.isBefore(instrumentCalibrationDue);
    }

    /** The first reading taken at the device surface, if one was. */
    public Optional<SurveyPoint> surfaceReading() {
        return points.stream().filter(SurveyPoint::atDeviceSurface).findFirst();
    }

    /** The hottest reading, the first of any that read the same. */
    public SurveyPoint hottest() {
        return points.stream()
                .max(Comparator.comparingLong(point -> point.reading().nsvPerHour()))
                .orElseThrow();
    }
}
