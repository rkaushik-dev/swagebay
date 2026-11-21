package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.LeakTestRow;
import io.github.rkaushikdev.swagebay.persistence.models.SightingRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceTablesTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 3, 1, 0, 0);
    private static final LocalDate WENT = LocalDate.of(2024, 6, 3);

    private static SourceRow sourceRow(Activity activity, Length diameter) {
        SourceRow row = new SourceRow();
        row.setSerial("IR-5512");
        row.setIsotopeKey("ir192");
        row.setAssayActivity(activity);
        row.setAssayAt(ASSAY);
        row.setManufacturer("QSA");
        row.setModel("A424-9");
        row.setActiveDiameter(diameter);
        row.setActiveLength(mm(3));
        row.setWorkingFloor(curie(15));
        return row;
    }

    private static SourceRow sourceRow() {
        return sourceRow(curie(90), mm(3));
    }

    private static LeakTestRow wipe(BigDecimal removed) {
        LeakTestRow row = new LeakTestRow();
        row.setPerformedAt(ASSAY);
        row.setBecquerelsRemoved(removed);
        row.setTechnician("rp");
        return row;
    }

    private static DeviceRow deviceRow(String model) {
        DeviceRow row = new DeviceRow();
        row.setSerial("880D-221");
        row.setModelKey(model);
        return row;
    }

    private static TripRow tripRow(SourceRow source, DeviceRow device, String site, LocalDate wentOut) {
        TripRow row = new TripRow();
        row.setSource(source);
        row.setDevice(device);
        row.setCrewBadge("0142");
        row.setSite(site);
        row.setWentOut(wentOut);
        return row;
    }

    @Test
    void aSourceRoundTripsWithItsActivityAndPellet() {
        try (TestSession db = TestSession.open()) {
            db.add(sourceRow());
            db.commit();
            SourceRow found = db.one(SourceRow.class);
            assertThat(found.getAssayActivity()).isEqualTo(curie(90));
            assertThat(found.getActiveDiameter()).isEqualTo(mm(3));
            assertThat(found.getState()).isEqualTo("in service");
        }
    }

    @Test
    void twoSourcesCannotShareASerial() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(sourceRow());
                db.add(sourceRow());
                db.commit();
            });
        }
    }

    @Test
    void aSourceWithNoActivityOrNoPelletIsRefused() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(sourceRow(Activity.ZERO, mm(3)));
                db.commit();
            });
        }
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(sourceRow(curie(90), Length.ZERO));
                db.commit();
            });
        }
    }

    @Test
    void aSourceAndItsFirstLeakTestGoInTogether() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            source.addLeakTest(wipe(BigDecimal.ZERO));
            db.add(source);
            db.commit();
            LeakTestRow found = db.one(LeakTestRow.class);
            assertThat(found.getSource().orElseThrow().getSerial()).isEqualTo("IR-5512");
        }
    }

    @Test
    void deletingASourceTakesItsLeakTestsWithIt() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            source.addLeakTest(wipe(BigDecimal.ZERO));
            db.add(source);
            db.commit();
            db.delete(source);
            db.commit();
            assertThat(db.all(LeakTestRow.class)).isEmpty();
        }
    }

    @Test
    void aNegativeWipeResultIsRefused() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            source.addLeakTest(wipe(BigDecimal.valueOf(-1)));
            assertTheSchemaRefuses(() -> {
                db.add(source);
                db.commit();
            });
        }
    }

    @Test
    void aTripTiesASourceToADevice() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            DeviceRow device = deviceRow("880d");
            db.addAll(source, device);
            db.flush();
            TripRow trip = tripRow(source, device, "Come By Chance", WENT);
            trip.setExposures(24);
            db.add(trip);
            db.commit();
            TripRow found = db.one(TripRow.class);
            assertThat(found.getSource().orElseThrow().getSerial()).isEqualTo("IR-5512");
            assertThat(found.getDevice().orElseThrow().getSerial()).isEqualTo("880D-221");
            assertThat(found.getCameBack()).isEmpty();
        }
    }

    @Test
    void aTripCannotComeBackBeforeItWentOut() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            DeviceRow device = deviceRow("880d");
            db.addAll(source, device);
            db.flush();
            TripRow trip = tripRow(source, device, "site", WENT);
            trip.setCameBack(LocalDate.of(2024, 6, 1));
            assertTheSchemaRefuses(() -> {
                db.add(trip);
                db.commit();
            });
        }
    }

    @Test
    void aNegativeExposureCountIsRefused() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            DeviceRow device = deviceRow("880d");
            db.addAll(source, device);
            db.flush();
            TripRow trip = tripRow(source, device, "site", WENT);
            trip.setExposures(-1);
            assertTheSchemaRefuses(() -> {
                db.add(trip);
                db.commit();
            });
        }
    }

    @Test
    void twoDevicesCannotShareASerial() {
        try (TestSession db = TestSession.open()) {
            assertTheSchemaRefuses(() -> {
                db.add(deviceRow("880d"));
                db.add(deviceRow("880s"));
                db.commit();
            });
        }
    }

    @Test
    void aSightingHangsOffTheSourceItSighted() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            SightingRow sighting = new SightingRow();
            sighting.setSeenOn(LocalDate.of(2024, 5, 1));
            sighting.setSeenBy("0142");
            sighting.setLocation("shop");
            source.addSighting(sighting);
            db.add(source);
            db.commit();
            assertThat(db.one(SightingRow.class).getSource().orElseThrow().getSerial())
                    .isEqualTo("IR-5512");
        }
    }

    @Test
    void aSourceCanHoldSeveralTripsAndTheyComeBackWithIt() {
        try (TestSession db = TestSession.open()) {
            SourceRow source = sourceRow();
            DeviceRow device = deviceRow("880d");
            db.addAll(source, device);
            db.flush();
            for (LocalDate day : List.of(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 10))) {
                db.add(tripRow(source, device, "site", day));
            }
            db.commit();
            assertThat(db.reread(source).getTrips()).hasSize(2);
        }
    }
}
