package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.WELD;
import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.prepared;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Disposition;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.evaluation.SpotExamination;
import io.github.rkaushikdev.swagebay.evaluation.Verdict;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadingServiceTest {

    private static final LocalDateTime READ = LocalDateTime.of(2024, 9, 25, 8, 0);

    @Test
    void aReadingIsStoredAgainstTheFilm() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            RadiographRow stored = work.welds().radiograph(shot.numbers().get(0));
            assertThat(stored.getReadBy()).contains("0142");
            assertThat(stored.getReadAt()).contains(READ);
        });
    }

    @Test
    void indicationsAreStoredWithTheReading() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            List<Indication> found = List.of(new Indication(Kind.ESI, mm(100), mm(20), mm(1)));
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ, found));
            RadiographRow stored = work.welds().radiograph(shot.numbers().get(0));
            assertThat(stored.getIndications()).hasSize(1);
            assertThat(stored.getIndications().get(0).getKind()).isEqualTo("ESI");
        });
    }

    @Test
    void aReaderWhoseEyeExamHasLapsedIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            Person stale = aReader("0142", Level.LEVEL_II, LocalDate.of(2022, 1, 1));
            assertThatThrownBy(() -> ReadingService.recordReading(
                            work, new ReadingRecord(shot.numbers().get(0), stale, READ)))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    (String) caught.context().get("lapsed"))
                            .contains("near vision test"));
        });
    }

    @Test
    void aLevelOneMayNotRead() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            assertThatThrownBy(() -> ReadingService.recordReading(
                            work, new ReadingRecord(shot.numbers().get(0), aReader("0142", Level.LEVEL_I), READ)))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aFilmCannotBeReadTwice() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            assertThatThrownBy(() -> ReadingService.recordReading(
                            work, new ReadingRecord(shot.numbers().get(0), aReader("0201"), READ)))
                    .isInstanceOfSatisfying(
                            ConflictError.class,
                            caught -> assertThat(caught.context().get("by")).isEqualTo("0142"));
        });
    }

    @Test
    void anInadmissibleFilmCannotBeReadAtAll() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work, 1, false);
            assertThatThrownBy(() -> ReadingService.recordReading(
                            work, new ReadingRecord(shot.numbers().get(0), aReader(), READ)))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(caught.message())
                            .contains("not evidence of anything"));
        });
    }

    @Test
    void aFilmThatDoesNotExistIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    prepared(work);
                    ReadingService.recordReading(work, new ReadingRecord("24-0001-R9999", aReader(), READ));
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aReadingNamesTheFilm() {
        assertThatThrownBy(() -> new ReadingRecord("   ", aReader(), READ)).isInstanceOf(ValidationError.class);
    }

    @Test
    void thereIsNoVerdictUntilEveryFilmIsRead() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work, 2);
            assertThat(ReadingService.weldVerdict(work, shot.job(), WELD)).isEmpty();
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            assertThat(ReadingService.weldVerdict(work, shot.job(), WELD)).isEmpty();
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(1), aReader(), READ));
            assertThat(ReadingService.weldVerdict(work, shot.job(), WELD)).isPresent();
        });
    }

    @Test
    void aWeldWithNoFilmsHasNoVerdict() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job =
                    JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", ServiceFixtures.OPENED));
            JobService.addWeld(
                    work,
                    job,
                    new NewWeld(
                            WELD,
                            PipeSchedule.girthWeld("6", "80", Materials.STEEL),
                            mm(529),
                            "w-19",
                            ServiceFixtures.OPENED));
            assertThat(ReadingService.weldVerdict(work, job, WELD)).isEmpty();
        });
    }

    @Test
    void aCleanWeldIsAccepted() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            Verdict verdict = ReadingService.weldVerdict(work, shot.job(), WELD).orElseThrow();
            assertThat(verdict.accepted()).isTrue();
            assertThat(verdict.code()).isSameAs(Code.API_1104);
        });
    }

    @Test
    void indicationsFromTwoFilmsAreJudgedTogether() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work, 2);
            ReadingService.recordReading(
                    work,
                    new ReadingRecord(
                            shot.numbers().get(0),
                            aReader(),
                            READ,
                            List.of(new Indication(Kind.ESI, mm(10), mm(30), mm(1)))));
            ReadingService.recordReading(
                    work,
                    new ReadingRecord(
                            shot.numbers().get(1),
                            aReader(),
                            READ,
                            List.of(new Indication(Kind.ESI, mm(60), mm(30), mm(1)))));
            Verdict verdict = ReadingService.weldVerdict(work, shot.job(), WELD).orElseThrow();
            assertThat(verdict.disposition()).isSameAs(Disposition.REPAIR);
        });
    }

    @Test
    void theFilmsStillWaitingForAReaderAreListed() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work, 2);
            assertThat(ReadingService.readingsOutstanding(work, shot.job())).isEqualTo(shot.numbers());
            ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
            assertThat(ReadingService.readingsOutstanding(work, shot.job()))
                    .containsExactly(shot.numbers().get(1));
        });
    }

    @Test
    void theCrewReportsWhoCanReadToday() {
        List<Person> crew = List.of(
                aReader("0142"),
                aReader("0201", Level.LEVEL_I),
                aReader("0303", Level.LEVEL_II, LocalDate.of(2022, 1, 1)));
        assertThat(ReadingService.readersAvailable(crew, READ.toLocalDate()))
                .extracting(Person::badge)
                .containsExactly("0142");
    }

    private static void read(UnitOfWork work, String number, Indication... found) {
        ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ, List.of(found)));
    }

    @Test
    void aSpotExaminedWeldIsFollowedThroughItsTracers() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = ServiceFixtures.prepared(work, 4, true, Code.ASME_VIII_SPOT);
            assertThat(ReadingService.spotStanding(work, shot.job(), WELD)).isEqualTo(new SpotExamination.NotShot());
            read(work, shot.numbers().get(0), new Indication(Kind.IF, mm(100), mm(3)));
            assertThat(ReadingService.spotStanding(work, shot.job(), WELD))
                    .isEqualTo(new SpotExamination.TracersDue(2));
            read(work, shot.numbers().get(1));
            read(work, shot.numbers().get(2));
            assertThat(ReadingService.spotStanding(work, shot.job(), WELD)).isEqualTo(new SpotExamination.RepairDue(0));
            read(work, shot.numbers().get(3));
            assertThat(ReadingService.spotStanding(work, shot.job(), WELD))
                    .isEqualTo(new SpotExamination.Accepted(true));
        });
    }

    @Test
    void theWholeWeldOnASpotJobIsStillJudgedUnderUw52() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = ServiceFixtures.prepared(work, 1, true, Code.ASME_VIII_SPOT);
            read(work, shot.numbers().get(0), new Indication(Kind.IF, mm(100), mm(3)));
            Verdict verdict = ReadingService.weldVerdict(work, shot.job(), WELD).orElseThrow();
            assertThat(verdict.code()).isSameAs(Code.ASME_VIII_SPOT);
            assertThat(verdict.clauses()).containsExactly("UW-52(c1)");
        });
    }

    @Test
    void onlyASpotJobHasASpotStanding() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            assertThatThrownBy(() -> ReadingService.spotStanding(work, shot.job(), WELD))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void anUnreadFilmStillHoldsTheVerdictBackThroughTheRecord() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work, 3);
            read(work, shot.numbers().get(0));
            read(work, shot.numbers().get(2));
            assertThat(ReadingService.weldVerdict(work, shot.job(), WELD)).isEmpty();
            read(work, shot.numbers().get(1), new Indication(Kind.ESI, mm(100), mm(60), mm(1)));
            assertThat(ReadingService.weldVerdict(work, shot.job(), WELD)
                            .orElseThrow()
                            .clauses())
                    .containsExactly("9.3.8");
        });
    }
}
