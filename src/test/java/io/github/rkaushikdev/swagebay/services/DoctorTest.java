package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.prepared;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.CounterRow;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.NonconformanceRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.StoredImageRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

class DoctorTest {

    private static final LocalDateTime READ = LocalDateTime.of(2024, 9, 25, 8, 0);
    private static final LocalDate RAISED = LocalDate.of(2024, 9, 26);

    private static SourceRow aSourceRow() {
        SourceRow row = new SourceRow();
        row.setSerial("IR-5512");
        row.setIsotopeKey("ir192");
        row.setAssayActivity(curie(90));
        row.setAssayAt(LocalDateTime.of(2024, 8, 1, 0, 0));
        row.setManufacturer("QSA");
        row.setModel("A424-9");
        row.setActiveDiameter(mm(3));
        row.setActiveLength(mm(3));
        row.setWorkingFloor(curie(15));
        row.setState("in service");
        return row;
    }

    private static DeviceRow aDeviceRow() {
        DeviceRow row = new DeviceRow();
        row.setSerial("D-77");
        row.setModelKey("880d");
        return row;
    }

    private static void sendOut(SourceRow source, DeviceRow device, String site) {
        TripRow trip = new TripRow();
        trip.setDevice(device);
        trip.setCrewBadge("0142");
        trip.setSite(site);
        trip.setWentOut(RAISED);
        source.addTrip(trip);
    }

    private static NonconformanceRow aRow(String closedBy, String disposition) {
        NonconformanceRow row = new NonconformanceRow();
        row.setReference("NCR-1");
        row.setJob("24-0001");
        row.setItem("GW-104");
        row.setRequirement("API 1104");
        row.setAccount("slag");
        row.setRaisedOn(RAISED);
        row.setRaisedBy("0142");
        row.setDisposition(disposition);
        row.setClosedBy(closedBy);
        row.setClosedOn(LocalDate.of(2024, 10, 1));
        return row;
    }

    /**
     * Insert a report the current check constraints would refuse.
     *
     * <p>Every check in the doctor is for something the code no longer lets in, so a test that goes
     * through the ordinary path cannot produce one. Lifting the table's check constraints for one
     * insert is how a row from before the constraint existed gets simulated, and it is the only
     * honest way to test a check whose whole purpose is legacy data. The database cannot switch a
     * check constraint off, so each one is dropped for the insert and put back afterwards, word for
     * word, without being run against the rows already there. Changing the schema commits what the
     * unit of work had written, which the next test's emptied database never sees.
     */
    private static void legacy(UnitOfWork work, NonconformanceRow row) {
        work.flush();
        Session session = work.entityManager().unwrap(Session.class);
        Map<String, String> checks =
                session.doReturningWork(connection -> checkConstraints(connection, "NONCONFORMANCE"));
        session.doWork(connection -> {
            for (String name : checks.keySet()) {
                execute(connection, "ALTER TABLE nonconformance DROP CONSTRAINT " + name);
            }
        });
        try {
            work.entityManager().persist(row);
            work.flush();
        } finally {
            session.doWork(connection -> {
                for (Map.Entry<String, String> check : checks.entrySet()) {
                    execute(
                            connection,
                            "ALTER TABLE nonconformance ADD CONSTRAINT " + check.getKey() + " CHECK ("
                                    + check.getValue() + ") NOCHECK");
                }
            });
        }
    }

    /**
     * Insert a stored image against an exposure that is not there, as a file left behind when its
     * radiograph was deleted. The foreign key is lifted for the insert and put back afterwards
     * without checking the rows already there.
     */
    private static void orphaned(UnitOfWork work, StoredImageRow row) {
        work.flush();
        Session session = work.entityManager().unwrap(Session.class);
        session.doWork(connection -> execute(connection, "ALTER TABLE stored_image SET REFERENTIAL_INTEGRITY FALSE"));
        try {
            row.setRadiograph(work.entityManager().getReference(RadiographRow.class, 999));
            work.entityManager().persist(row);
            work.flush();
        } finally {
            session.doWork(
                    connection -> execute(connection, "ALTER TABLE stored_image SET REFERENTIAL_INTEGRITY TRUE"));
        }
    }

