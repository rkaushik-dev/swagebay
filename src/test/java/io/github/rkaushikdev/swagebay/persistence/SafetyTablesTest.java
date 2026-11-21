package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.models.DoseEntryRow;
import io.github.rkaushikdev.swagebay.persistence.models.DosimeterRow;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyPointRow;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyRow;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyTablesTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2024, 8, 13, 23, 30);
    private static final LocalDateTime DUE = LocalDateTime.of(2024, 12, 1, 0, 0);

    private static SurveyRow surveyRow(String stage) {
        SurveyRow row = new SurveyRow();
        row.setRadiographNumber("R-001");
        row.setStage(stage);
        row.setPerformedAt(WHEN);
        row.setPerformedBy("0142");
        row.setInstrument("Ludlum 3");
        row.setCalibrationDue(DUE);
        return row;
    }

    private static SurveyRow surveyRow() {
        return surveyRow("after");
    }

    private static SurveyPointRow pointRow(String label, long metres, boolean surface) {
        SurveyPointRow row = new SurveyPointRow();
        row.setLabel(label);
        row.setDistance(Length.fromM(metres));
        row.setBearingDegrees(0);
        row.setReading(usvPerHour(20));
        row.setAtDeviceSurface(surface);
        return row;
    }

    private static SurveyPointRow pointRow() {
        return pointRow("boundary", 140, false);
    }

    private static DoseEntryRow doseEntry(Dose taken) {
        DoseEntryRow row = new DoseEntryRow();
        row.setBadge("0142");
        row.setYear(2024);
        row.setTaken(taken);
        row.setRecordedAt(WHEN);
        row.setSourceOfEntry("badge");
        return row;
    }

    private static DosimeterRow dosimeter(String serial, String kind) {
        DosimeterRow row = new DosimeterRow();
        row.setSerial(serial);
        row.setKind(kind);
        row.setBadge("0142");
        row.setWorkerClass("adult worker");
        return row;
    }

    @Test
    void everySafetyTableIsOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("survey", "survey_point", "dose_entry", "dosimeter");
    }

    @Test
    void aSurveyHoldsItsPoints() {
        try (TestSession db = TestSession.open()) {
            SurveyRow survey = surveyRow();
            survey.addPoint(pointRow());
            survey.addPoint(pointRow("device", 1, true));
            db.add(survey);
            db.commit();
            SurveyRow found = db.one(SurveyRow.class);
            assertThat(found.getPoints()).hasSize(2);
            assertThat(found.getPoints().get(0).getReading()).isEqualTo(usvPerHour(20));
        }
    }

    @Test
    void aStageTheSchemaDoesNotKnowIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(surveyRow("during"));
                db.commit();
            });
        }
    }

    @Test
    void aPointNeedsADistance() {
        try (TestSession db = TestSession.open()) {
            SurveyRow survey = surveyRow();
            SurveyPointRow bad = pointRow();
            bad.setDistance(Length.ZERO);
            survey.addPoint(bad);
            assertTheSchemaRefuses(() -> {
                db.add(survey);
                db.commit();
            });
        }
    }

    @Test
    void theSurfaceFlagIsAColumnAndNotInferred() {
        try (TestSession db = TestSession.open()) {
            SurveyRow survey = surveyRow();
            survey.addPoint(pointRow("device", 140, true));
            db.add(survey);
            db.commit();
            SurveyPointRow found = db.one(SurveyPointRow.class);
            assertThat(found.isAtDeviceSurface()).isTrue();
            assertThat(found.getDistance()).isEqualTo(Length.fromM(140));
        }
    }

    @Test
    void deletingASurveyTakesItsPoints() {
        try (TestSession db = TestSession.open()) {
            SurveyRow survey = surveyRow();
            survey.addPoint(pointRow());
            db.add(survey);
            db.commit();
            db.delete(survey);
            db.commit();
            assertThat(db.all(SurveyPointRow.class)).isEmpty();
        }
    }

    @Test
    void doseIsAnAppendOnlyLedgerRatherThanATotal() {
        try (TestSession db = TestSession.open()) {
            for (Dose taken : List.of(usv(500), usv(300), usv(120))) {
                db.add(doseEntry(taken));
            }
            db.commit();
            List<DoseEntryRow> entries = db.all(DoseEntryRow.class);
            assertThat(entries).hasSize(3);
            Dose total = entries.stream().map(DoseEntryRow::getTaken).reduce(Dose.ZERO, Dose::plus);
            assertThat(total).isEqualTo(usv(920));
        }
    }

    /** The sum comes back from the database as whole nanosieverts, which the dose column reads. */
    @Test
    void theTotalCanBeSummedInTheDatabaseToo() {
        try (TestSession db = TestSession.open()) {
            for (Dose taken : List.of(usv(500), usv(300))) {
                db.add(doseEntry(taken));
            }
            db.commit();
            Long summed = db.entityManager()
                    .createQuery("select sum(d.taken) from DoseEntryRow d where d.badge = :badge", Long.class)
                    .setParameter("badge", "0142")
                    .getSingleResult();
            assertThat(new DoseColumn().convertToEntityAttribute(summed)).isEqualTo(usv(800));
        }
    }

    @Test
    void aNegativeDoseEntryIsRefused() {
        try (TestSession db = TestSession.open()) {
            DoseEntryRow entry = db.add(doseEntry(usv(1)));
            db.commit();
            assertTheSchemaRefuses(() -> {
                db.entityManager()
                        .createNativeQuery("UPDATE dose_entry SET taken = -1 WHERE id = :row")
                        .setParameter("row", entry.getId().orElseThrow())
                        .executeUpdate();
                db.commit();
            });
        }
    }

    @Test
    void aDosimeterKindTheSchemaDoesNotKnowIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(dosimeter("X-1", "lucky charm"));
                db.commit();
            });
        }
    }

    @Test
    void twoDosimetersCannotShareASerial() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                for (int i = 0; i < 2; i++) {
                    DosimeterRow row = dosimeter("A-77", "alarming ratemeter");
                    row.setCalibrationDue(LocalDate.of(2025, 1, 1));
                    db.add(row);
                }
                db.commit();
            });
        }
    }

    @Test
    void aBadgeNeedsNoCalibrationDateInTheSchema() {
        try (TestSession db = TestSession.open()) {
            db.add(dosimeter("B-4471", "badge"));
            db.commit();
            assertThat(db.one(DosimeterRow.class).getCalibrationDue()).isEmpty();
        }
    }
}
