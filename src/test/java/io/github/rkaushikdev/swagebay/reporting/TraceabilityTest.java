package io.github.rkaushikdev.swagebay.reporting;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.LicenceRole;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.procedure.ProcedureRegister;
import io.github.rkaushikdev.swagebay.procedure.Revision;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import io.github.rkaushikdev.swagebay.services.ReadingRecord;
import io.github.rkaushikdev.swagebay.services.ReadingService;
import io.github.rkaushikdev.swagebay.services.ShootingService;
import io.github.rkaushikdev.swagebay.services.ShotRecord;
import io.github.rkaushikdev.swagebay.testing.ImagingFixtures;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceabilityTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 11, 1);
    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 11, 8, 23, 0);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 11, 9, 8, 0);
    private static final WeldId WELD = WeldId.parse("GW-104");

    /** A job, and the one film shot on it. */
    private record Prepared(JobNumber job, String number) {}

    private static String shoot(UnitOfWork work, JobNumber job, int index, boolean visible) {
        ShotRecord shot = new ShotRecord(WELD, index, SHOT, "RT-001 rev 1", ImagingFixtures.filmEvidence(visible));
        return ShootingService.recordShot(work, job, shot).number();
    }

    /** A job with GW-104 on it and one exposure of it, read when it was visible and {@code read} says so. */
    private static Prepared prepared(UnitOfWork work, boolean visible, boolean read) {
        JobNumber job = JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", OPENED));
        JobService.addWeld(
                work,
                job,
                new NewWeld(WELD, PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), mm(529), "w-19", OPENED));
        String number = shoot(work, job, 1, visible);
        if (read && visible) {
            ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ));
        }
        return new Prepared(job, number);
    }

    private static Prepared prepared(UnitOfWork work) {
        return prepared(work, true, true);
    }

    private static ProcedureRegister register(LocalDate effective) {
        Person signer = new Person(
                "0001",
                "level three",
                Level.LEVEL_III,
                LicenceRole.SAFETY_OFFICER,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 1));
        return new ProcedureRegister(List.of(new Revision(
                "RT-001",
                1,
                "radiography of butt welds",
                io.github.rkaushikdev.swagebay.procedure.Code.ASME_V,
                effective,
                signer,
                effective)));
    }

    private static ProcedureRegister register() {
        return register(LocalDate.of(2024, 1, 1));
    }

    private static Link linkNamed(Chain chain, String name) {
        return chain.links().stream()
                .filter(link -> link.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aSoundChainHoldsAtEveryLink() {
        TestDatabase.fresh().transaction(work -> {
            Chain chain = Traceability.chainFor(work, prepared(work).number());
            assertThat(chain.isSound()).isTrue();
            assertThat(chain.broken()).isEmpty();
            assertThat(chain.links()).hasSize(8);
        });
    }

    @Test
    void theChainNamesTheJobTheWeldAndTheWelder() {
        TestDatabase.fresh().transaction(work -> {
            Map<String, String> values = new HashMap<>();
            for (Link link :
                    Traceability.chainFor(work, prepared(work).number()).links()) {
                values.put(link.name(), link.value());
            }
            assertThat(values.get("job")).isEqualTo("24-0001");
            assertThat(values.get("weld")).isEqualTo("GW-104");
            assertThat(values.get("welder")).isEqualTo("w-19");
            assertThat(values.get("technique")).isEqualTo("RT-001 rev 1");
        });
    }

    @Test
    void anUnreadFilmBreaksTheReadingLink() {
        TestDatabase.fresh().transaction(work -> {
            Chain chain =
                    Traceability.chainFor(work, prepared(work, true, false).number());
            assertThat(chain.isSound()).isFalse();
            assertThat(chain.broken()).extracting(Link::name).contains("read by");
        });
    }

    @Test
    void anInadmissibleFilmBreaksTheAdmissibilityLinkAndSaysWhy() {
        TestDatabase.fresh().transaction(work -> {
            Link link = linkNamed(
                    Traceability.chainFor(work, prepared(work, false, true).number()), "admissible");
            assertThat(link.holds()).isFalse();
            assertThat(link.note()).contains("not visible");
        });
    }

    @Test
    void aLinkPrintsItsStateAndItsNote() {
        TestDatabase.fresh().transaction(work -> {
            String number = prepared(work, true, false).number();
            String text = Traceability.chainFor(work, number).describe();
            assertThat(text).contains(number, "[ok]", "[broken]", "not yet read");
        });
    }

    @Test
    void theProcedureLinkIsUncheckedWithoutARegister() {
        TestDatabase.fresh().transaction(work -> {
            Link link = linkNamed(Traceability.chainFor(work, prepared(work).number()), "procedure");
            assertThat(link.holds()).isTrue();
            assertThat(link.note()).contains("not checked");
        });
    }

    @Test
    void theProcedureLinkResolvesTheRevisionInForceThatNight() {
        TestDatabase.fresh().transaction(work -> {
            Link link = linkNamed(Traceability.chainFor(work, prepared(work).number(), register()), "procedure");
            assertThat(link.holds()).isTrue();
            assertThat(link.value()).isEqualTo("RT-001 rev 1");
        });
    }

    @Test
    void aProcedureThatWasNotYetInForceBreaksTheLink() {
        TestDatabase.fresh().transaction(work -> {
            ProcedureRegister later = register(LocalDate.of(2025, 6, 1));
            Link link = linkNamed(Traceability.chainFor(work, prepared(work).number(), later), "procedure");
            assertThat(link.holds()).isFalse();
            assertThat(link.note()).contains("no approved revision");
        });
    }

    @Test
    void everyChainOnAJobComesBackInRadiographOrder() {
        TestDatabase.fresh().transaction(work -> {
            Prepared first = prepared(work);
            String second = shoot(work, first.job(), 2, true);
            ReadingService.recordReading(work, new ReadingRecord(second, aReader(), READ));
            assertThat(Traceability.chainsForJob(work, 24, 1))
                    .extracting(Chain::radiograph)
                    .isEqualTo(List.of(first.number(), second).stream().sorted().toList());
        });
    }

    @Test
    void theBrokenChainsAreTheOnesWorthReading() {
        TestDatabase.fresh().transaction(work -> {
            Prepared first = prepared(work);
            shoot(work, first.job(), 2, true);
            List<Chain> broken = Traceability.brokenChains(work, 24, 1);
            assertThat(broken).hasSize(1);
            assertThat(broken.get(0).isSound()).isFalse();
        });
    }

    @Test
    void aJobWithEverythingSoundReportsNoBrokenChains() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work);
            assertThat(Traceability.brokenChains(work, 24, 1)).isEmpty();
        });
    }

    @Test
    void assertingTraceabilityRefusesABrokenChain() {
        TestDatabase.fresh().transaction(work -> {
            String number = prepared(work, true, false).number();
            assertThatThrownBy(() -> Traceability.assertTraceable(work, number)).isInstanceOf(NotFoundError.class);
        });
    }

    @Test
    void assertingTraceabilityReturnsASoundChain() {
        TestDatabase.fresh().transaction(work -> assertThat(
                        Traceability.assertTraceable(work, prepared(work).number())
                                .isSound())
                .isTrue());
    }

    @Test
    void aFilmThatDoesNotExistHasNoChain() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    prepared(work);
                    Traceability.chainFor(work, "24-0001-R9999");
                }))
                .isInstanceOf(NotFoundError.class);
    }
}
