package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MorningTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 6, 1, 0, 0);
    private static final LocalDate DAY = LocalDate.of(2025, 7, 10);

    private static SealedSource aSource() {
        return new SealedSource(
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                curie(15));
    }

    /** Source IR-5512 in camera D-77, the source wiped then if at all, the camera inspected for the day or not. */
    private static void aShop(UnitOfWork work, Optional<LocalDateTime> wipedAt, boolean inspected) {
        SourceService.registerSource(work, aSource());
        SourceService.registerDevice(work, "D-77", "880d");
        wipedAt.ifPresent(at -> WipeService.recordWipe(work, "IR-5512", at, BigDecimal.ZERO, "rp"));
        if (inspected) {
            WorkFixtures.passInspections(work, "D-77", DAY);
        }
    }

    private static void aShop(UnitOfWork work, LocalDateTime wipedAt) {
        aShop(work, Optional.of(wipedAt), true);
    }

    private static void anUninspectedShop(UnitOfWork work) {
        aShop(work, Optional.of(ASSAY), false);
    }

    private static Report aReport(LocalDate raisedOn) {
        return new Report("NCR-1", "24-0001", "GW-104", "API 1104 9.3.8", "elongated slag", raisedOn, "0142");
    }

    @Test
    void aShopWithEverythingCurrentIsQuiet() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, LocalDateTime.of(2025, 7, 1, 0, 0));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.isQuiet()).isTrue();
        assertThat(sheet.describe()).contains("nothing outstanding");
    }

    @Test
    void aDeviceNobodyInspectedStopsWorkToday() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            anUninspectedShop(work);
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsToday()).anyMatch(line -> line.account().contains("no quarterly"));
    }

    @Test
    void aQuarterlyThatRanOutStopsWorkToday() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            anUninspectedShop(work);
            work.maintenance().record(Inspection.quarterly("D-77", LocalDate.of(2025, 1, 1), "0303"));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsToday()).anyMatch(line -> line.account().contains("ran out"));
    }

    @Test
    void aQuarterlyDueNextWeekIsThisWeekAndNotToday() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, Optional.of(LocalDateTime.of(2025, 7, 1, 0, 0)), false);
            work.maintenance().record(Inspection.quarterly("D-77", DAY.minusDays(88), "0303"));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsToday()).isEmpty();
        assertThat(sheet.stopsThisWeek()).anyMatch(line -> line.account().contains("quarterly due by"));
    }

    @Test
    void aSourceWithNoWipeTestStopsWorkToday() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, Optional.empty(), true);
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsToday()).anyMatch(line -> line.account().contains("no wipe test"));
    }

    @Test
    void aWipeTestThatRanOutStopsWorkToday() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, LocalDateTime.of(2024, 6, 1, 0, 0));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsToday()).anyMatch(line -> line.account().contains("wipe test ran out"));
    }

    @Test
    void aWipeTestDueNextWeekIsThisWeek() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, LocalDateTime.of(2025, 1, 15, 0, 0));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsThisWeek()).anyMatch(line -> line.account().contains("wipe test due by"));
    }

    @Test
    void anUndecidedReportIsThisWeek() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, LocalDateTime.of(2025, 7, 1, 0, 0));
            work.nonconformances().raiseReport(aReport(DAY));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.stopsThisWeek()).anyMatch(line -> line.account().contains("undecided"));
    }

    @Test
    void aReportOpenTwoMonthsIsRunningLate() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, LocalDateTime.of(2025, 7, 1, 0, 0));
            work.nonconformances().raiseReport(aReport(DAY.minusDays(60)));
            return MorningService.sheetFor(work, DAY);
        });
        assertThat(sheet.runningLate()).anyMatch(line -> line.account().contains("open 60 days"));
        assertThat(sheet.stopsThisWeek()).isEmpty();
    }

    @Test
    void theSheetReadsTodayThenThisWeekThenLate() {
        Sheet sheet = TestDatabase.fresh().transactionResult(work -> {
            aShop(work, Optional.empty(), false);
            work.nonconformances().raiseReport(aReport(DAY.minusDays(60)));
            return MorningService.sheetFor(work, DAY);
        });
        List<String> urgencies = sheet.lines().stream().map(Line::urgency).toList();
        assertThat(urgencies.get(0)).isEqualTo("today");
        assertThat(urgencies.get(urgencies.size() - 1)).isEqualTo("late");
        assertThat(sheet.describe()).contains("outstanding on");
    }

    @Test
    void aLinePrintsTheSubjectAndTheAccount() {
        Line line = TestDatabase.fresh().transactionResult(work -> {
            anUninspectedShop(work);
            return MorningService.sheetFor(work, DAY).stopsToday().get(0);
        });
        assertThat(line.describe()).startsWith("D-77: ");
    }

    @Test
    void thePairingsThatCouldLeaveAreListed() {
        TestDatabase.fresh().transaction(work -> {
            aShop(work, LocalDateTime.of(2025, 7, 1, 0, 0));
            assertThat(MorningService.loadsReady(work, DAY)).containsExactly(new Load("D-77", "IR-5512"));
        });
    }

    @Test
    void aShopThatCannotSendAnythingOutListsNothing() {
        TestDatabase.fresh().transaction(work -> {
            anUninspectedShop(work);
            assertThat(MorningService.loadsReady(work, DAY)).isEmpty();
        });
    }

    @Test
    void theFilmsWaitingAreTheUnreadOnes() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = ServiceFixtures.prepared(work, 2);
            assertThat(MorningService.filmsWaiting(work, LocalDate.of(2024, 9, 24)))
                    .containsExactlyInAnyOrderElementsOf(shot.numbers());
        });
    }

    @Test
    void aShopWithNoOpenJobsHasNoFilmsWaiting() {
        TestDatabase.fresh().transaction(work -> {
            aShop(work, ASSAY);
            assertThat(MorningService.filmsWaiting(work, DAY)).isEmpty();
        });
    }
}
