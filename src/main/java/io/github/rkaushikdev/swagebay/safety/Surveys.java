package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Surveys, which are the only part of the safety side that touches reality.
 *
 * <p>Every number here is a calculation until somebody walks the boundary with a meter, and it is
 * checked twice on every exposure: before, to confirm the area is clear and the boundary is where
 * the sketch says, and after, to confirm the source went back into the device. The second is the one
 * that matters. A source that hangs up in the guide tube reads normal at the boundary, because a
 * hundred metres of air has already swallowed the difference, and wrong at the device surface,
 * which is why the post exposure survey always has a surface reading on it.
 *
 * <p>A reading that disagrees with the calculation is not automatically a bad reading: air and
 * concrete scatter, the meter has an energy response, and the source is not a point. What the
 * office wants to know is which way the disagreement runs.
 */
public final class Surveys {

    /** Above this at the device surface the source did not go home. */
    public static final DoseRate SURFACE_READING_CEILING = DoseRate.fromMrPerHour(200);

    /** A reading up to half again the calculation, or down to two thirds of it, is put down to scatter. */
    public static final BigDecimal TOLERANCE = new BigDecimal("1.5");

    private static final BigDecimal LOWER_TOLERANCE = Decimals.divide(BigDecimal.ONE, TOLERANCE);

    private Surveys() {}

    /**
     * Every point away from the device where the meter and the model differ by more than scatter, in
     * the order walked. A point the model puts at no rate at all is not compared.
     */
    public static List<Discrepancy> compare(SurveyRecord record, RadiationField radiationField) {
        List<Discrepancy> out = new ArrayList<>();
        for (SurveyPoint point : record.points()) {
            if (point.atDeviceSurface()) {
                continue;
            }
            DoseRate calculated = radiationField.rateAt(point.distance(), point.bearingDegrees());
            if (calculated.nsvPerHour() == 0) {
                continue;
            }
            BigDecimal ratio = Decimals.divide(point.reading().nsvPerHour(), calculated.nsvPerHour());
            if (ratio.compareTo(TOLERANCE) > 0 || ratio.compareTo(LOWER_TOLERANCE) < 0) {
                out.add(new Discrepancy(point, calculated, ratio));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Everything wrong with the survey itself, before any comparison: an expired meter, and after the
     * exposure a missing or hot surface reading.
     */
    public static List<String> faults(SurveyRecord record) {
        List<String> out = new ArrayList<>();
        if (!record.instrumentIsCurrent()) {
            out.add("the survey meter calibration has expired");
        }
        Optional<SurveyPoint> surface = record.surfaceReading();
        if (record.stage() == SurveyStage.AFTER) {
            if (surface.isEmpty()) {
                out.add("no reading was taken at the device surface");
            } else if (surface.get().reading().compareTo(SURFACE_READING_CEILING) > 0) {
                out.add("the device surface reads " + surface.get().reading().formatMrPerHour()
                        + ", the source is not fully retracted");
            }
        }
        return List.copyOf(out);
    }

    /** Whether every reading away from the device is at or under the restricted area rate. */
    public static boolean boundaryConfirmed(SurveyRecord record) {
        return boundaryConfirmed(record, BoundaryKind.RESTRICTED);
    }

    /** Whether every reading away from the device is at or under the posting's rate. */
    public static boolean boundaryConfirmed(SurveyRecord record, BoundaryKind kind) {
        return record.points().stream()
                .filter(point -> !point.atDeviceSurface())
                .allMatch(point -> point.reading().compareTo(kind.rate()) <= 0);
    }
}
