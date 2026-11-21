package io.github.rkaushikdev.swagebay;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Disposition;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.evaluation.Verdict;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.reporting.Chain;
import io.github.rkaushikdev.swagebay.reporting.ReportPackage;
import io.github.rkaushikdev.swagebay.reporting.Traceability;
import io.github.rkaushikdev.swagebay.services.Booking;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import io.github.rkaushikdev.swagebay.services.PlanRequest;
import io.github.rkaushikdev.swagebay.services.PlanningService;
import io.github.rkaushikdev.swagebay.services.ReadingRecord;
import io.github.rkaushikdev.swagebay.services.ReadingService;
import io.github.rkaushikdev.swagebay.services.ShootingService;
import io.github.rkaushikdev.swagebay.services.ShotRecord;
import io.github.rkaushikdev.swagebay.services.ShotResult;
import io.github.rkaushikdev.swagebay.services.SourceService;
import io.github.rkaushikdev.swagebay.services.WipeService;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.github.rkaushikdev.swagebay.testing.ImagingFixtures;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * One job walked from end to end, the way the office actually does it.
 *
 * <p>Every other test class checks one thing in isolation, which is right and which leaves one gap:
 * whether the pieces still fit together. This walks a whole job through the same services the API
 * and the command line call, in the order a coordinator would call them, one unit of work after
 * another, and checks the state after each step.
 *
 * <p>The first walk is deliberately one long test rather than fifteen short ones. The value is in the
 * sequence, and a sequence split into fifteen tests with a fixture in between is fifteen tests that
 * share a fixture and prove nothing about order.
 */
class EndToEndTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 2, 1, 0, 0);
    private static final LocalDateTime SHOT = LocalDateTime.of(2025, 2, 12, 23, 30);
    private static final LocalDateTime READ = LocalDateTime.of(2025, 2, 13, 8, 0);
    private static final LocalDate OPENED = LocalDate.of(2025, 2, 3);
    private static final LocalDate CLOSED = LocalDate.of(2025, 2, 20);
    private static final WeldId WELD = WeldId.parse("GW-104");
    private static final WeldId REPAIR = WeldId.parse("GW-104R1");

    private static SealedSource aSource() {
        return new SealedSource(
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                curie(15));
    }

    private static NewWeld aWeld(WeldId weld) {
        return new NewWeld(weld, PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), mm(529), "w-19", OPENED);
    }

    private static ShotResult shoot(
            UnitOfWork work, JobNumber job, WeldId weld, int index, LocalDateTime at, Evidence evidence) {
        return ShootingService.recordShot(work, job, new ShotRecord(weld, index, at, "RT-001 rev 1", evidence));
    }

    /** The source on the inventory with a clean wipe, the camera, and the job opened for {@code client}. */
    private static JobNumber aShopAndAJob(UnitOfWork work, String client, String site) {
        SourceService.registerSource(work, aSource());
        SourceService.registerDevice(work, "880D-221", "880d");
        WipeService.recordWipe(work, "IR-5512", ASSAY, BigDecimal.ZERO, "rp");
        JobNumber job = JobService.openJob(work, new NewJob(client, site, Code.API_1104, "RT-001", OPENED));
        JobService.addWeld(work, job, aWeld(WELD));
        return job;
    }

    /** A job with a fit source and one weld on it, which is where the night starts. */
    private static JobNumber prepared(Database made) {
        return made.transactionResult(work -> aShopAndAJob(work, "c", "s"));
    }

    @Test
    void aJobWalksFromOpenToClosed() {
        Database made = TestDatabase.fresh();

        JobNumber job = made.transactionResult(work -> aShopAndAJob(work, "Maritime Pipelines", "Come By Chance"));

        int exposures = made.transactionResult(work -> {
            WorkFixtures.passInspections(work, "880D-221", LocalDate.of(2025, 2, 12));
            SourceService.sendOut(
                    work, new Booking("IR-5512", "880D-221", "0142", "Come By Chance", LocalDate.of(2025, 2, 12)));
            TechniqueSheet sheet = PlanningService.plan(
                    work,
                    job,
                    PlanRequest.builder(WELD, "IR-5512", "d7", SHOT)
                            .shot(ShotType.DWSI)
                            .build());
            assertThat(sheet.isClean()).isTrue();
            return sheet.exposures();
        });

        List<String> numbers = made.transactionResult(work -> {
            assertThat(ShootingService.missingExposures(work, job, WELD, exposures))
                    .isEqualTo(IntStream.rangeClosed(1, exposures).boxed().toList());
            List<String> shot = new ArrayList<>();
            for (int index = 1; index <= exposures; index++) {
                shot.add(shoot(work, job, WELD, index, SHOT, ImagingFixtures.filmEvidence(SHOT))
                        .number());
            }
            assertThat(ShootingService.missingExposures(work, job, WELD, exposures))
                    .isEmpty();
            return shot;
        });

        made.transaction(work -> {
            assertThat(ReadingService.weldVerdict(work, job, WELD)).isEmpty();
            for (int index = 0; index < numbers.size(); index++) {
                List<Indication> found =
                        index == 0 ? List.of(new Indication(Kind.ESI, mm(10), mm(60), mm(1))) : List.of();
                ReadingService.recordReading(work, new ReadingRecord(numbers.get(index), aReader(), READ, found));
            }
            Verdict verdict = ReadingService.weldVerdict(work, job, WELD).orElseThrow();
            assertThat(verdict.disposition()).isSameAs(Disposition.REPAIR);
            assertThat(verdict.clauses()).containsExactly("9.3.8");
        });

        made.transaction(work -> {
            SourceService.bringBack(work, "IR-5512", LocalDate.of(2025, 2, 13), exposures);
            JobService.addWeld(work, job, aWeld(REPAIR));
        });

        made.transaction(work -> {
            LocalDateTime again = SHOT.plusDays(3);
            for (int index = 1; index <= exposures; index++) {
                String number = shoot(work, job, REPAIR, index, again, ImagingFixtures.filmEvidence(again))
                        .number();
                ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ.plusDays(3)));
            }
            assertThat(ReadingService.weldVerdict(work, job, REPAIR)
                            .orElseThrow()
                            .accepted())
                    .isTrue();
        });

        made.transaction(work -> {
            assertThat(JobService.outstanding(work, job)).isEmpty();
            assertThat(Traceability.brokenChains(work, job.year(), job.serial()))
                    .isEmpty();
            ReportPackage assembled = ReportPackage.assemble(work, job);
            assertThat(assembled.isComplete()).isTrue();
            assertThat(assembled.countsAgree()).isTrue();
            assertThat(assembled.weldsShot()).isEqualTo(2);
            assertThat(assembled.weldsRepaired()).isEqualTo(1);
            assertThat(assembled.repairRate().repaired()).isEqualTo(1);
            assertThat(assembled.repairRate().shot()).isEqualTo(2);
            assertThat(assembled.repairRate().isReportable()).isFalse();
            assertThat(assembled.radiographs()).hasSize(exposures * 2);
        });

        made.transaction(work -> {
            JobService.closeJob(work, job, CLOSED);
            assertThat(work.jobs().get(job).getClosedOn()).contains(CLOSED);
        });

        made.transaction(work -> {
            assertThat(ReportPackage.assemble(work, job).closedOn()).contains(CLOSED);
            assertThat(ASSAY.atOffset(ZoneOffset.UTC).toLocalDate()).isEqualTo(LocalDate.of(2025, 2, 1));
        });
    }

    @Test
    void anInadmissibleFilmBlocksTheJobUntilItIsReshot() {
        Database made = TestDatabase.fresh();
        JobNumber job = prepared(made);

        ShotResult bad = made.transactionResult(work -> {
            ShotResult shot = shoot(work, job, WELD, 1, SHOT, ImagingFixtures.filmEvidence(false, SHOT));
            assertThat(shot.needsReshooting()).isTrue();
            return shot;
        });

        made.transaction(work -> {
            assertThat(JobService.outstanding(work, job)).contains(bad.number() + " is not admissible");
            assertThat(ReadingService.weldVerdict(work, job, WELD)).isEmpty();
            assertThat(ReportPackage.assemble(work, job).isComplete()).isFalse();
        });

        made.transaction(work -> {
            List<Chain> chains = Traceability.brokenChains(work, job.year(), job.serial());
            assertThat(chains).hasSize(1);
            assertThat(chains.get(0).broken()).anyMatch(link -> link.name().contains("admissible"));
        });

        made.transaction(work -> {
            ShotResult good = shoot(work, job, WELD, 2, SHOT.plusHours(1), ImagingFixtures.filmEvidence(SHOT));
            ReadingService.recordReading(work, new ReadingRecord(good.number(), aReader(), READ));
        });

        made.transaction(work -> {
            assertThat(JobService.outstanding(work, job)).contains(bad.number() + " is not admissible");
            assertThat(ReadingService.weldVerdict(work, job, WELD)).isEmpty();
        });
    }

    @Test
    void aJobWillNotCloseWhileAnythingIsOutstanding() {
        Database made = TestDatabase.fresh();
        JobNumber job = prepared(made);

        assertThatThrownBy(() -> made.transaction(work -> JobService.closeJob(work, job, CLOSED)))
                .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                (Integer) caught.context().get("outstanding"))
                        .isGreaterThanOrEqualTo(1));

        String number =
                made.transactionResult(work -> shoot(work, job, WELD, 1, SHOT, ImagingFixtures.filmEvidence(SHOT))
                        .number());

        assertThatThrownBy(() -> made.transaction(work -> JobService.closeJob(work, job, CLOSED)))
                .isInstanceOf(ConflictError.class);

        made.transaction(work -> {
            ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ));
            assertThat(JobService.outstanding(work, job)).isEmpty();
            JobService.closeJob(work, job, CLOSED);
        });
    }

    @Test
    void aSourceThatWentOutAndCameBackReadsAsTheShopAgain() {
        Database made = TestDatabase.fresh();
        JobNumber job = prepared(made);

        made.transaction(work -> {
            WorkFixtures.passInspections(work, "880D-221", LocalDate.of(2025, 2, 12));
            SourceService.sendOut(work, new Booking("IR-5512", "880D-221", "0142", "site", LocalDate.of(2025, 2, 12)));
            assertThat(work.sources().openTrip("IR-5512")).isPresent();
        });

        made.transaction(work -> {
            SourceService.bringBack(work, "IR-5512", LocalDate.of(2025, 2, 13), 8);
            assertThat(work.sources().openTrip("IR-5512")).isEmpty();
            TripRow trip = work.sources().get("IR-5512").getTrips().get(0);
            assertThat(trip.getExposures()).isEqualTo(8);
            assertThat(trip.getCameBack()).contains(LocalDate.of(2025, 2, 13));
            assertThat(job.serial()).isEqualTo(1);
        });
    }
}
