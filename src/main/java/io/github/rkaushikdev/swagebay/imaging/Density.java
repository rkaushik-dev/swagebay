package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reading density off a film, and whether the readings pass.
 *
 * <p>The gate has two halves that fail for different reasons. The absolute band says the film is
 * neither too thin to have contrast nor too dense to get light through. The variation rule says the
 * density under the weld has not wandered away from the density under the indicator, which catches
 * the shot where the part was wedge shaped or the beam was off axis, and which a single reading in
 * the middle of the film cannot catch at all. Three readings is the practical minimum: one at the
 * indicator and one either side of it.
 *
 * <p>A reading taken on an instrument whose step wedge check was yesterday is worth less than one
 * taken after this morning's check, so the calibration is graded along with the readings.
 */
public final class Density {

    /** A shift. The step wedge check happens at the start of one. */
    public static final java.time.Duration CALIBRATION_INTERVAL = java.time.Duration.ofHours(8);

    /** How far off its certified step the densitometer may read and still pass its check. */
    public static final BigDecimal STEP_WEDGE_TOLERANCE = new BigDecimal("0.05");

    private Density() {}

    /** Grade single film readings with no calibration on record, which is itself a reason. */
    public static DensityVerdict grade(List<Reading> readings, boolean gamma) {
        return grade(readings, DensityLimits.sectionV(gamma, false), Optional.empty(), Optional.empty());
    }

    /** Grade readings, single film or composite, with no calibration on record. */
    public static DensityVerdict grade(List<Reading> readings, boolean gamma, boolean composite) {
        return grade(readings, DensityLimits.sectionV(gamma, composite), Optional.empty(), Optional.empty());
    }

    /** Grade single film readings taken at a moment on an instrument with that calibration. */
    public static DensityVerdict grade(
            List<Reading> readings, boolean gamma, Calibration calibration, LocalDateTime readAt) {
        return grade(readings, DensityLimits.sectionV(gamma, false), Optional.of(calibration), Optional.of(readAt));
    }

    /**
     * Apply the absolute band and the variation rule to a set of readings, and collect every reason
     * against them.
     *
     * <p>No calibration is a reason. A calibration is checked for currency only when the moment of
     * reading is known. The variation rule needs exactly one reading through the indicator; anything
     * else is a reason and leaves the window unset.
     *
     * @param readings the readings, at least one
     * @param gamma whether the source was a gamma source, which raises the floor
     * @param composite whether the films are viewed as a composite, which lowers it
     * @param calibration the densitometer's step wedge check, if there is one on record
     * @param readAt when the readings were taken, if known
     * @throws ValidationError when there are no readings
     */
    public static DensityVerdict grade(
            List<Reading> readings,
            boolean gamma,
            boolean composite,
            Optional<Calibration> calibration,
            Optional<LocalDateTime> readAt) {
        return grade(readings, DensityLimits.sectionV(gamma, composite), calibration, readAt);
    }

    /**
     * Hold the readings to the limits of whichever standard the job is worked to, with the
     * densitometer's check graded the same way under every one of them.
     *
     * <p>The variation rule, and with it the one reading through the indicator it needs, applies only
     * where the limits say so. Under ISO every reading is simply held between the floor and the
     * ceiling and the window is left unset.
     *
     * @throws ValidationError when there are no readings
     */
    public static DensityVerdict grade(
            List<Reading> readings,
            DensityLimits limits,
            Optional<Calibration> calibration,
            Optional<LocalDateTime> readAt) {
        if (readings.isEmpty()) {
            throw new ValidationError("there is nothing to grade");
        }
        List<String> reasons = new ArrayList<>();
        if (calibration.isEmpty()) {
            reasons.add("no densitometer calibration on record");
        } else if (readAt.isPresent() && !calibration.get().isCurrentAt(readAt.get())) {
            reasons.add("densitometer calibration is not current");
        }
        for (Reading reading : readings) {
            if (reading.density().compareTo(limits.floor()) < 0) {
                reasons.add(reading.label() + " reads " + reading.density() + " below the floor of " + limits.floor());
            } else if (reading.density().compareTo(limits.ceiling()) > 0) {
                reasons.add(
                        reading.label() + " reads " + reading.density() + " above the ceiling of " + limits.ceiling());
            }
        }
        if (!limits.variationRule()) {
            return new DensityVerdict(reasons.isEmpty(), reasons, Optional.empty(), Optional.empty(), Optional.empty());
        }
        List<Reading> through =
                readings.stream().filter(Reading::throughIndicator).toList();
        if (through.size() != 1) {
            reasons.add("exactly one reading has to be taken through the indicator");
            return new DensityVerdict(false, reasons, Optional.empty(), Optional.empty(), Optional.empty());
        }
        BigDecimal indicatorDensity = through.get(0).density();
        Films.Window window = Films.variationWindow(indicatorDensity);
        for (Reading reading : readings) {
            if (!reading.throughIndicator() && !Films.variationAcceptable(indicatorDensity, reading.density())) {
                reasons.add(reading.label() + " reads " + reading.density() + " outside the variation window");
            }
        }
        return new DensityVerdict(
                reasons.isEmpty(),
                reasons,
                Optional.of(indicatorDensity),
                Optional.of(window.low()),
                Optional.of(window.high()));
    }
}
