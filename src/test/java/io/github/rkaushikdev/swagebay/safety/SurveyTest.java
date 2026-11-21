package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurveyTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2024, 6, 3, 23, 30);
    private static final LocalDateTime DUE = LocalDateTime.of(2024, 12, 1, 0, 0);

    private static RadiationField fieldOf() {
        return new RadiationField(IR192, curie(74));
    }

    private static SurveyPoint point(String label, String metres, String mr, boolean surface) {
        return new SurveyPoint(label, Length.fromM(metres), BigDecimal.ZERO, mrPerHour(mr), surface);
    }

    private static SurveyPoint point(String label, long metres, long mr) {
        return point(label, Long.toString(metres), Long.toString(mr), false);
    }

    private static SurveyPoint point() {
        return point("boundary", 140, 2);
    }

    private static SurveyPoint device(long mr) {
        return point("device", "0.3", Long.toString(mr), true);
    }

    private static SurveyRecord record(SurveyStage stage, List<SurveyPoint> points, LocalDateTime due) {
        return new SurveyRecord(stage, WHEN, "0142", "Ludlum 3", due, points);
    }

    private static SurveyRecord record(List<SurveyPoint> points) {
        return record(SurveyStage.AFTER, points, DUE);
    }

    private static SurveyRecord record() {
        return record(List.of(point(), device(1)));
    }

    private static SurveyRecord atTheBoundary(DoseRate reading) {
        RadiationField plan = fieldOf();
        Length boundary = plan.boundary(BoundaryKind.RESTRICTED, BigDecimal.ZERO);
        return record(List.of(new SurveyPoint("boundary", boundary, BigDecimal.ZERO, reading), device(1)));
    }

    private static DoseRate calculatedAtTheBoundary() {
        RadiationField plan = fieldOf();
        Length boundary = plan.boundary(BoundaryKind.RESTRICTED, BigDecimal.ZERO);
        return plan.rateAt(boundary, BigDecimal.ZERO);
    }

    @Test
    void aSoundPostExposureSurveyHasNoFaults() {
        assertThat(Surveys.faults(record())).isEmpty();
    }

    @Test
    void aPostExposureSurveyWithNoSurfaceReadingIsAFault() {
        SurveyRecord lonely = record(List.of(point()));
        assertThat(Surveys.faults(lonely)).contains("no reading was taken at the device surface");
    }

    @Test
    void aHotDeviceSurfaceMeansTheSourceDidNotGoHome() {
        SurveyRecord stuck = record(List.of(point(), device(900)));
        List<String> found = Surveys.faults(stuck);
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("not fully retracted");
    }

    @Test
    void theSurfaceCeilingIsWhereTheCallIsMade() {
        SurveyRecord atLimit = record(List.of(device(200)));
        assertThat(Surveys.faults(atLimit)).isEmpty();
        SurveyRecord over = record(List.of(device(201)));
        assertThat(Surveys.faults(over)).isNotEmpty();
        assertThat(mrPerHour(200)).isEqualTo(Surveys.SURFACE_READING_CEILING);
    }

    @Test
    void aPreExposureSurveyNeedsNoSurfaceReading() {
        SurveyRecord before = record(SurveyStage.BEFORE, List.of(point()), DUE);
        assertThat(Surveys.faults(before)).isEmpty();
        assertThat(SurveyStage.BEFORE).hasToString("before the exposure");
    }

    @Test
    void anExpiredMeterCalibrationIsAFaultOnItsOwn() {
        SurveyRecord stale = record(SurveyStage.AFTER, List.of(point(), device(1)), WHEN.minusDays(1));
        assertThat(stale.instrumentIsCurrent()).isFalse();
        assertThat(Surveys.faults(stale)).contains("the survey meter calibration has expired");
    }

    @Test
    void theHottestPointIsReportedForTheRecord() {
        SurveyRecord survey = record(List.of(point("a", 140, 1), point("b", 100, 4), point("c", 200, 2)));
        assertThat(survey.hottest().label()).isEqualTo("b");
    }

    @Test
    void aReadingThatMatchesTheModelIsNoDiscrepancy() {
        SurveyRecord survey = atTheBoundary(calculatedAtTheBoundary());
        assertThat(Surveys.compare(survey, fieldOf())).isEmpty();
    }

    @Test
    void aReadingMuchHotterThanTheModelIsTheFinding() {
        List<Discrepancy> found = Surveys.compare(atTheBoundary(mrPerHour(20)), fieldOf());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).measuredHotter()).isTrue();
        assertThat(found.get(0).describe()).contains("hotter");
    }

    @Test
    void aReadingMuchColderThanTheModelIsReportedTheOtherWay() {
        List<Discrepancy> found = Surveys.compare(atTheBoundary(mrPerHour("0.1")), fieldOf());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).measuredHotter()).isFalse();
        assertThat(found.get(0).describe()).contains("colder");
    }

    @Test
    void scatterInsideTheToleranceIsNotADiscrepancy() {
        DoseRate slightlyHot =
                mrPerHour(Decimals.multiply(calculatedAtTheBoundary().mrPerHour(), new BigDecimal("1.4")));
        assertThat(Surveys.compare(atTheBoundary(slightlyHot), fieldOf())).isEmpty();
    }

    @Test
    void theSurfaceReadingIsNeverComparedAgainstTheModel() {
        SurveyRecord survey = record(List.of(device(1)));
        assertThat(Surveys.compare(survey, fieldOf())).isEmpty();
    }

    @Test
    void theBoundaryIsConfirmedWhenEveryReadingIsUnderTheRate() {
        SurveyRecord survey = record(List.of(point("a", 140, 2), device(900)));
        assertThat(Surveys.boundaryConfirmed(survey)).isTrue();
        SurveyRecord hot = record(List.of(point("a", 140, 3)));
        assertThat(Surveys.boundaryConfirmed(hot)).isFalse();
    }

    @Test
    void aSurveyNeedsReadingsAndASignature() {
        assertThatThrownBy(() -> new SurveyRecord(SurveyStage.AFTER, WHEN, "0142", "Ludlum 3", DUE, List.of()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new SurveyRecord(SurveyStage.AFTER, WHEN, "   ", "Ludlum 3", DUE, List.of(point())))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSurveyPointNeedsALabelAndAPlace() {
        assertThatThrownBy(() -> new SurveyPoint("  ", mm(100), BigDecimal.ZERO, mrPerHour(2)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new SurveyPoint("a", Length.ZERO, BigDecimal.ZERO, mrPerHour(2)))
                .isInstanceOf(ValidationError.class);
    }
}
