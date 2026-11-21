package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyPointRow;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyRow;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.safety.SurveyStage;
import io.github.rkaushikdev.swagebay.safety.Surveys;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recording surveys, and refusing to lose the one that matters.
 *
 * <p>Both surveys on an exposure go in the same record and neither is optional. The before survey
 * confirms the area was clear. The after survey confirms the source went home. A shift with twelve
 * exposures and eleven after surveys has one exposure nobody can prove ended safely, and the missing
 * one is never the one anybody remembers.
 *
 * <p>So this service asks the record which exposures are missing a survey rather than trusting a
 * crew to notice. That query is the whole reason surveys are stored against a radiograph number
 * instead of against a shift.
 *
 * <p>A failing after survey is recorded and then reported loudly. It is not refused. A survey that
 * reads three hundred millirem at the device is a fact about the world, and software that will not
 * let a crew write it down is software that gets a crew writing it on paper instead.
 *
 * <p>A survey's stage is stored as {@code before} or {@code after}.
 */
public final class SurveyService {

    private SurveyService() {}

    /**
     * Store a survey and say what is wrong with it, without refusing it.
     *
     * @param calibrationDue when the survey meter's calibration runs out
     * @throws ValidationError for a survey with no readings, or one nobody signed
     */
    public static SurveyOutcome recordSurvey(
            UnitOfWork work,
            String radiographNumber,
            SurveyStage stage,
            LocalDateTime performedAt,
            String performedBy,
            String instrument,
            LocalDateTime calibrationDue,
            List<Point> points) {
        if (points.isEmpty()) {
            throw new ValidationError("a survey with no readings is not a survey");
        }
        if (Text.isBlank(performedBy)) {
            throw new ValidationError("a survey is signed");
        }

        SurveyRow row = new SurveyRow();
        row.setRadiographNumber(radiographNumber);
        row.setStage(stored(stage));
        row.setPerformedAt(performedAt);
        row.setPerformedBy(performedBy);
        row.setInstrument(instrument);
        row.setCalibrationDue(calibrationDue);
        for (Point point : points) {
            SurveyPointRow reading = new SurveyPointRow();
            reading.setLabel(point.label());
            reading.setDistance(point.distance());
            reading.setBearingDegrees(point.bearingDegrees());
            reading.setReading(point.reading());
            reading.setAtDeviceSurface(point.atDeviceSurface());
            row.addPoint(reading);
        }
        work.entityManager().persist(row);
        work.flush();

        Point hottest = points.stream()
                .max(Comparator.comparingLong(point -> point.reading().nsvPerHour()))
                .orElseThrow();
        return new SurveyOutcome(
                stage, faults(stage, performedAt, calibrationDue, points), hottest.label(), hottest.reading());
    }

    /** Every survey against one exposure, in time order. */
    public static List<SurveyRow> surveysFor(UnitOfWork work, String radiographNumber) {
        return work.entityManager()
                .createQuery(
                        "select s from SurveyRow s where s.radiographNumber = :number order by s.performedAt",
                        SurveyRow.class)
                .setParameter("number", radiographNumber)
                .getResultList();
    }

    /**
     * Every exposure among those given that is short of a before or an after survey, one line per
     * survey missing, in the order given.
     */
    public static List<String> missingSurveys(UnitOfWork work, List<String> radiographNumbers) {
        List<String> out = new ArrayList<>();
        for (String number : radiographNumbers) {
            Set<String> stages =
                    surveysFor(work, number).stream().map(SurveyRow::getStage).collect(Collectors.toSet());
            for (SurveyStage wanted : SurveyStage.values()) {
                if (!stages.contains(stored(wanted))) {
                    out.add(number + " has no " + stored(wanted) + " survey");
                }
            }
        }
        return List.copyOf(out);
    }

    /** Every after survey whose surface reading says the source did not go home. */
    public static List<String> stuckSourceReports(UnitOfWork work) {
        List<SurveyRow> rows = work.entityManager()
                .createQuery("select s from SurveyRow s where s.stage = :stage order by s.id", SurveyRow.class)
                .setParameter("stage", stored(SurveyStage.AFTER))
                .getResultList();
        List<String> out = new ArrayList<>();
        for (SurveyRow row : rows) {
            Optional<SurveyPointRow> surface = row.getPoints().stream()
                    .filter(SurveyPointRow::isAtDeviceSurface)
                    .findFirst();
            if (surface.isPresent() && surface.get().getReading().compareTo(Surveys.SURFACE_READING_CEILING) > 0) {
                out.add(row.getRadiographNumber().orElse("None") + ": "
                        + surface.get().getReading().formatMrPerHour() + " at the device");
            }
        }
        return List.copyOf(out);
    }

    /**
     * How far the meter and the model differ, as the measured rate over the calculated one, which the
     * office trends.
     *
     * @throws ValidationError for a calculated rate that is not positive
     */
    public static BigDecimal scatterFactor(DoseRate measured, DoseRate calculated) {
        if (calculated.nsvPerHour() <= 0) {
            throw new ValidationError("a calculated rate has to be positive");
        }
        return Decimals.divide(measured.nsvPerHour(), calculated.nsvPerHour());
    }

    /**
     * For the callers that genuinely cannot proceed on a faulty survey.
     *
     * @throws ConflictError when the survey is not clear, with every fault
     */
    public static void requireClear(SurveyOutcome outcome) {
        if (!outcome.isClear()) {
            throw new ConflictError(
                    "the survey is not clear", Context.of("faults", String.join("; ", outcome.faults())));
        }
    }

    private static List<String> faults(
            SurveyStage stage, LocalDateTime performedAt, LocalDateTime calibrationDue, List<Point> points) {
        List<String> out = new ArrayList<>();
        if (!performedAt.isBefore(calibrationDue)) {
            out.add("the survey meter calibration has expired");
        }
        if (stage == SurveyStage.AFTER) {
            Optional<Point> surface =
                    points.stream().filter(Point::atDeviceSurface).findFirst();
            if (surface.isEmpty()) {
                out.add("no reading was taken at the device surface");
            } else if (surface.get().reading().compareTo(Surveys.SURFACE_READING_CEILING) > 0) {
                out.add("the device surface reads " + surface.get().reading().formatMrPerHour()
                        + ", the source is not fully retracted");
            }
        }
        return List.copyOf(out);
    }

    /** The stage as the survey table stores it: {@code before} or {@code after}. */
    private static String stored(SurveyStage stage) {
        return stage.name().toLowerCase(Locale.ROOT);
    }
}