    private static Map<String, String> checkConstraints(Connection connection, String table) throws SQLException {
        Map<String, String> checks = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc"
                        + " JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc"
                        + " ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME"
                        + " WHERE tc.TABLE_NAME = ? AND tc.CONSTRAINT_TYPE = 'CHECK' ORDER BY tc.CONSTRAINT_NAME")) {
            query.setString(1, table);
            try (ResultSet found = query.executeQuery()) {
                while (found.next()) {
                    checks.put(found.getString(1), found.getString(2));
                }
            }
        }
        assertThat(checks).as("the check constraints on " + table).isNotEmpty();
        return checks;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    void aHealthyShopFindsNothing() {
        Examination found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = prepared(work);
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            return DoctorService.examine(work);
        });
        assertThat(found.isHealthy()).isTrue();
        assertThat(found.describe()).contains("nothing found");
        assertThat(found.checksRun()).hasSameSizeAs(DoctorService.checkNames());
    }

    @Test
    void anEmptyDatabaseIsHealthy() {
        TestDatabase.fresh()
                .transaction(work ->
                        assertThat(DoctorService.examine(work).isHealthy()).isTrue());
    }

    @Test
    void aSourceOutOnTwoTripsIsFound() {
        List<Finding> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            SourceRow source = aSourceRow();
            DeviceRow device = aDeviceRow();
            work.entityManager().persist(source);
            work.entityManager().persist(device);
            work.flush();
            for (String site : List.of("one", "two")) {
                sendOut(source, device, site);
            }
            work.flush();
            found = DoctorService.doubleBookings(work);
        }
        assertThat(found).hasSize(1);
        assertThat(found.get(0).subject()).isEqualTo("IR-5512");
        assertThat(found.get(0).account()).contains("2 open trips");
    }

    @Test
    void aSourceOutOnceIsNotADoubleBooking() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            SourceRow source = aSourceRow();
            DeviceRow device = aDeviceRow();
            work.entityManager().persist(source);
            work.entityManager().persist(device);
            work.flush();
            sendOut(source, device, "one");
            work.flush();
            assertThat(DoctorService.doubleBookings(work)).isEmpty();
        }
    }

    @Test
    void filmsIssuedThroughTheCounterHaveNoNumberingFinding() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work, 3);
            assertThat(DoctorService.numberingGaps(work)).isEmpty();
        });
    }

    @Test
    void aFilmNumberedPastTheCounterIsFound() {
        List<Finding> found = TestDatabase.fresh().transactionResult(work -> {
            prepared(work);
            CounterRow row = work.entityManager()
                    .createQuery("select c from CounterRow c where c.name = 'radiograph'", CounterRow.class)
                    .getSingleResult();
            row.setNextValue(1);
            work.flush();
            return DoctorService.numberingGaps(work);
        });
        assertThat(found).hasSize(1);
        assertThat(found.get(0).account()).contains("past a counter");
    }

    @Test
    void filmsWithNoCounterAtAllAreFound() {
        List<Finding> found = TestDatabase.fresh().transactionResult(work -> {
            prepared(work);
            for (CounterRow row : work.entityManager()
                    .createQuery("select c from CounterRow c", CounterRow.class)
                    .getResultList()) {
                work.entityManager().remove(row);
            }
            work.flush();
            return DoctorService.numberingGaps(work);
        });
        assertThat(found.get(0).account()).contains("no counter");
    }

    @Test
    void aFileWhoseExposureIsGoneIsFound() {
        List<Finding> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            StoredImageRow row = new StoredImageRow();
            row.setFilename("24-0001-R0001.dcm");
            row.setImageFormat("dcm");
            row.setPurpose("record");
            row.setRevision(0);
            row.setBitDepth(16);
            row.setWidth(2048);
            row.setHeight(512);
            row.setByteSize(512);
            row.setDigest("a".repeat(64));
            row.setCapturedAt(READ);
            row.setIngestedAt(READ);
            row.setIngestedBy("0142");
            orphaned(work, row);
            found = DoctorService.orphanedImages(work);
        }
        assertThat(found.get(0).subject()).isEqualTo("24-0001-R0001.dcm");
    }

    @Test
    void anInspectionAgainstADeviceNobodyRegisteredIsFound() {
        List<Finding> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(Inspection.quarterly("D-99", RAISED, "0303"));
            work.flush();
            found = DoctorService.unknownDevices(work);
        }
        assertThat(found.get(0).subject()).isEqualTo("D-99");
    }

    @Test
    void aReportClosedByItsAuthorIsFound() {
        List<Finding> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            legacy(work, aRow("0142", "rework"));
            found = DoctorService.selfClosedReports(work);
        }
        assertThat(found.get(0).subject()).isEqualTo("NCR-1");
        assertThat(found.get(0).account()).contains("0142");
    }

    @Test
    void aReportClosedBySomebodyElseIsNot() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            legacy(work, aRow("0303", "rework"));
            assertThat(DoctorService.selfClosedReports(work)).isEmpty();
        }
    }

    @Test
    void aReportClosedWithNothingDecidedIsFound() {
        List<Finding> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            legacy(work, aRow("0303", null));
            found = DoctorService.undecidedClosures(work);
        }
        assertThat(found.get(0).subject()).isEqualTo("NCR-1");
    }

    @Test
    void findingsAreGroupedByTheCheckThatFoundThem() {
        Map<String, List<Finding>> grouped;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(Inspection.quarterly("D-99", RAISED, "0303"));
            legacy(work, aRow("0303", null));
            grouped = DoctorService.examine(work).byCheck();
        }
        assertThat(grouped).containsOnlyKeys("unknown device", "undecided closure");
    }

    @Test
    void onlyTheNamedChecksRunWhenTheyAreAskedFor() {
        Examination found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(Inspection.quarterly("D-99", RAISED, "0303"));
            work.flush();
            found = DoctorService.examine(work, List.of("self closed"));
        }
        assertThat(found.checksRun()).containsExactly("self closed");
        assertThat(found.isHealthy()).isTrue();
    }

    @Test
    void aFindingPrintsTheCheckTheSubjectAndTheAccount() {
        String line;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.maintenance().record(Inspection.quarterly("D-99", RAISED, "0303"));
            work.flush();
            line = DoctorService.examine(work).findings().get(0).describe();
        }
        assertThat(line).startsWith("unknown device: D-99 ");
    }
}
