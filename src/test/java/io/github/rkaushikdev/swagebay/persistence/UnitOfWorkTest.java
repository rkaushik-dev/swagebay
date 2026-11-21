package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.persistence.Numbering.RADIOGRAPH;
import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class UnitOfWorkTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 9, 2);

    /** Every repository a unit of work offers, by name, the way a caller reaches it. */
    private static final Map<String, Function<UnitOfWork, Repository>> REPOSITORIES = repositories();

    private static final List<String> REGISTERS =
            List.of("incidents", "calibrations", "stock", "archive", "qualifications");

    private static Map<String, Function<UnitOfWork, Repository>> repositories() {
        Map<String, Function<UnitOfWork, Repository>> reach = new LinkedHashMap<>();
        reach.put("sources", UnitOfWork::sources);
        reach.put("jobs", UnitOfWork::jobs);
        reach.put("welds", UnitOfWork::welds);
        reach.put("numbering", UnitOfWork::numbering);
        reach.put("incidents", UnitOfWork::incidents);
        reach.put("calibrations", UnitOfWork::calibrations);
        reach.put("stock", UnitOfWork::stock);
        reach.put("archive", UnitOfWork::archive);
        reach.put("qualifications", UnitOfWork::qualifications);
        reach.put("audit", UnitOfWork::audit);
        reach.put("store", UnitOfWork::store);
        reach.put("maintenance", UnitOfWork::maintenance);
        reach.put("nonconformances", UnitOfWork::nonconformances);
        reach.put("shop", UnitOfWork::shop);
        return reach;
    }

    private static Repository reach(UnitOfWork work, String name) {
        return REPOSITORIES.get(name).apply(work);
    }

    private static UnitOfWork unopened() {
        return new UnitOfWork(TestDatabase.fresh().entityManagerFactory());
    }

    private static JobRow jobRow(int serial) {
        JobRow row = new JobRow();
        row.setYear(24);
        row.setSerial(serial);
        row.setClient("c");
        row.setSite("s");
        row.setAcceptanceCode("API 1104");
        row.setProcedureNumber("RT-001");
        row.setOpenedOn(OPENED);
        return row;
    }

    private static JobRow jobRow() {
        return jobRow(417);
    }

    private static SourceRow sourceRow() {
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
        return row;
    }

    private static IncidentRow incident(String reference) {
        IncidentRow row = new IncidentRow();
        row.setReference(reference);
        row.setOccurrence("dose over a limit");
        row.setDiscoveredAt(LocalDateTime.of(2025, 3, 31, 23, 0));
        row.setSite("site");
        row.setReportedBy("0142");
        row.setAccount("a");
        return row;
    }

    @Test
    void workCommittedInsideTheBlockIsThereAfterwards() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> work.jobs().add(jobRow()));
        made.transaction(
                work -> assertThat(work.jobs().get(JobNumber.parse("24-0417")).getClient())
                        .isEqualTo("c"));
    }

    @Test
    void anExceptionInsideTheBlockRollsTheWholeThingBack() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    work.jobs().add(jobRow());
                    work.flush();
                    throw new IllegalStateException("the crew went home");
                }))
                .isInstanceOf(IllegalStateException.class);
        made.transaction(
                work -> assertThat(work.jobs().find(JobNumber.parse("24-0417"))).isEmpty());
    }

    @Test
    void aNumberTakenAndThenRolledBackIsNotLost() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    work.numbering().take(RADIOGRAPH, "24-0417");
                    throw new IllegalStateException("no");
                }))
                .isInstanceOf(IllegalStateException.class);
        made.transaction(
                work -> assertThat(work.numbering().take(RADIOGRAPH, "24-0417")).isEqualTo(1));
    }

    @Test
    void theNumberAndTheWorkItNumbersCommitTogether() {
        Database made = TestDatabase.fresh();
        String number = made.transactionResult(work -> {
            work.jobs().add(jobRow());
            return work.numbering().takeRadiographNumber("24-0417");
        });
        assertThat(number).isEqualTo("24-0417-R0001");
        made.transaction(work -> {
            assertThat(work.numbering().peek(RADIOGRAPH, "24-0417")).isEqualTo(2);
            assertThat(work.jobs().find(JobNumber.parse("24-0417"))).isPresent();
        });
    }

    @Test
    void everyRepositorySharesTheOneSession() {
        TestDatabase.fresh().transaction(work -> {
            work.sources().add(sourceRow());
            work.jobs().add(jobRow());
            work.flush();
            assertThat(work.sources().get("IR-5512").getId()).isPresent();
            assertThat(work.welds().entityManager()).isSameAs(work.sources().entityManager());
        });
    }

    @Test
    void anExplicitRollbackUndoesTheWorkSoFar() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            work.jobs().add(jobRow(417));
            work.flush();
            work.rollback();
            work.jobs().add(jobRow(418));
        });
        made.transaction(work -> {
            assertThat(work.jobs().find(JobNumber.parse("24-0417"))).isEmpty();
            assertThat(work.jobs().find(JobNumber.parse("24-0418"))).isPresent();
        });
    }

    @Test
    void aUnitOfWorkThatWasNeverOpenedRefusesToBeUsed() {
        UnitOfWork work = unopened();
        List<ThrowingCallable> calls = List.of(work::commit, work::rollback, work::flush);
        for (ThrowingCallable call : calls) {
            assertThatThrownBy(call).isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void theRepositoriesAreNotOptionalOnceTheBlockIsEntered() {
        TestDatabase.fresh().transaction(work -> {
            assertThat(work.isOpen()).isTrue();
            assertThat(work.jobs().entityManager()).isSameAs(work.entityManager());
            assertThat(work.welds().entityManager()).isSameAs(work.entityManager());
            assertThat(work.sources().entityManager()).isSameAs(work.entityManager());
        });
    }

    @Test
    void leavingTheBlockClosesTheRepositoriesToo() {
        UnitOfWork work = unopened();
        try (UnitOfWork open = work.open()) {
            assertThat(open.isOpen()).isTrue();
            assertThat(open.jobs()).isNotNull();
        }
        assertThat(work.isOpen()).isFalse();
        List<ThrowingCallable> reaches =
                List.of(work::entityManager, work::jobs, work::welds, work::sources, work::numbering);
        for (ThrowingCallable reach : reaches) {
            assertThatThrownBy(reach).isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void aLookupThatMissesStillRaisesInsideAUnitOfWork() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> work.sources().get("IR-9999")))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSchemaBuilderMakesEveryTable() {
        try (Database database = Database.inMemory()) {
            database.migrate();
            assertThat(database.tableNames()).contains("job", "weld", "radiograph", "source", "counter");
        }
    }

    @Test
    void aSecondUnitOfWorkSeesTheFirstOneCommitted() {
        Database made = TestDatabase.fresh();
        made.transaction(first -> first.sources().add(sourceRow()));
        made.transaction(second ->
                assertThat(second.sources().get("IR-5512").getSerial()).isEqualTo("IR-5512"));
    }

    @Test
    void everyRegisterIsOnTheUnitOfWork() {
        TestDatabase.fresh().transaction(work -> {
            for (String name : REGISTERS) {
                assertThat(reach(work, name)).as(name).isNotNull();
            }
        });
    }

    @Test
    void everyRegisterSharesTheOneSession() {
        TestDatabase.fresh().transaction(work -> {
            for (String name : REGISTERS) {
                assertThat(reach(work, name).entityManager()).as(name).isSameAs(work.entityManager());
            }
        });
    }

    @Test
    void everyRegisterRefusesOnceTheBlockIsLeft() {
        UnitOfWork work = unopened();
        try (UnitOfWork open = work.open()) {
            assertThat(open.incidents()).isNotNull();
        }
        for (String name : REGISTERS) {
            assertThatThrownBy(() -> reach(work, name)).as(name).isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void aRegisterWriteCommitsWithTheRestOfTheWork() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            work.entityManager().persist(incident("INC-1"));
            work.jobs().add(jobRow());
        });
        made.transaction(work -> {
            assertThat(work.incidents().get("INC-1").getSite()).isEqualTo("site");
            assertThat(work.jobs().find(new JobNumber(24, 417))).isPresent();
        });
    }

    @Test
    void aRegisterWriteRollsBackWithTheRestOfTheWork() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    work.entityManager().persist(incident("INC-2"));
                    work.flush();
                    throw new IllegalStateException("no");
                }))
                .isInstanceOf(IllegalStateException.class);
        made.transaction(work -> assertThat(work.incidents().find("INC-2")).isEmpty());
    }

    @Test
    void aRepositoryReachedBeforeOpeningRefuses() {
        UnitOfWork work = unopened();
        for (String name : List.of("sources", "jobs", "welds", "numbering", "audit", "stock")) {
            assertThatThrownBy(() -> reach(work, name)).as(name).isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void everyRepositoryIsBuiltWhenTheBlockOpens() {
        try (UnitOfWork work = unopened().open()) {
            for (String name : List.of("sources", "jobs", "welds", "numbering", "audit", "archive")) {
                assertThat(reach(work, name)).as(name).isNotNull();
            }
        }
    }

    @Test
    void theSameRepositoryComesBackTwiceInOneBlock() {
        try (UnitOfWork work = unopened().open()) {
            assertThat(work.jobs()).isSameAs(work.jobs());
        }
    }

    @Test
    void aNewBlockBuildsANewRepository() {
        Database made = TestDatabase.fresh();
        JobRepository first;
        try (UnitOfWork work = made.unitOfWork()) {
            first = work.jobs();
        }
        try (UnitOfWork work = made.unitOfWork()) {
            assertThat(work.jobs()).isNotSameAs(first);
        }
    }

    @Test
    void leavingTheBlockPutsTheRepositoriesBack() {
        UnitOfWork work = unopened();
        try (UnitOfWork open = work.open()) {
            assertThat(open.isOpen()).isTrue();
        }
        assertThatThrownBy(work::welds).isInstanceOf(ConflictError.class);
    }

    /**
     * The repositories are the unit's public accessors that take nothing and return a repository,
     * and they are exactly the ones named here: each is reached by its own name and returns its own
     * kind of repository.
     */
    @Test
    void theAccessorOnTheClassIsTheAccessor() throws NoSuchMethodException {
        List<String> offered = Arrays.stream(UnitOfWork.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0)
                .filter(method -> Repository.class.isAssignableFrom(method.getReturnType()))
                .map(Method::getName)
                .sorted()
                .toList();
        assertThat(offered).isEqualTo(REPOSITORIES.keySet().stream().sorted().toList());
        Method jobs = UnitOfWork.class.getMethod("jobs");
        assertThat(jobs.getName()).isEqualTo("jobs");
        assertThat(jobs.getReturnType()).isEqualTo(JobRepository.class);
    }
}
