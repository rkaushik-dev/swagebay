package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.prepared;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.audit.Entry;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.IndicationRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RereadTest {

    private static final LocalDateTime READ = LocalDateTime.of(2024, 9, 25, 8, 0);
    private static final LocalDateTime AGAIN = READ.plusDays(3);
    private static final List<Indication> SLAG = List.of(new Indication(Kind.ESI, mm(100), mm(20), mm(1)));
    private static final List<Indication> POROSITY = List.of(new Indication(Kind.P, mm(100), mm(3), mm(3)));

    /** The job and the one film on it, read by badge {@code 0142}, who called the slag. */
    private record FirstReading(JobNumber job, String number) {}

    private static FirstReading firstReading(UnitOfWork work) {
        Prepared shot = prepared(work);
        String number = shot.numbers().get(0);
        ReadingService.recordReading(work, new ReadingRecord(number, aReader(), READ, SLAG));
        return new FirstReading(shot.job(), number);
    }

    private static void reread(UnitOfWork work, String number, String badge, String reason, List<Indication> found) {
        ReadingService.recordReread(work, new RereadRequest(number, aReader(badge), AGAIN, reason, found));
    }

    @Test
    void aRereadIsStoredBesideTheFirstReading() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "client asked", POROSITY);
            RadiographRow stored = work.welds().radiograph(number);
            assertThat(stored.getReadBy()).contains("0142");
            assertThat(stored.getRereadBy()).contains("0201");
            assertThat(stored.getRereadAt()).contains(AGAIN);
        });
    }

    @Test
    void theSecondReadersCallIsTheOneThatCounts() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "client asked", POROSITY);
            assertThat(work.welds().radiograph(number).getIndications())
                    .extracting(IndicationRow::getKind)
                    .containsExactly("P");
        });
    }

    @Test
    void whatTheFirstReaderSaidIsOnTheTrail() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "client asked", POROSITY);
            List<Entry> history = work.audit().forRecord("radiograph", number);
            assertThat(history).hasSize(1);
            assertThat(history.get(0).was()).startsWith("ESI 20.0 mm");
            assertThat(history.get(0).reason()).isEqualTo("client asked");
        });
    }

    @Test
    void aRereadThatAgreesIsRecordedAsASignatureNotAChange() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "second look", SLAG);
            assertThat(work.audit().forRecord("radiograph", number))
                    .extracting(entry -> entry.action().toString())
                    .containsExactly("signed");
        });
    }

    @Test
    void aRereadOfAFilmNobodyReadIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            assertThatThrownBy(() -> reread(work, shot.numbers().get(0), "0201", "why", SLAG))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aSecondRereadIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "client asked", POROSITY);
            assertThatThrownBy(() -> reread(work, number, "0303", "again", SLAG))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void theFirstReaderMayNotRereadTheirOwnFilm() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            assertThatThrownBy(() -> reread(work, number, "0142", "why", POROSITY))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aRereadDatedBeforeTheReadingIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            LocalDateTime earlier = READ.minusHours(1);
            assertThatThrownBy(() -> ReadingService.recordReread(
                            work, new RereadRequest(number, aReader("0201"), earlier, "why", POROSITY)))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aReaderWhoseEyesHaveLapsedMayNotReread() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            Person stale = aReader("0201", Level.LEVEL_II, LocalDate.of(2022, 1, 1));
            assertThatThrownBy(() ->
                            ReadingService.recordReread(work, new RereadRequest(number, stale, AGAIN, "why", POROSITY)))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aRereadWithNoReasonIsRefused() {
        assertThatThrownBy(() -> new RereadRequest("24-0001-R0001", aReader("0201"), AGAIN, "   ", POROSITY))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aRereadThatNamesNoFilmIsRefused() {
        assertThatThrownBy(() -> new RereadRequest("  ", aReader("0201"), AGAIN, "why", POROSITY))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void bothReadersComeBackInTheOrderTheyRead() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "client asked", POROSITY);
            assertThat(ReadingService.readersOf(work, number)).containsExactly("0142", "0201");
        });
    }

    @Test
    void aFilmWithOneReaderListsOne() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            assertThat(ReadingService.readersOf(work, number)).containsExactly("0142");
        });
    }

    @Test
    void aRereadFilmShowsUpAsDisputed() {
        TestDatabase.fresh().transaction(work -> {
            FirstReading first = firstReading(work);
            assertThat(ReadingService.disputed(work, first.job())).isEmpty();
            reread(work, first.number(), "0201", "client asked", POROSITY);
            assertThat(ReadingService.disputed(work, first.job())).containsExactly(first.number());
        });
    }

    @Test
    void clearingTheCallIsRecordedAsAChangeToNothing() {
        TestDatabase.fresh().transaction(work -> {
            String number = firstReading(work).number();
            reread(work, number, "0201", "no such call", List.of());
            List<Entry> history = work.audit().forRecord("radiograph", number);
            assertThat(history.get(0).became()).isEqualTo("nothing");
            assertThat(work.welds().radiograph(number).getIndications()).isEmpty();
        });
    }
}
