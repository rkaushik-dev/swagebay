package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.models.IndicationRow;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WorkTablesTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 7, 1);
    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 7, 10, 23, 15);

    private static JobRow jobRow(int year, int serial) {
        JobRow row = new JobRow();
        row.setYear(year);
        row.setSerial(serial);
        row.setClient("Maritime Pipelines");
        row.setSite("Come By Chance");
        row.setAcceptanceCode("API 1104");
        row.setProcedureNumber("RT-001");
        row.setOpenedOn(OPENED);
        return row;
    }

    private static JobRow jobRow() {
        return jobRow(24, 417);
    }

    private static WeldRow weldRow(int number, int repair, Length wall, Length length) {
        WeldRow row = new WeldRow();
        row.setPrefix("GW");
        row.setNumber(number);
        row.setRepairRound(repair);
        row.setMaterialKey("carbon_steel");
        row.setOutsideDiameter(mm("168.3"));
        row.setWall(wall);
        row.setReinforcement(mm(2));
        row.setWeldLength(length);
        row.setWelder("w-19");
        row.setWeldedOn(OPENED);
        return row;
    }

    private static WeldRow weldRow(int number, int repair) {
        return weldRow(number, repair, mm("7.11"), mm(529));
    }

    private static WeldRow weldRow() {
        return weldRow(104, 0);
    }

    private static RadiographRow radiographRow(String number, int index) {
        RadiographRow row = new RadiographRow();
        row.setNumber(number);
        row.setExposureIndex(index);
        row.setExposedAt(SHOT);
        row.setTechniqueReference("RT-001 rev 1");
        row.setMedium("film");
        row.setAdmissible(true);
        return row;
    }

    private static RadiographRow radiographRow() {
        return radiographRow("R-001", 1);
    }

    private static IndicationRow indication(String kind, Length length, Length width) {
        IndicationRow row = new IndicationRow();
        row.setKind(kind);
        row.setStartsAt(mm(100));
        row.setLength(length);
        row.setWidth(width);
        return row;
    }

    @Test
    void aJobHoldsWeldsAndAWeldHoldsRadiographs() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            weld.addRadiograph(radiographRow());
            job.addWeld(weld);
            db.add(job);
            db.commit();
            RadiographRow found = db.one(RadiographRow.class);
            WeldRow shotOf = found.getWeld().orElseThrow();
            assertThat(shotOf.getNumber()).isEqualTo(104);
            assertThat(shotOf.getJob().orElseThrow().getClient()).isEqualTo("Maritime Pipelines");
        }
    }

    @Test
    void twoJobsCannotShareAYearAndSerial() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(jobRow());
                db.add(jobRow());
                db.commit();
            });
        }
    }

    @Test
    void aJobSerialOutsideTheRangeIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(jobRow(24, 0));
                db.commit();
            });
        }
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(jobRow(100, 417));
                db.commit();
            });
        }
    }

    @Test
    void aRepairIsARoundOfTheSameWeldAndBothRowsAreAllowed() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            job.addWeld(weldRow(104, 0));
            job.addWeld(weldRow(104, 1));
            db.add(job);
            db.commit();
            assertThat(db.all(WeldRow.class)).hasSize(2);
        }
    }

    @Test
    void theSameWeldAndRoundTwiceIsRefused() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            job.addWeld(weldRow(104, 1));
            job.addWeld(weldRow(104, 1));
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
    }

    @Test
    void theSameWeldNumberOnTwoJobsIsFine() {
        try (TestSession db = TestSession.open()) {
            JobRow first = jobRow(24, 417);
            JobRow second = jobRow(24, 418);
            first.addWeld(weldRow());
            second.addWeld(weldRow());
            db.addAll(first, second);
            db.commit();
            assertThat(db.all(WeldRow.class)).hasSize(2);
        }
    }

    @Test
    void aWeldWithNoWallOrNoLengthIsRefused() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            job.addWeld(weldRow(104, 0, Length.ZERO, mm(529)));
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            job.addWeld(weldRow(104, 0, mm("7.11"), Length.ZERO));
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
    }

    @Test
    void aRadiographNumberIsUniqueAcrossTheWholeShop() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow first = weldRow(104, 0);
            WeldRow second = weldRow(105, 0);
            first.addRadiograph(radiographRow("R-001", 1));
            second.addRadiograph(radiographRow("R-001", 1));
            job.addWeld(first);
            job.addWeld(second);
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
    }

    @Test
    void theSameExposureIndexTwiceOnOneWeldIsRefused() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            weld.addRadiograph(radiographRow("R-001", 1));
            weld.addRadiograph(radiographRow("R-002", 1));
            job.addWeld(weld);
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
    }

    @Test
    void exposuresAreNumberedFromOneInTheSchemaToo() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            weld.addRadiograph(radiographRow("R-001", 0));
            job.addWeld(weld);
            assertTheSchemaRefuses(() -> {
                db.add(job);
                db.commit();
            });
        }
    }

    @Test
    void indicationsHangOffTheFilmTheyWereSeenOn() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            RadiographRow film = radiographRow();
            IndicationRow seen = indication("ESI", mm(20), mm(1));
            seen.setClockMinutes(180);
            film.addIndication(seen);
            weld.addRadiograph(film);
            job.addWeld(weld);
            db.add(job);
            db.commit();
            IndicationRow found = db.one(IndicationRow.class);
            assertThat(found.getRadiograph().orElseThrow().getNumber()).isEqualTo("R-001");
            assertThat(found.getStartsAt()).isEqualTo(mm(100));
        }
    }

    @Test
    void deletingAJobTakesTheWholeTreeWithIt() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            RadiographRow film = radiographRow();
            film.addIndication(indication("P", Length.ZERO, mm(2)));
            weld.addRadiograph(film);
            job.addWeld(weld);
            db.add(job);
            db.commit();
            db.delete(job);
            db.commit();
            assertThat(db.all(WeldRow.class)).isEmpty();
            assertThat(db.all(RadiographRow.class)).isEmpty();
            assertThat(db.all(IndicationRow.class)).isEmpty();
        }
    }

    @Test
    void aReadingKeepsItsNumbersAsDecimals() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow();
            WeldRow weld = weldRow();
            RadiographRow film = radiographRow();
            film.setDensityThroughIndicator(new BigDecimal("2.55"));
            film.setNormalisedSnr(new BigDecimal("132.4"));
            weld.addRadiograph(film);
            job.addWeld(weld);
            db.add(job);
            db.commit();
            RadiographRow found = db.one(RadiographRow.class);
            assertThat(found.getDensityThroughIndicator())
                    .hasValueSatisfying(density -> assertThat(density).isEqualByComparingTo("2.55"));
            assertThat(found.getNormalisedSnr())
                    .hasValueSatisfying(snr -> assertThat(snr).isEqualByComparingTo("132.4"));
        }
    }
}
