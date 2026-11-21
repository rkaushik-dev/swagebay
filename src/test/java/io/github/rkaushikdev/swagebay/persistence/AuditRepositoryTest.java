package io.github.rkaushikdev.swagebay.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.audit.Action;
import io.github.rkaushikdev.swagebay.audit.Entry;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditRepositoryTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2025, 5, 13, 8, 30);
    private static final LocalDateTime LATER = WHEN.plusHours(2);
    private static final LocalDateTime LATEST = WHEN.plusHours(4);

    @Test
    void anAppendedEntryComesBack() {
        List<Entry> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            found = work.audit().forRecord("weld", "GW-104");
        }
        assertThat(found).hasSize(1);
        assertThat(found.get(0).action()).isSameAs(Action.CREATED);
        assertThat(found.get(0).actor()).isEqualTo("0142");
    }

    @Test
    void aHistoryReadsOldestFirstEvenWhenWrittenOutOfOrder() {
        List<Action> actions;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept");
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            actions = work.audit().forRecord("weld", "GW-104").stream()
                    .map(Entry::action)
                    .toList();
        }
        assertThat(actions).containsExactly(Action.CREATED, Action.CHANGED);
    }

    @Test
    void twoEntriesAtTheSameInstantKeepTheOrderTheyWereWritten() {
        List<String> became;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
            work.audit().changed(WHEN, "0142", "weld", "GW-104", "disposition", "repair", "accept");
            became = work.audit().forRecord("weld", "GW-104").stream()
                    .map(Entry::became)
                    .toList();
        }
        assertThat(became).containsExactly("repair", "accept");
    }

    @Test
    void anotherRecordIsNotInThisHistory() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            work.audit().created(WHEN, "0142", "weld", "GW-110");
            assertThat(work.audit().forRecord("weld", "GW-104")).hasSize(1);
        }
    }

    @Test
    void onePersonsDayCanBePulledOut() {
        List<Entry> mine;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            work.audit().created(LATEST, "0201", "weld", "GW-110");
            mine = work.audit().byActor("0142", WHEN, WHEN.plusDays(1));
        }
        assertThat(mine).hasSize(1);
    }

    @Test
    void aDayThatEndsBeforeTheEntryDoesNotIncludeIt() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(LATER, "0142", "weld", "GW-104");
            assertThat(work.audit().byActor("0142", WHEN, LATER)).isEmpty();
        }
    }

    @Test
    void theCurrentValueIsTheLastThingTheFieldBecame() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
            work.audit().changed(LATER, "0201", "weld", "GW-104", "disposition", "repair", "accept");
            assertThat(work.audit().current("weld", "GW-104", "disposition")).contains("accept");
        }
    }

    @Test
    void aFieldNobodyTouchedHasNoCurrentValue() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            assertThat(work.audit().current("weld", "GW-104", "welder")).isEmpty();
        }
    }

    @Test
    void aChangeAfterTheSignatureIsFound() {
        List<Entry> after;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
            work.audit().signed(LATER, "0303", "weld", "GW-104");
            work.audit().changed(LATEST, "0201", "weld", "GW-104", "disposition", "repair", "accept");
            after = work.audit().changedAfterSigning("weld", "GW-104");
        }
        assertThat(after).extracting(Entry::actor).containsExactly("0201");
    }

    @Test
    void nothingIsFoundAfterASignatureNobodyGave() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.audit().changed(WHEN, "0142", "weld", "GW-104", "disposition", "none", "repair");
            assertThat(work.audit().changedAfterSigning("weld", "GW-104")).isEmpty();
        }
    }

    @Test
    void theTrailSurvivesTheTransactionThatWroteIt() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> work.audit().created(WHEN, "0142", "weld", "GW-104"));
        try (UnitOfWork work = made.unitOfWork()) {
            assertThat(work.audit().forRecord("weld", "GW-104")).hasSize(1);
        }
    }

    @Test
    void aTransactionThatDidNotReachTheEndLeavesNoEntry() {
        Database made = TestDatabase.fresh();
        try (UnitOfWork work = made.unitOfWork()) {
            work.audit().created(WHEN, "0142", "weld", "GW-104");
        }
        try (UnitOfWork work = made.unitOfWork()) {
            assertThat(work.audit().forRecord("weld", "GW-104")).isEmpty();
        }
    }

    @Test
    void aClosedUnitOfWorkRefusesTheTrail() {
        UnitOfWork work = new UnitOfWork(TestDatabase.fresh().entityManagerFactory());
        assertThatThrownBy(() -> work.audit().created(WHEN, "0142", "weld", "GW-104"))
                .isInstanceOf(ConflictError.class);
    }
}
