package io.github.rkaushikdev.swagebay.reporting;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import io.github.rkaushikdev.swagebay.services.ReadingRecord;
import io.github.rkaushikdev.swagebay.services.ReadingService;
import io.github.rkaushikdev.swagebay.services.ShootingService;
import io.github.rkaushikdev.swagebay.services.ShotRecord;
import io.github.rkaushikdev.swagebay.testing.ImagingFixtures;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReportPackageTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 11, 1);
    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 11, 5, 23, 0);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 11, 6, 8, 0);

    private static JobNumber aJob(UnitOfWork work) {
        return JobService.openJob(
                work, new NewJob("Maritime Pipelines", "Come By Chance", Code.API_1104, "RT-001", OPENED));
    }

    private static void aWeld(UnitOfWork work, JobNumber job, String weld) {
        JobService.addWeld(
                work,
                job,
                new NewWeld(
                        WeldId.parse(weld),
                        PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)),
                        mm(529),
                        "w-19",
                        OPENED));
    }

    private static void aWeld(UnitOfWork work, JobNumber job) {
        aWeld(work, job, "GW-104");
    }

    private static String shoot(UnitOfWork work, JobNumber job, String weld, int index, boolean visible) {
        ShotRecord shot =
                new ShotRecord(WeldId.parse(weld), index, SHOT, "RT-001 rev 1", ImagingFixtures.filmEvidence(visible));
        return ShootingService.recordShot(work, job, shot).number();
    }

    private static String shoot(UnitOfWork work, JobNumber job, String weld) {
        return shoot(work, job, weld, 1, true);
    }

    private static String shoot(UnitOfWork work, JobNumber job) {
        return shoot(work, job, "GW-104");
    }

    private static void read(UnitOfWork work, String number, List<Indication> found) {
        ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ, found));
    }

    private static void read(UnitOfWork work, String number) {
        read(work, number, List.of());
    }

    private static Report aReport(String job) {
        return new Report("NCR-1", job, "GW-104", "API 1104 9.3.8", "elongated slag", OPENED, "0142");
    }

    @Test
    void aFinishedJobAssemblesComplete() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            read(work, shoot(work, job));
            ReportPackage assembled = ReportPackage.assemble(work, job);
            assertThat(assembled.isComplete()).isTrue();
            assertThat(assembled.faults()).isEmpty();
            assertThat(assembled.client()).isEqualTo("Maritime Pipelines");
            assertThat(assembled.code()).isSameAs(Code.API_1104);
        });
    }

    @Test
    void anEmptyJobIsNotFinishable() {
        TestDatabase.fresh().transaction(work -> {
            ReportPackage assembled = ReportPackage.assemble(work, aJob(work));
            assertThat(assembled.isComplete()).isFalse();
            assertThat(assembled.faults()).contains("the job has no welds on it");
        });
    }

    @Test
    void aWeldWithNoFilmsIsNamedInTheFaults() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            List<String> faults = ReportPackage.assemble(work, job).faults();
            assertThat(faults).contains("GW-104 has no radiographs");
            assertThat(faults).contains("GW-104 has no disposition");
        });
    }

    @Test
    void anUnreadFilmStopsThePackage() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            String number = shoot(work, job);
            assertThat(ReportPackage.assemble(work, job).faults()).contains(number + " has not been read");
        });
    }

    @Test
    void anInadmissibleFilmStopsItToo() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            String number = shoot(work, job, "GW-104", 1, false);
            assertThat(ReportPackage.assemble(work, job).faults()).contains(number + " is not admissible");
        });
    }

    @Test
    void theRadiographScheduleListsEveryFilmInOrder() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            for (int index : List.of(2, 1, 3)) {
                read(work, shoot(work, job, "GW-104", index, true));
            }
            ReportPackage assembled = ReportPackage.assemble(work, job);
            assertThat(assembled.radiographs())
                    .extracting(RadiographLine::exposureIndex)
                    .containsExactly(1, 2, 3);
            assertThat(assembled.radiographs()).allMatch(line -> line.weld().equals("GW-104"));
        });
    }

    @Test
    void theSummaryAndTheDetailAgree() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job, "GW-104");
            aWeld(work, job, "GW-110");
            for (String weld : List.of("GW-104", "GW-110")) {
                read(work, shoot(work, job, weld));
            }
            ReportPackage assembled = ReportPackage.assemble(work, job);
            assertThat(assembled.countsAgree()).isTrue();
            assertThat(assembled.welds()).hasSize(2);
            assertThat(assembled.radiographs()).hasSize(2);
        });
    }

    @Test
    void aWeldCarriesItsDispositionAndTheClausesBehindIt() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            read(work, shoot(work, job), List.of(new Indication(Kind.ESI, mm(10), mm(60), mm(1))));
            WeldLine line = ReportPackage.assemble(work, job).welds().get(0);
            assertThat(line.disposition()).isEqualTo("repair");
            assertThat(line.clauses()).containsExactly("9.3.8");
        });
    }

    @Test
    void aCleanWeldReadsAsAccepted() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            read(work, shoot(work, job));
            assertThat(ReportPackage.assemble(work, job).welds().get(0).disposition())
                    .isEqualTo("accept");
        });
    }

    @Test
    void theRepairRateCountsWeldsNotRepairs() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = aJob(work);
            for (String weld : List.of("GW-104", "GW-104R1", "GW-110", "GW-111", "GW-112")) {
                aWeld(work, job, weld);
                read(work, shoot(work, job, weld));
            }
            ReportPackage assembled = ReportPackage.assemble(work, job);
            assertThat(assembled.weldsShot()).isEqualTo(5);
            assertThat(assembled.weldsRepaired()).isEqualTo(1);
            assertThat(assembled.repairRate().repaired()).isEqualTo(1);
            assertThat(assembled.repairRate().shot()).isEqualTo(5);
            assertThat(assembled.repairRate().value())
                    .isEqualByComparingTo(BigDecimal.ONE.divide(BigDecimal.valueOf(5)));
        });
    }

    @Test
    void aJobWithNothingShotHasNoRepairRateRatherThanACrash() {
        TestDatabase.fresh().transaction(work -> {
            Rate rate = ReportPackage.assemble(work, aJob(work)).repairRate();
            assertThat(rate.value()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(rate.isReportable()).isFalse();
        });
    }

    @Test
    void aJobThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> ReportPackage.assemble(work, new JobNumber(24, 99))))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void thePackageCarriesTheDatesTheJobWasOpen() {
        TestDatabase.fresh().transaction(work -> {
            ReportPackage assembled = ReportPackage.assemble(work, aJob(work));
            assertThat(assembled.openedOn()).isEqualTo(OPENED);
            assertThat(assembled.closedOn()).isEmpty();
        });
    }

    @Test
    void aRateWithADenominatorBehindItIsReportable() {
        Rate rate = TestDatabase.fresh().transactionResult(work -> {
            JobNumber job = aJob(work);
            for (int index = 1; index <= 24; index++) {
                String weld = String.format(Locale.ROOT, "GW-%03d", index);
                aWeld(work, job, weld);
                read(work, shoot(work, job, weld));
            }
            return ReportPackage.assemble(work, job).repairRate();
        });
        assertThat(rate.shot()).isEqualTo(24);
        assertThat(rate.isReportable()).isTrue();
        assertThat(rate.describe()).contains("on 24");
    }

    @Test
    void aRateOnAHandfulOfWeldsSaysSoRatherThanAPercentage() {
        Rate rate = TestDatabase.fresh().transactionResult(work -> {
            JobNumber job = aJob(work);
            for (String weld : List.of("GW-104", "GW-104R1")) {
                aWeld(work, job, weld);
                read(work, shoot(work, job, weld));
            }
            return ReportPackage.assemble(work, job).repairRate();
        });
        assertThat(rate.isReportable()).isFalse();
        assertThat(rate.describe()).isEqualTo("1 of 2, too few to rate");
    }

    @Test
    void thePackageCarriesTheReportsRaisedAgainstTheJob() {
        ReportPackage assembled = TestDatabase.fresh().transactionResult(work -> {
            JobNumber job = aJob(work);
            work.nonconformances().raiseReport(aReport(job.toString()));
            return ReportPackage.assemble(work, job);
        });
        assertThat(assembled.reports()).extracting(Report::reference).containsExactly("NCR-1");
        assertThat(assembled.faults()).contains("NCR-1 is still open");
    }

    @Test
    void anotherJobsReportsAreNotOnThisPackage() {
        ReportPackage assembled = TestDatabase.fresh().transactionResult(work -> {
            JobNumber job = aJob(work);
            work.nonconformances()
                    .raiseReport(new Report("NCR-1", "24-9999", "GW-104", "API 1104", "slag", OPENED, "0142"));
            return ReportPackage.assemble(work, job);
        });
        assertThat(assembled.reports()).isEmpty();
    }

    @Test
    void thePackageCarriesTheFilesInNameOrder() {
        ReportPackage assembled = TestDatabase.fresh().transactionResult(work -> {
            JobNumber job = aJob(work);
            aWeld(work, job);
            String number = shoot(work, job);
            read(work, number);
            record Copy(int revision, ImageFormat imageFormat, Purpose purpose, int bits) {}
            for (Copy copy : List.of(
                    new Copy(1, ImageFormat.JPEG, Purpose.VIEWING, 8),
                    new Copy(0, ImageFormat.DICONDE, Purpose.RECORD, 16))) {
                work.store()
                        .ingest(
                                new StoredImage(
                                        number,
                                        ImageStore.expectedName(number, copy.imageFormat(), copy.revision()),
                                        copy.imageFormat(),
                                        copy.bits(),
                                        2048,
                                        512,
                                        512,
                                        ImageStore.digestOf(
                                                (number + "-" + copy.revision()).getBytes(StandardCharsets.UTF_8)),
                                        LocalDateTime.of(2024, 11, 3, 22, 0),
                                        copy.purpose()),
                                READ,
                                "0142");
            }
            return ReportPackage.assemble(work, job);
        });
        List<String> names =
                assembled.files().stream().map(StoredImage::filename).toList();
        assertThat(names).isSorted();
        assertThat(names).hasSize(2);
    }

    @Test
    void aJobWithNoFilesCarriesNone() {
        TestDatabase.fresh().transaction(work -> assertThat(
                        ReportPackage.assemble(work, aJob(work)).files())
                .isEmpty());
    }
}
