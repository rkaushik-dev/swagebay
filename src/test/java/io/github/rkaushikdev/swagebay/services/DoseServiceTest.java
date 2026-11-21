package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.safety.WorkerClass;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoseServiceTest {

    private static final LocalDateTime MARCH = LocalDateTime.of(2024, 3, 1, 0, 0);

    /** An entry for 2024 against {@code badge}, recorded on the fifteenth of {@code month} at nine. */
    private static void put(UnitOfWork work, String badge, String msv, int month) {
        DoseService.recordDose(work, badge, 2024, Dose.fromMsv(msv), LocalDateTime.of(2024, month, 15, 9, 0), "badge");
    }

    private static void put(UnitOfWork work, String msv, int month) {
        put(work, "0142", msv, month);
    }

    private static void put(UnitOfWork work, String msv) {
        put(work, "0142", msv, 3);
    }

    @Test
    void entriesAddUpAcrossTheYear() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "1", 1);
            put(work, "2", 6);
            assertThat(DoseService.takenInYear(work, "0142", 2024)).isEqualTo(Dose.fromMsv(3));
        });
    }

    @Test
    void somebodyWithNoEntriesHasTakenNothing() {
        TestDatabase.fresh().transaction(work -> assertThat(DoseService.takenInYear(work, "0999", 2024))
                .isEqualTo(Dose.ZERO));
    }

    @Test
    void aMonthIsSummedOnItsOwn() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "1", 3);
            put(work, "2", 4);
            assertThat(DoseService.takenInMonth(work, "0142", 2024, 3)).isEqualTo(Dose.fromMsv(1));
            assertThat(DoseService.takenInMonth(work, "0142", 2024, 4)).isEqualTo(Dose.fromMsv(2));
            assertThat(DoseService.takenInMonth(work, "0142", 2024, 5)).isEqualTo(Dose.ZERO);
        });
    }

    @Test
    void decemberDoesNotRunOffTheEndOfTheYear() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "1", 12);
            assertThat(DoseService.takenInMonth(work, "0142", 2024, 12)).isEqualTo(Dose.fromMsv(1));
        });
    }

    @Test
    void aMonthOutsideTheCalendarIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            assertThatThrownBy(() -> DoseService.takenInMonth(work, "0142", 2024, 13))
                    .isInstanceOf(ValidationError.class);
            assertThatThrownBy(() -> DoseService.takenInMonth(work, "0142", 2024, 0))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aDoseNeedsABadgeAndASource() {
        TestDatabase.fresh().transaction(work -> {
            assertThatThrownBy(() -> DoseService.recordDose(work, "  ", 2024, usv(10), MARCH, "badge"))
                    .isInstanceOf(ValidationError.class);
            assertThatThrownBy(() -> DoseService.recordDose(work, "0142", 2024, usv(10), MARCH, "  "))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aQuietYearAndABadMonthDisagreeAndBothAreRight() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "0.2", 1);
            put(work, "4", 7);
            assertThat(DoseService.takenInMonth(work, "0142", 2024, 7)).isEqualTo(Dose.fromMsv(4));
            assertThat(DoseService.position(work, "0142", 2024).investigationLevel())
                    .isZero();
        });
    }

    @Test
    void theInvestigationLevelsReadAgainstTheAnnualPosition() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "6");
            assertThat(DoseService.position(work, "0142", 2024).investigationLevel())
                    .isEqualTo(1);
            put(work, "10", 8);
            assertThat(DoseService.position(work, "0142", 2024).investigationLevel())
                    .isEqualTo(2);
        });
    }

    @Test
    void theLimitDependsOnTheWorkerClass() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "4");
            DosePosition adult = DoseService.position(work, "0142", 2024);
            DosePosition expecting = DoseService.position(work, "0142", 2024, WorkerClass.DECLARED_PREGNANT);
            assertThat(adult.investigationLevel()).isZero();
            assertThat(expecting.investigationLevel()).isEqualTo(2);
            assertThat(expecting.limit()).isLessThan(adult.limit());
        });
    }

    @Test
    void whatIsLeftOfTheYearRunsDownAndStopsAtZero() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "20");
            assertThat(DoseService.position(work, "0142", 2024).remaining()).isEqualTo(Dose.fromMsv(30));
            put(work, "40", 9);
            assertThat(DoseService.position(work, "0142", 2024).remaining()).isEqualTo(Dose.ZERO);
        });
    }

    @Test
    void theBadgesWithEntriesComeBackSorted() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "0201", "1", 3);
            put(work, "0142", "1", 3);
            assertThat(DoseService.badgesInYear(work, 2024)).containsExactly("0142", "0201");
        });
    }

    @Test
    void thePeopleOverALevelComeBackWorstFirst() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "0142", "6", 3);
            put(work, "0201", "16", 3);
            put(work, "0303", "1", 3);
            List<DosePosition> over = DoseService.overInvestigationLevel(work, 2024, 1);
            assertThat(over).extracting(DosePosition::badge).containsExactly("0201", "0142");
            assertThat(DoseService.overInvestigationLevel(work, 2024, 2))
                    .extracting(DosePosition::badge)
                    .containsExactly("0201");
        });
    }

    @Test
    void thereAreOnlyTwoInvestigationLevels() {
        TestDatabase.fresh()
                .transaction(work -> assertThatThrownBy(() -> DoseService.overInvestigationLevel(work, 2024, 3))
                        .isInstanceOf(ValidationError.class));
    }

    @Test
    void theMonthlySheetListsEverybodyInBadgeOrder() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "0201", "2", 5);
            put(work, "0142", "1", 5);
            List<BadgeDose> sheet = DoseService.monthlySheet(work, 2024, 5);
            assertThat(sheet).extracting(BadgeDose::badge).containsExactly("0142", "0201");
            assertThat(sheet.get(1).taken()).isEqualTo(Dose.fromMsv(2));
        });
    }

    @Test
    void theCollectiveDoseIsTheWholeShop() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "0142", "2", 3);
            put(work, "0201", "3", 3);
            assertThat(DoseService.collectiveDose(work, 2024)).isEqualTo(Dose.fromMsv(5));
            assertThat(DoseService.collectiveDose(work, 2025)).isEqualTo(Dose.ZERO);
        });
    }

    @Test
    void theBurnDownSaysHowLongTheYearLastsAtThisRate() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "5");
            assertThat(DoseService.daysLeftAtThisRate(DoseService.position(work, "0142", 2024), 100))
                    .hasValue(900);
        });
    }

    @Test
    void somebodyWhoHasTakenNothingHasNoBurnDown() {
        TestDatabase.fresh().transaction(work -> assertThat(
                        DoseService.daysLeftAtThisRate(DoseService.position(work, "0999", 2024), 100))
                .isEmpty());
    }

    @Test
    void aBurnDownNeedsSomeOfTheYearToHaveGone() {
        TestDatabase.fresh().transaction(work -> assertThatThrownBy(
                        () -> DoseService.daysLeftAtThisRate(DoseService.position(work, "0142", 2024), 0))
                .isInstanceOf(ValidationError.class));
    }

    @Test
    void aPositionPrintsTheWayTheBoardReads() {
        TestDatabase.fresh().transaction(work -> {
            put(work, "6");
            assertThat(DoseService.position(work, "0142", 2024).describe())
                    .isEqualTo("0142 6.00 mSv of 50 mSv, level 1");
        });
    }
}
