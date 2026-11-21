package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RepositoriesTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 8, 1, 0, 0);
    private static final LocalDate OPENED = LocalDate.of(2024, 8, 1);

    private static SourceRow sourceRow(String serial, String state) {
        SourceRow row = new SourceRow();
        row.setSerial(serial);
        row.setIsotopeKey("ir192");
        row.setAssayActivity(curie(90));
        row.setAssayAt(ASSAY);
        row.setManufacturer("QSA");
        row.setModel("A424-9");
        row.setActiveDiameter(mm(3));
        row.setActiveLength(mm(3));
        row.setState(state);
        row.setWorkingFloor(curie(15));
        return row;
    }

    private static SourceRow sourceRow() {
        return sourceRow("IR-5512", "in service");
    }

    private static JobRow jobRow(int serial, LocalDate closed) {
        JobRow row = new JobRow();
        row.setYear(24);
        row.setSerial(serial);
        row.setClient("c");
        row.setSite("s");
        row.setAcceptanceCode("API 1104");
        row.setProcedureNumber("RT-001");
        row.setOpenedOn(OPENED);
        row.setClosedOn(closed);
        return row;
    }

    private static JobRow jobRow(int serial) {
        return jobRow(serial, null);
    }

    private static WeldRow weldRow(int number, int repair) {
        WeldRow row = new WeldRow();
        row.setPrefix("GW");
        row.setNumber(number);
        row.setRepairRound(repair);
        row.setMaterialKey("carbon_steel");
        row.setWall(mm(10));
        row.setReinforcement(Length.ZERO);
        row.setWeldLength(mm(500));
        row.setWelder("w-19");
        row.setWeldedOn(OPENED);
        return row;
    }

    private static RadiographRow film(String number, int index, boolean admissible, boolean read) {
        RadiographRow row = new RadiographRow();
        row.setNumber(number);
        row.setExposureIndex(index);
        row.setExposedAt(LocalDateTime.of(2024, 8, 20, 23, 0));
        row.setTechniqueReference("RT-001 rev 1");
        row.setMedium("film");
        row.setAdmissible(admissible);
        row.setReadBy(read ? "0142" : null);
        row.setReadAt(read ? LocalDateTime.of(2024, 8, 21, 8, 0) : null);
        return row;
    }

    private static RadiographRow film(String number, int index) {
        return film(number, index, true, true);
    }

    private static DeviceRow deviceRow() {
        DeviceRow row = new DeviceRow();
        row.setSerial("880D-221");
        row.setModelKey("880d");
        return row;
    }

    private static TripRow trip(SourceRow source, DeviceRow device, String site, LocalDate out, LocalDate back) {
        TripRow row = new TripRow();
        row.setSource(source);
        row.setDevice(device);
        row.setCrewBadge("0142");
        row.setSite(site);
        row.setWentOut(out);
        row.setCameBack(back);
        return row;
    }

    @Test
    void aSourceIsFoundBySerial() {
        try (TestSession db = TestSession.open()) {
            SourceRepository repository = new SourceRepository(db.entityManager());
            repository.add(sourceRow());
            db.commit();
            assertThat(repository.get("IR-5512").getIsotopeKey()).isEqualTo("ir192");
            assertThat(repository.find("IR-9999")).isEmpty();
        }
    }

    @Test
    void aMissingSourceRaisesWithTheIdentityOnIt() {
        try (TestSession db = TestSession.open()) {
            assertThatThrownBy(() -> new SourceRepository(db.entityManager()).get("IR-9999"))
                    .isInstanceOfSatisfying(NotFoundError.class, missing -> {
                        assertThat(missing.identity()).isEqualTo("IR-9999");
                        assertThat(missing.kind()).isEqualTo("source");
                    });
        }
    }

    @Test
    void onlyTheSourcesInServiceComeBack() {
        try (TestSession db = TestSession.open()) {
            SourceRepository repository = new SourceRepository(db.entityManager());
            repository.add(sourceRow("IR-5512", "in service"));
            repository.add(sourceRow("IR-6000", "awaiting disposal"));
            db.commit();
            assertThat(repository.inService()).extracting(SourceRow::getSerial).containsExactly("IR-5512");
        }
    }

    @Test
    void theOpenTripIsTheOneWithNoReturn() {
        try (TestSession db = TestSession.open()) {
            SourceRepository repository = new SourceRepository(db.entityManager());
            SourceRow source = sourceRow();
            DeviceRow device = deviceRow();
            db.addAll(source, device);
            db.flush();
            db.add(trip(source, device, "a", LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 2)));
            db.commit();
            assertThat(repository.openTrip("IR-5512")).isEmpty();
            db.add(trip(source, device, "b", LocalDate.of(2024, 8, 5), null));
            db.commit();
            assertThat(repository.openTrip("IR-5512"))
                    .hasValueSatisfying(open -> assertThat(open.getSite()).isEqualTo("b"));
        }
    }

    @Test
    void aMissingDeviceRaisesToo() {
        try (TestSession db = TestSession.open()) {
            assertThatThrownBy(() -> new SourceRepository(db.entityManager()).device("880D-999"))
                    .isInstanceOf(NotFoundError.class);
        }
    }

    @Test
    void aJobIsFoundByItsNumber() {
        try (TestSession db = TestSession.open()) {
            JobRepository repository = new JobRepository(db.entityManager());
            repository.add(jobRow(417));
            db.commit();
            assertThat(repository.get(JobNumber.parse("24-0417")).getClient()).isEqualTo("c");
            assertThat(repository.find(JobNumber.parse("24-0999"))).isEmpty();
        }
    }

    @Test
    void aMissingJobRaisesWithTheNumberOnIt() {
        try (TestSession db = TestSession.open()) {
            assertThatThrownBy(() -> new JobRepository(db.entityManager()).get(JobNumber.parse("24-0999")))
                    .isInstanceOfSatisfying(NotFoundError.class, missing -> assertThat(missing.identity())
                            .isEqualTo("24-0999"));
        }
    }

    @Test
    void theJobsOpenOnADayExcludeTheClosedOnes() {
        try (TestSession db = TestSession.open()) {
            JobRepository repository = new JobRepository(db.entityManager());
            repository.add(jobRow(417));
            repository.add(jobRow(418, LocalDate.of(2024, 8, 10)));
            db.commit();
            assertThat(repository.openOn(LocalDate.of(2024, 8, 20)))
                    .extracting(JobRow::getSerial)
                    .containsExactly(417);
            assertThat(repository.openOn(LocalDate.of(2024, 8, 5))).hasSize(2);
            assertThat(repository.openOn(LocalDate.of(2024, 7, 1))).isEmpty();
        }
    }

    @Test
    void theNextSerialFollowsTheHighestUsed() {
        try (TestSession db = TestSession.open()) {
            JobRepository repository = new JobRepository(db.entityManager());
            assertThat(repository.nextSerial(24)).isEqualTo(1);
            repository.add(jobRow(417));
            db.commit();
            assertThat(repository.nextSerial(24)).isEqualTo(418);
            assertThat(repository.nextSerial(25)).isEqualTo(1);
        }
    }

    @Test
    void aWeldIsFoundByItsIdentifierAndRound() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow(417);
            job.addWeld(weldRow(104, 0));
            job.addWeld(weldRow(104, 1));
            db.add(job);
            db.commit();
            WeldRepository repository = new WeldRepository(db.entityManager());
            assertThat(repository.get(job, WeldId.parse("GW-104")).getRepairRound())
                    .isZero();
            assertThat(repository.get(job, WeldId.parse("GW-104R1")).getRepairRound())
                    .isEqualTo(1);
            assertThat(repository.find(job, WeldId.parse("GW-999"))).isEmpty();
        }
    }

    @Test
    void everyRoundOfAWeldComesBackAsFirstMadeFirst() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow(417);
            job.addWeld(weldRow(104, 1));
            job.addWeld(weldRow(104, 0));
            db.add(job);
            db.commit();
            assertThat(new WeldRepository(db.entityManager()).rounds(job, WeldId.parse("GW-104")))
                    .extracting(WeldRow::getRepairRound)
                    .containsExactly(0, 1);
        }
    }

    @Test
    void theUnreadAndInadmissibleFilmsAreFoundPerJob() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow(417);
            WeldRow weld = weldRow(104, 0);
            weld.addRadiograph(film("R-001", 1));
            weld.addRadiograph(film("R-002", 2, true, false));
            weld.addRadiograph(film("R-003", 3, false, true));
            job.addWeld(weld);
            db.add(job);
            db.commit();
            WeldRepository repository = new WeldRepository(db.entityManager());
            assertThat(repository.unread(job))
                    .extracting(RadiographRow::getNumber)
                    .containsExactly("R-002");
            assertThat(repository.inadmissible(job))
                    .extracting(RadiographRow::getNumber)
                    .containsExactly("R-003");
        }
    }

    @Test
    void aRadiographIsFoundByItsNumberAcrossTheShop() {
        try (TestSession db = TestSession.open()) {
            JobRow job = jobRow(417);
            WeldRow weld = weldRow(104, 0);
            weld.addRadiograph(film("R-001", 1));
            job.addWeld(weld);
            db.add(job);
            db.commit();
            WeldRepository repository = new WeldRepository(db.entityManager());
            assertThat(repository.radiograph("R-001").getExposureIndex()).isEqualTo(1);
            assertThatThrownBy(() -> repository.radiograph("R-404")).isInstanceOf(NotFoundError.class);
        }
    }

    @Test
    void aRepositoryNeverCommitsOnItsOwn() {
        try (TestSession db = TestSession.open()) {
            new SourceRepository(db.entityManager()).add(sourceRow());
            db.rollback();
            assertThat(new SourceRepository(db.entityManager()).find("IR-5512")).isEmpty();
        }
    }
}
