package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.persistence.Numbering.RADIOGRAPH;
import static io.github.rkaushikdev.swagebay.persistence.Numbering.formatRadiograph;
import static io.github.rkaushikdev.swagebay.persistence.Numbering.formatReport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.CounterRow;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NumberingTest {

    private static Numbering numbering(TestSession db) {
        return new Numbering(db.entityManager());
    }

    @Test
    void theFirstNumberIssuedIsOne() {
        try (TestSession db = TestSession.open()) {
            assertThat(numbering(db).take(RADIOGRAPH, "24-0417")).isEqualTo(1);
        }
    }

    @Test
    void numbersComeOutInOrderWithNoHoles() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            List<Integer> issued = IntStream.range(0, 5)
                    .mapToObj(each -> counter.take(RADIOGRAPH, "24-0417"))
                    .toList();
            assertThat(issued).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Test
    void twoScopesNumberIndependently() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            assertThat(counter.take(RADIOGRAPH, "24-0417")).isEqualTo(1);
            assertThat(counter.take(RADIOGRAPH, "24-0418")).isEqualTo(1);
            assertThat(counter.take(RADIOGRAPH, "24-0417")).isEqualTo(2);
        }
    }

    @Test
    void twoCountersInOneScopeAreSeparateToo() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            assertThat(counter.take("radiograph", "24-0417")).isEqualTo(1);
            assertThat(counter.take("report", "24-0417")).isEqualTo(1);
        }
    }

    @Test
    void peekingDoesNotTake() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            assertThat(counter.peek(RADIOGRAPH, "24-0417")).isEqualTo(1);
            assertThat(counter.peek(RADIOGRAPH, "24-0417")).isEqualTo(1);
            counter.take(RADIOGRAPH, "24-0417");
            assertThat(counter.peek(RADIOGRAPH, "24-0417")).isEqualTo(2);
        }
    }

    @Test
    void aScopeNeverUsedHasNoRowAtAll() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            assertThat(counter.peek(RADIOGRAPH, "24-9999")).isEqualTo(1);
            assertThat(db.all(CounterRow.class)).isEmpty();
        }
    }

    @Test
    void aRolledBackTransactionTakesTheNumberBackWithIt() {
        Database made = TestDatabase.fresh();
        try (TestSession db = TestSession.on(made)) {
            numbering(db).take(RADIOGRAPH, "24-0417");
            db.commit();
        }
        try (TestSession db = TestSession.on(made)) {
            assertThat(numbering(db).take(RADIOGRAPH, "24-0417")).isEqualTo(2);
            db.rollback();
        }
        try (TestSession db = TestSession.on(made)) {
            assertThat(numbering(db).take(RADIOGRAPH, "24-0417")).isEqualTo(2);
        }
    }

    @Test
    void aCommittedNumberStaysTaken() {
        Database made = TestDatabase.fresh();
        try (TestSession db = TestSession.on(made)) {
            assertThat(numbering(db).take(RADIOGRAPH, "24-0417")).isEqualTo(1);
            db.commit();
        }
        try (TestSession db = TestSession.on(made)) {
            assertThat(numbering(db).peek(RADIOGRAPH, "24-0417")).isEqualTo(2);
        }
    }

    @Test
    void aCounterNeedsAName() {
        try (TestSession db = TestSession.open()) {
            assertThatThrownBy(() -> numbering(db).take("   ")).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void aRadiographNumberReadsTheWayTheEnvelopeIsWritten() {
        assertThat(formatRadiograph("24-0417", 42)).isEqualTo("24-0417-R0042");
        assertThat(formatRadiograph("24-0417", 1)).isEqualTo("24-0417-R0001");
        assertThat(formatRadiograph("24-0417", 10000)).isEqualTo("24-0417-R10000");
    }

    @Test
    void aReportNumberHasItsOwnShape() {
        assertThat(formatReport("24-0417", 3)).isEqualTo("24-0417-RPT003");
    }

    @Test
    void aNumberBelowOneCannotBeFormatted() {
        assertThatThrownBy(() -> formatRadiograph("24-0417", 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> formatReport("24-0417", -1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void takingAFormattedNumberAdvancesTheCounter() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            assertThat(counter.takeRadiographNumber("24-0417")).isEqualTo("24-0417-R0001");
            assertThat(counter.takeRadiographNumber("24-0417")).isEqualTo("24-0417-R0002");
            assertThat(counter.takeReportNumber("24-0417")).isEqualTo("24-0417-RPT001");
        }
    }

    @Test
    void theCounterRowIsCreatedOnceAndThenUpdated() {
        try (TestSession db = TestSession.open()) {
            Numbering counter = numbering(db);
            for (int round = 0; round < 4; round++) {
                counter.take(RADIOGRAPH, "24-0417");
            }
            List<CounterRow> rows = db.all(CounterRow.class);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getNextValue()).isEqualTo(5);
        }
    }
}
