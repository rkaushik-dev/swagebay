package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What goes over the wire, and how the value objects cross it.
 *
 * <p>A length is whole micrometres inside this package and it is not micrometres to anybody else. A
 * client asking for a technique sheet wants millimetres, and a client in Texas wants inches, so both
 * go out and neither is the store. A length goes out as an object with both readings on it rather
 * than as a bare number, which costs a few bytes and removes an entire class of unit confusion from
 * every integration anybody ever writes against this.
 *
 * <p>Requests take a length as text rather than as a number, because a client sending a wall
 * thickness should not have to know which unit this package happens to prefer. Parsing is done by
 * the same parser the rest of the package uses, so {@code "0.375 in"} works in a request body
 * exactly as it works in a procedure. Decimals go out as their text; see {@link Json}.
 *
 * <p>Nothing here reaches into the database and nothing computes. A shape that computes is a rule
 * that lives in two places. Every shape is a record, so a handler cannot edit a request. The
 * constraints on a request are checked as it is read; {@link Requests#validated} checks one built
 * in code. A field a request may leave out takes its default in the record's constructor.
 */
public final class Schemas {

    private Schemas() {}

    private static <T> List<T> listOrNone(List<T> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private static <T> Optional<T> orNone(Optional<T> value) {
        return Objects.requireNonNullElse(value, Optional.empty());
    }

    private static <T> T orDefault(T value, T fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }

    /** A length, in both systems, so nobody has to convert. */
    public record LengthOut(long um, BigDecimal mm, BigDecimal inch) {

        /** The readings of {@code value}. */
        public static LengthOut of(Length value) {
            return new LengthOut(value.um(), value.mm(), value.inch());
        }
    }

    /** An activity, in becquerels, curies and terabecquerels. */
    public record ActivityOut(long bq, BigDecimal curie, BigDecimal tbq) {

        /** The readings of {@code value}. */
        public static ActivityOut of(Activity value) {
            return new ActivityOut(value.bq(), value.curie(), value.tbq());
        }
    }

    /** A duration, with the timer reading the crew sets. */
    public record DurationOut(long ms, BigDecimal seconds, String timer) {

        /** The readings of {@code value}. */
        public static DurationOut of(Duration value) {
            return new DurationOut(value.ms(), value.seconds(), value.asTimer());
        }
    }

    /**
     * A length as a client writes it, {@code "9.53 mm"} or {@code "0.375 in"}, parsed by the
     * package's own parser. The parse is checked only once the text is within its bounds, so an
     * empty text is refused for being empty rather than for not being a length.
     */
    @GroupSequence({LengthIn.class, LengthIn.Parsed.class})
    public record LengthIn(
            @NotNull
                    @Size(min = 1, max = 40)
                    @ParsesAsLength(groups = LengthIn.Parsed.class)
                    @Schema(examples = {"9.53 mm", "0.375 in"})
                    String text) {

        /** The checks that run once the text is within its bounds. */
        public interface Parsed {}

        /** The length the text says. */
        public Length toLength() {
            return Length.parse(text);
        }
    }

    /** A joint as a work order describes it. Plate has no outside diameter. */
    public record JointIn(
            @NotNull @Size(min = 1, max = 40) String material,
            @NotNull @Valid LengthIn wall,
            Optional<@Valid LengthIn> outsideDiameter,
            Optional<@Valid LengthIn> reinforcement) {

        public JointIn {
            outsideDiameter = orNone(outsideDiameter);
            reinforcement = orNone(reinforcement);
        }

        /** A joint with no outside diameter and no reinforcement given. */
        public JointIn(String material, LengthIn wall) {
            this(material, wall, Optional.empty(), Optional.empty());
        }
    }

    /** A job to open. */
    public record NewJobIn(
            @NotNull @Size(min = 1, max = 120) String client,
            @NotNull @Size(min = 1, max = 120) String site,
            @NotNull @Size(min = 1, max = 40) String acceptanceCode,
            @NotNull @Size(min = 1, max = 40) String procedureNumber,
            @NotNull LocalDate openedOn) {}

    /** A job as it reads back. */
    public record JobOut(
            String number,
            String client,
            String site,
            String acceptanceCode,
            String procedureNumber,
            LocalDate openedOn,
            Optional<LocalDate> closedOn,
            int welds) {}

    /** A weld to add to a job. */
    public record NewWeldIn(
            @NotNull @Size(min = 1, max = 20) @Schema(examples = {"GW-104", "GW-104R1"}) String weld,
            @NotNull @Valid JointIn joint,
            @NotNull @Valid LengthIn weldLength,
            @NotNull @Size(min = 1, max = 40) String welder,
            @NotNull LocalDate weldedOn) {}

    /** A weld as it reads back. */
    public record WeldOut(
            String weld,
            String material,
            LengthOut wall,
            Optional<LengthOut> outsideDiameter,
            LengthOut reinforcement,
            LengthOut weldLength,
            String welder,
            LocalDate weldedOn,
            int radiographs) {}

    /** A radiograph as it reads back. */
    public record RadiographOut(
            String number,
            String weld,
            int exposureIndex,
            LocalDateTime exposedAt,
            String techniqueReference,
            String medium,
            boolean admissible,
            Optional<String> inadmissibleReason,
            Optional<String> readBy,
            Optional<LocalDateTime> readAt,
            int indications) {}

    /** One finding of an acceptance verdict. */
    public record FindingOut(String clause, String kind, String reason) {}

    /** An acceptance verdict. */
    public record VerdictOut(String code, String disposition, List<DoctorFindingOut> findings) {

        public VerdictOut {
            findings = List.copyOf(findings);
        }
    }

    /** What is still outstanding on a job before it can close. */
    public record OutstandingOut(String job, List<String> items) {

        public OutstandingOut {
            items = List.copyOf(items);
        }

        /** Whether nothing is outstanding. Not written to the wire. */
        @JsonIgnore
        public boolean isClear() {
            return items.isEmpty();
        }
    }

    /** A source as its certificate reads. */
    public record NewSourceIn(
            @NotNull @Size(min = 1, max = 40) String serial,
            @NotNull @Size(min = 1, max = 20) String isotope,
            @NotNull @Positive BigDecimal assayCuries,
            @NotNull LocalDateTime assayAt,
            @NotNull @Size(min = 1, max = 80) String manufacturer,
            @NotNull @Size(min = 1, max = 80) String model,
            @NotNull @Valid LengthIn activeDiameter,
            @NotNull @Valid LengthIn activeLength,
            @Positive BigDecimal workingFloorCuries) {

        /** The working floor when the certificate does not give one: 15 Ci. */
        public static final BigDecimal WORKING_FLOOR_CURIES = BigDecimal.valueOf(15);

        public NewSourceIn {
            workingFloorCuries = orDefault(workingFloorCuries, WORKING_FLOOR_CURIES);
        }
    }

    /** A source as it reads back. */
    public record SourceOut(
            String serial,
            String isotope,
            ActivityOut assayActivity,
            LocalDateTime assayAt,
            String state,
            String location,
            LengthOut focalSize) {}

    /** Whether a source is fit to go out on a day, and why not. */
    public record FitnessOut(String serial, LocalDate on, boolean fit, List<String> reasons) {

        public FitnessOut {
            reasons = List.copyOf(reasons);
        }
    }

    /** A device booked out to a crew. */
    public record BookingIn(
            @NotNull @Size(min = 1, max = 40) String deviceSerial,
            @NotNull @Size(min = 1, max = 20) String crewBadge,
            @NotNull @Size(min = 1, max = 120) String site,
            @NotNull LocalDate wentOut) {}

    /** A device back from site, with the exposures it made. */
    public record ReturnIn(@NotNull LocalDate cameBack, @PositiveOrZero int exposures) {}

    /** The decisions a plan needs that are not on the record. */
    public record PlanIn(
            @NotNull @Size(min = 1, max = 40) String sourceSerial,
            @NotNull @Size(min = 1, max = 20) String film,
            @NotNull LocalDateTime shotAt,
            Optional<String> shot,
            String indicatorSide,
            Optional<@Positive Integer> windowMinutes,
            @Positive BigDecimal density,
            String distanceRule) {

        /** The rule the distance is set by when none is given. */
        public static final String DISTANCE_RULE = "ASME Section V";

        /** The indicator side when none is given. */
        public static final String INDICATOR_SIDE = "source side";

        /** The density aimed for when none is given. */
        public static final BigDecimal DENSITY = new BigDecimal("2.0");

        public PlanIn {
            shot = orNone(shot);
            indicatorSide = orDefault(indicatorSide, INDICATOR_SIDE);
            windowMinutes = orNone(windowMinutes);
            density = orDefault(density, DENSITY);
            distanceRule = orDefault(distanceRule, DISTANCE_RULE);
        }
    }

    /** How many exposures cover a circumference, and how far apart they are. */
    public record CoverageOut(int exposures, BigDecimal separationDegrees, BigDecimal overlapDegrees) {}

    /** The technique sheet, as a client reads it. */
    public record SheetOut(
            String weld,
            String shot,
            LengthOut penetrated,
            LengthOut sourceToFilm,
            LengthOut geometricUnsharpness,
            String indicator,
            CoverageOut coverage,
            DurationOut exposure,
            DurationOut totalSourceOut,
            ActivityOut activity,
            List<String> warnings,
            boolean clean) {

        public SheetOut {
            warnings = List.copyOf(warnings);
        }
    }

    /** What the reader could see of the indicator. */
    public record IndicatorEvidenceIn(
            Optional<Integer> holeIdentity,
            boolean holeVisible,
            Optional<Integer> wireNumber,
            Optional<Integer> finestWireVisible) {

        public IndicatorEvidenceIn {
            holeIdentity = orNone(holeIdentity);
            wireNumber = orNone(wireNumber);
            finestWireVisible = orNone(finestWireVisible);
        }
    }

    /** One density reading off the film. */
    public record DensityReadingIn(
            @NotNull @Size(min = 1, max = 80) String label,
            @NotNull @Positive BigDecimal density,
            boolean throughIndicator) {}

    /** The densitometer check behind the readings. */
    public record CalibrationIn(
            @NotNull LocalDateTime checkedAt,
            @NotNull @Positive BigDecimal stepNominal,
            @NotNull @Positive BigDecimal stepRead,
            @NotNull @Size(min = 1, max = 80) String instrument) {}

    /** One exposure, as the crew reports it. */
    public record ShotIn(
            @NotNull @Min(1) Integer exposureIndex,
            @NotNull LocalDateTime exposedAt,
            @NotNull @Size(min = 1, max = 80) String techniqueReference,
            String medium,
            String film,
            @NotNull @Valid LengthIn penetrated,
            String techniqueClass,
            @NotNull @Valid IndicatorEvidenceIn indicator,
            List<@Valid DensityReadingIn> densities,
            Optional<@Valid CalibrationIn> calibration,
            Boolean gamma,
            Optional<BigDecimal> meanGrey,
            Optional<BigDecimal> noise,
            Optional<@Valid LengthIn> resolution) {

        /** The medium when none is given. */
        public static final String MEDIUM = "film";

        /** The film when none is given. */
        public static final String FILM = "d7";

        /** The technique class when none is given. */
        public static final String TECHNIQUE_CLASS = "A";

        public ShotIn {
            medium = orDefault(medium, MEDIUM);
            film = orDefault(film, FILM);
            techniqueClass = orDefault(techniqueClass, TECHNIQUE_CLASS);
            densities = listOrNone(densities);
            calibration = orNone(calibration);
            gamma = orDefault(gamma, Boolean.TRUE);
            meanGrey = orNone(meanGrey);
            noise = orNone(noise);
            resolution = orNone(resolution);
        }
    }

    /** What recording a shot found. */
    public record ShotOut(String number, boolean admissible, List<String> reasons, List<String> gatesRun) {

        public ShotOut {
            reasons = List.copyOf(reasons);
            gatesRun = List.copyOf(gatesRun);
        }
    }

    /** One indication a reader marked. */
    public record IndicationIn(
            @NotNull @Size(min = 1, max = 8) String kind,
            @NotNull @Valid LengthIn startsAt,
            @NotNull @Valid LengthIn length,
            Optional<@Valid LengthIn> width,
            Optional<BigDecimal> depthFraction,
            Optional<String> clock) {

        public IndicationIn {
            width = orNone(width);
            depthFraction = orNone(depthFraction);
            clock = orNone(clock);
        }
    }

    /** A reading of one radiograph. */
    public record ReadingIn(
            @NotNull @Size(min = 1, max = 20) String readerBadge,
            @NotNull LocalDateTime readAt,
            List<@Valid IndicationIn> indications) {

        public ReadingIn {
            indications = listOrNone(indications);
        }
    }

    /** A source and what is round it, for working out the rope. */
    public record BoundaryIn(
            @NotNull @Size(min = 1, max = 20) String isotope,
            @NotNull @Positive BigDecimal curies,
            Optional<String> collimator,
            Optional<String> barrierMaterial,
            Optional<@Valid LengthIn> barrierThickness) {

        public BoundaryIn {
            collimator = orNone(collimator);
            barrierMaterial = orNone(barrierMaterial);
            barrierThickness = orNone(barrierThickness);
        }
    }

    /** One boundary of the rope, and how far it reaches. */
    public record BoundaryOut(String kind, BigDecimal rateMrPerHour, LengthOut downBeam, LengthOut offBeam) {}

    /** The rope round a shot. */
    public record RopeOut(List<BoundaryOut> boundaries, boolean directional) {

        public RopeOut {
            boundaries = List.copyOf(boundaries);
        }
    }

    /** One point of a survey. */
    public record SurveyPointIn(
            @NotNull @Size(min = 1, max = 80) String label,
            @NotNull @Valid LengthIn distance,
            int bearingDegrees,
            @NotNull @PositiveOrZero BigDecimal mrPerHour,
            boolean atDeviceSurface) {}

    /** A survey taken before or after an exposure. */
    public record SurveyIn(
            @NotNull @Pattern(regexp = "^(before|after)$") String stage,
            @NotNull LocalDateTime performedAt,
            @NotNull @Size(min = 1, max = 40) String performedBy,
            @NotNull @Size(min = 1, max = 80) String instrument,
            @NotNull LocalDateTime calibrationDue,
            List<@Valid SurveyPointIn> points) {

        public SurveyIn {
            points = listOrNone(points);
        }
    }

    /** What a survey found. */
    public record SurveyOut(
            String radiograph,
            String stage,
            boolean clear,
            List<String> faults,
            String hottestLabel,
            BigDecimal hottestMrPerHour) {

        public SurveyOut {
            faults = List.copyOf(faults);
        }
    }

    /** One weld's line in a report package. */
    public record WeldSummaryOut(
            String weld, BigDecimal wallMm, String welder, int radiographs, String disposition, List<String> clauses) {

        public WeldSummaryOut {
            clauses = List.copyOf(clauses);
        }
    }

    /** The report package, as a client reads it. */
    public record PackageOut(
            String job,
            String client,
            String site,
            String code,
            String procedure,
            LocalDate openedOn,
            Optional<LocalDate> closedOn,
            boolean complete,
            List<String> faults,
            int weldsShot,
            int weldsRepaired,
            BigDecimal repairRate,
            boolean repairRateReportable,
            int radiographs,
            List<WeldSummaryOut> welds) {

        public PackageOut {
            faults = List.copyOf(faults);
            welds = List.copyOf(welds);
        }
    }

    /** One page of radiographs, with where the next one starts. */
    public record RadiographPageOut(List<RadiographOut> items, Optional<String> nextCursor, boolean last) {

        public RadiographPageOut {
            items = List.copyOf(items);
        }
    }

    /** An incident to put on the register. */
    public record IncidentIn(
            @NotNull @Size(min = 1, max = 40) String reference,
            @NotNull @Size(min = 1, max = 60) String occurrence,
            @NotNull LocalDateTime discoveredAt,
            @NotNull @Size(min = 1, max = 120) String site,
            @NotNull @Size(min = 1, max = 40) String reportedBy,
            @NotNull @Size(min = 1, max = 2000) String account) {}

    /** An incident as it reads back. */
    public record IncidentOut(
            String reference,
            String occurrence,
            LocalDateTime discoveredAt,
            LocalDateTime dueAt,
            String site,
            Optional<LocalDateTime> notifiedAt,
            boolean late) {}

    /**
     * A calibration certificate against one instrument.
     *
     * <p>Named for the instrument rather than just calibration, because the densitometer check the
     * shot route takes is also a calibration and the two were briefly the same name in one file.
     */
    public record InstrumentCalibrationIn(
            @NotNull @Size(min = 1, max = 40) String serial,
            @NotNull @Size(min = 1, max = 40) String instrument,
            @NotNull LocalDate calibratedOn,
            @NotNull LocalDate dueOn,
            @NotNull @Size(min = 1, max = 120) String laboratory,
            @Size(max = 60) String certificate) {

        public InstrumentCalibrationIn {
            certificate = orDefault(certificate, "");
        }
    }

    /** A calibration as it reads back. */
    public record CalibrationOut(
            String serial, String instrument, LocalDate calibratedOn, LocalDate dueOn, String laboratory) {}

    /** A second reading of the same film. The reason is not optional. */
    public record RereadIn(
            @NotNull @Size(min = 1, max = 20) String readerBadge,
            @NotNull LocalDateTime readAt,
            @NotNull @Size(min = 1, max = 200) String reason,
            List<@Valid IndicationIn> indications) {

        public RereadIn {
            indications = listOrNone(indications);
        }
    }

    /** What a second reading changed. */
    public record RereadOut(String radiograph, List<String> readers, boolean changed) {

        public RereadOut {
            readers = List.copyOf(readers);
        }
    }

    /** One line of the audit trail as it goes over the wire. */
    public record EntryOut(
            LocalDateTime at,
            String actor,
            String action,
            String fieldName,
            String was,
            String became,
            String reason) {}

    /** The audit trail of one record. */
    public record HistoryOut(String record, String identity, List<EntryOut> entries, int afterSigning) {

        public HistoryOut {
            entries = List.copyOf(entries);
        }
    }

    /** One defect an inspection found. */
    public record DefectIn(
            @NotNull @Size(min = 1, max = 30) String item, @NotNull @Size(min = 1, max = 400) String account) {}

    /** A device inspection to record. */
    public record InspectionIn(
            @NotNull LocalDate performedOn,
            @NotNull @Size(min = 1, max = 20) String performedBy,
            boolean isQuarterly,
            List<@Valid DefectIn> defects,
            @Size(max = 400) String notes) {

        public InspectionIn {
            defects = listOrNone(defects);
            notes = orDefault(notes, "");
        }
    }

    /** A device inspection as it reads back. */
    public record InspectionOut(
            String deviceSerial,
            LocalDate performedOn,
            boolean isQuarterly,
            boolean passed,
            List<String> blocking,
            List<String> noted,
            Optional<LocalDate> expiresOn) {

        public InspectionOut {
            blocking = List.copyOf(blocking);
            noted = List.copyOf(noted);
        }
    }

    /** Why the truck is not moving, kept apart by what has to be fixed. */
    public record ClearanceOut(
            String deviceSerial,
            String sourceSerial,
            LocalDate day,
            boolean cleared,
            List<String> sourceReasons,
            List<String> deviceReasons,
            List<String> crewReasons) {

        public ClearanceOut {
            sourceReasons = List.copyOf(sourceReasons);
            deviceReasons = List.copyOf(deviceReasons);
            crewReasons = List.copyOf(crewReasons);
        }
    }

    /** A non conformance report to raise. */
    public record ReportIn(
            @NotNull @Size(min = 1, max = 40) String reference,
            @NotNull @Size(min = 1, max = 20) String job,
            @NotNull @Size(min = 1, max = 60) String item,
            @NotNull @Size(min = 1, max = 120) String requirement,
            @NotNull @Size(min = 1) String account,
            @NotNull LocalDate raisedOn,
            @NotNull @Size(min = 1, max = 20) String raisedBy) {}

    /** What was decided about a report, with its paperwork. */
    public record DecisionIn(
            @NotNull @Size(min = 1, max = 20) String disposition,
            @Size(max = 60) String concession,
            @Size(max = 60) String removal) {

        public DecisionIn {
            concession = orDefault(concession, "");
            removal = orDefault(removal, "");
        }
    }

    /** A report's close. */
    public record CloseIn(@NotNull LocalDate closedOn, @NotNull @Size(min = 1, max = 20) String closedBy) {}

    /** A non conformance report as it reads back, open and overdue as of the day asked about. */
    public record ReportOut(
            String reference,
            String job,
            String item,
            String requirement,
            Optional<String> disposition,
            String concession,
            String removal,
            LocalDate raisedOn,
            String raisedBy,
            Optional<LocalDate> closedOn,
            Optional<String> closedBy,
            long daysOpen,
            boolean overdue) {}

    /**
     * The morning, in the order it has to be worked through.
     *
     * <p>Not SheetOut. That name was already taken by the technique sheet, which is a different sheet
     * entirely, and two response shapes with one name is an API document with one of them missing.
     */
    public record MorningOut(
            LocalDate day,
            List<String> stopsToday,
            List<String> dueThisWeek,
            List<String> runningLate,
            List<String> filmsWaiting,
            List<String> loadsReady,
            boolean quiet) {

        public MorningOut {
            stopsToday = List.copyOf(stopsToday);
            dueThisWeek = List.copyOf(dueThisWeek);
            runningLate = List.copyOf(runningLate);
            filmsWaiting = List.copyOf(filmsWaiting);
            loadsReady = List.copyOf(loadsReady);
        }
    }

    /** What stands between a job and its close, kept apart by who has to fix it. */
    public record ChecklistOut(
            String job,
            boolean clear,
            List<String> workReasons,
            List<String> reportReasons,
            List<String> imageReasons) {

        public ChecklistOut {
            workReasons = List.copyOf(workReasons);
            reportReasons = List.copyOf(reportReasons);
            imageReasons = List.copyOf(imageReasons);
        }
    }

    /** One finding of the doctor. */
    public record DoctorFindingOut(String check, String subject, String account) {}

    /** What the doctor ran and what it found. */
    public record DoctorOut(
            List<String> checksRun, List<String> checksAvailable, boolean healthy, List<DoctorFindingOut> findings) {

        public DoctorOut {
            checksRun = List.copyOf(checksRun);
            checksAvailable = List.copyOf(checksAvailable);
            findings = List.copyOf(findings);
        }
    }
}
