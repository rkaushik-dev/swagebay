package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.safety.SurveyStage;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurveyServiceTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2024, 12, 4, 23, 30);
    private static final LocalDateTime DUE = LocalDateTime.of(2025, 6, 1, 0, 0);

    private static Point point(String label, long metres, long mr, boolean surface) {
        return new Point(label, Length.fromM(metres), 0, mrPerHour(mr), surface);
    }

    /** A reading at the boundary, 140 m out, reading 2 mR/h. */
    private static Point boundary() {
        return point("boundary", 140, 2, false);
    }

    /** A reading against the device, reading {@code mr} mR/h. */
    private static Point surface(long mr) {
        return point("device", 1, mr, true);
    }

    private static SurveyOutcome record(
            UnitOfWork work,
            String number,
            SurveyStage stage,
            List<Point> points,
            LocalDateTime due,
            LocalDateTime at) {
        return SurveyService.recordSurvey(work, number, stage, at, "0142", "Ludlum 3", due, points);
    }

    private static SurveyOutcome record(UnitOfWork work, String number, SurveyStage stage, List<Point> points) {
        return record(work, number, stage, points, DUE, WHEN);
    }

    private static SurveyOutcome record(UnitOfWork work, SurveyStage stage, List<Point> points) {
        return record(work, "R-001", stage, points);
    }

    /** A sound after survey of R-001: the boundary, and the device surface at 1 mR/h. */
    private static SurveyOutcome record(UnitOfWork work) {
        return record(work, SurveyStage.AFTER, List.of(boundary(), surface(1)));
    }

    @Test
    void aSoundAfterSurveyIsClear() {
        TestDatabase.fresh().transaction(work -> {
            SurveyOutcome outcome = record(work);
            assertThat(outcome.isClear()).isTrue();
            assertThat(outcome.hottestLabel()).isEqualTo("boundary");
        });
    }

    @Test
    void theSurveyAndItsPointsAreStored() {
        TestDatabase.fresh().transaction(work -> {
            record(work);
            List<SurveyRow> stored = SurveyService.surveysFor(work, "R-001");
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).getPoints()).hasSize(2);
            assertThat(stored.get(0).getPerformedBy()).isEqualTo("0142");
        });
    }

    @Test
    void anAfterSurveyWithNoSurfaceReadingIsAFault() {
        TestDatabase.fresh().transaction(work -> {
            SurveyOutcome outcome = record(work, SurveyStage.AFTER, List.of(boundary()));
            assertThat(outcome.faults()).contains("no reading was taken at the device surface");
        });
    }

    @Test
    void aHotDeviceSurfaceIsRecordedAndThenReportedLoudly() {
        TestDatabase.fresh().transaction(work -> {
            SurveyOutcome outcome = record(work, SurveyStage.AFTER, List.of(boundary(), surface(900)));
            assertThat(outcome.isClear()).isFalse();
            assertThat(outcome.faults().get(0)).contains("not fully retracted");
            assertThat(SurveyService.stuckSourceReports(work)).containsExactly("R-001: 900.00 mR/h at the device");
        });
    }

    @Test
    void aFailingSurveyIsStillWrittenDown() {
        TestDatabase.fresh().transaction(work -> {
            record(work, SurveyStage.AFTER, List.of(surface(900)));
            assertThat(SurveyService.surveysFor(work, "R-001")).hasSize(1);
        });
    }

    @Test
    void anExpiredMeterCalibrationIsAFault() {
        TestDatabase.fresh().transaction(work -> {
            SurveyOutcome outcome =
                    record(work, "R-001", SurveyStage.AFTER, List.of(boundary(), surface(1)), WHEN.minusDays(1), WHEN);
            assertThat(outcome.faults()).contains("the survey meter calibration has expired");
        });
    }

    @Test
    void aBeforeSurveyNeedsNoSurfaceReading() {
        TestDatabase.fresh().transaction(work -> {
            SurveyOutcome outcome = record(work, SurveyStage.BEFORE, List.of(boundary()));
            assertThat(outcome.isClear()).isTrue();
        });
    }

    @Test
    void aSurveyWithNothingInItOrNobodyOnItIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            assertThatThrownBy(() -> SurveyService.recordSurvey(
                            work, "R-001", SurveyStage.AFTER, WHEN, "0142", "Ludlum 3", DUE, List.of()))
                    .isInstanceOf(ValidationError.class);
            assertThatThrownBy(() -> SurveyService.recordSurvey(
                            work, "R-001", SurveyStage.AFTER, WHEN, "  ", "Ludlum 3", DUE, List.of(boundary())))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aPointNeedsALabelAndAPlace() {
        assertThatThrownBy(() -> new Point("  ", Length.fromM(1), 0, mrPerHour(2)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Point("a", Length.ZERO, 0, mrPerHour(2))).isInstanceOf(ValidationError.class);
    }

    @Test
    void anExposureWithNeitherSurveyIsShortOfBoth() {
        TestDatabase.fresh().transaction(work -> assertThat(SurveyService.missingSurveys(work, List.of("R-001")))
                .containsExactly("R-001 has no before survey", "R-001 has no after survey"));
    }

    @Test
    void anExposureWithOnlyTheBeforeSurveyIsShortOfTheAfter() {
        TestDatabase.fresh().transaction(work -> {
            record(work, SurveyStage.BEFORE, List.of(boundary()));
            assertThat(SurveyService.missingSurveys(work, List.of("R-001")))
                    .containsExactly("R-001 has no after survey");
        });
    }

    @Test
    void anExposureWithBothIsShortOfNothing() {
        TestDatabase.fresh().transaction(work -> {
            record(work, SurveyStage.BEFORE, List.of(boundary()));
            record(work);
            assertThat(SurveyService.missingSurveys(work, List.of("R-001"))).isEmpty();
        });
    }

    @Test
    void theShortfallIsReportedPerExposure() {
        TestDatabase.fresh().transaction(work -> {
            record(work, "R-001", SurveyStage.BEFORE, List.of(boundary()));
            record(work, "R-001", SurveyStage.AFTER, List.of(boundary(), surface(1)));
            record(work, "R-002", SurveyStage.BEFORE, List.of(boundary()));
            assertThat(SurveyService.missingSurveys(work, List.of("R-001", "R-002")))
                    .containsExactly("R-002 has no after survey");
        });
    }

    @Test
    void theSurveysOnOneFilmComeBackInTimeOrder() {
        TestDatabase.fresh().transaction(work -> {
            record(work, "R-001", SurveyStage.AFTER, List.of(boundary(), surface(1)), DUE, WHEN.plusMinutes(30));
            record(work, "R-001", SurveyStage.BEFORE, List.of(boundary()), DUE, WHEN);
            assertThat(SurveyService.surveysFor(work, "R-001"))
                    .extracting(SurveyRow::getStage)
                    .containsExactly("before", "after");
        });
    }

    @Test
    void theScatterFactorIsTheRatioTheOfficeTrends() {
        assertThat(SurveyService.scatterFactor(mrPerHour(4), mrPerHour(2))).isEqualByComparingTo("2");
        assertThatThrownBy(() -> SurveyService.scatterFactor(mrPerHour(4), mrPerHour(0)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aCallerThatCannotProceedCanInsistOnAClearSurvey() {
        TestDatabase.fresh().transaction(work -> {
            SurveyService.requireClear(record(work));
            assertThatThrownBy(() ->
                            SurveyService.requireClear(record(work, "R-002", SurveyStage.AFTER, List.of(boundary()))))
                    .isInstanceOf(ConflictError.class);
        });
    }
}
