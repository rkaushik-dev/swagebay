package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.safety.Inspection.preUse;
import static io.github.rkaushikdev.swagebay.safety.Inspection.quarterly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenanceTest {

    private static final LocalDate STRIPPED = LocalDate.of(2025, 4, 14);
    private static final LocalDate MORNING = LocalDate.of(2025, 6, 16);
    private static final Defect CABLE =
            new Defect(InspectionItem.DRIVE_CABLE, "three strands broken 200 mm from the connector");
    private static final Defect WHEEL = new Defect(InspectionItem.WHEELS, "one castor seized");
    private static final List<InspectionItem> EVERY_ITEM = List.of(InspectionItem.values());

    @Test
    void aFaultOnASafetyItemStopsTheDevice() {
        assertThat(InspectionItem.DRIVE_CABLE.stopsTheDevice()).isTrue();
        assertThat(InspectionItem.SOURCE_LOCK.severity()).isSameAs(Severity.STOPS_THE_DEVICE);
        assertThat(CABLE.stopsTheDevice()).isTrue();
    }

    @Test
    void aFaultOnTheWheelsDoesNot() {
        assertThat(InspectionItem.WHEELS.stopsTheDevice()).isFalse();
        assertThat(WHEEL.stopsTheDevice()).isFalse();
    }

    @Test
    void theSafetyItemsAreTheOnesThatStopIt() {
        assertThat(InspectionItem.safetyItems()).contains(InspectionItem.GUIDE_TUBE);
        assertThat(InspectionItem.safetyItems()).doesNotContain(InspectionItem.LABELS);
    }

    @Test
    void aDefectThatDoesNotSayWhatIsWrongIsRefused() {
        assertThatThrownBy(() -> new Defect(InspectionItem.WHEELS, "   ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aDefectPrintsTheItemAndTheAccount() {
        assertThat(WHEEL.describe()).isEqualTo("wheels: one castor seized");
    }

    @Test
    void aPreUseCheckCoversTheSafetyItems() {
        Inspection check = preUse("D-77", MORNING, "0142");
        assertThat(check.itemsChecked()).containsExactlyInAnyOrderElementsOf(InspectionItem.safetyItems());
        assertThat(check.passed()).isTrue();
        assertThat(check.expiresOn()).isEmpty();
    }

    @Test
    void aQuarterlyCoversEverything() {
        Inspection strip = quarterly("D-77", STRIPPED, "0303");
        assertThat(strip.itemsChecked()).containsExactlyInAnyOrderElementsOf(EVERY_ITEM);
        assertThat(strip.expiresOn()).hasValue(STRIPPED.plusDays(Inspection.QUARTERLY_DAYS));
    }

    @Test
    void aQuarterlyThatSkippedAnItemIsRefused() {
        assertThatThrownBy(() -> new Inspection("D-77", STRIPPED, "0303", true, List.of(InspectionItem.DRIVE_CABLE)))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(
                                (String) caught.context().get("missed"))
                        .contains("labels"));
    }

    @Test
    void anInspectionThatCheckedNothingIsRefused() {
        assertThatThrownBy(() -> new Inspection("D-77", MORNING, "0142", false, List.of()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void anUnsignedInspectionIsRefused() {
        assertThatThrownBy(() -> new Inspection("D-77", MORNING, "  ", false, InspectionItem.safetyItems()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void anInspectionWithNoDeviceIsRefused() {
        assertThatThrownBy(() -> new Inspection("  ", MORNING, "0142", false, InspectionItem.safetyItems()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theSameItemTwiceOnOneSheetIsRefused() {
        assertThatThrownBy(() -> new Inspection(
                        "D-77", MORNING, "0142", false, List.of(InspectionItem.WHEELS, InspectionItem.WHEELS)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aDefectOnAnItemNobodyCheckedIsRefused() {
        assertThatThrownBy(() ->
                        new Inspection("D-77", MORNING, "0142", false, List.of(InspectionItem.WHEELS), List.of(CABLE)))
                .isInstanceOfSatisfying(
                        ValidationError.class,
                        caught -> assertThat(caught.context().get("item")).isEqualTo("drive cable"));
    }

    @Test
    void defectsAreKeptApartByWhatTheyDo() {
        Inspection check = new Inspection("D-77", MORNING, "0142", false, EVERY_ITEM, List.of(CABLE, WHEEL));
        assertThat(check.blockingDefects()).containsExactly(CABLE);
        assertThat(check.notedDefects()).containsExactly(WHEEL);
        assertThat(check.passed()).isFalse();
    }

    @Test
    void aCheckWithOnlyAWheelOffStillPasses() {
        Inspection check = new Inspection("D-77", MORNING, "0142", false, EVERY_ITEM, List.of(WHEEL));
        assertThat(check.passed()).isTrue();
    }

    @Test
    void anInspectionPrintsTheWayTheSheetReads() {
        assertThat(quarterly("D-77", STRIPPED, "0303").describe()).endsWith("quarterly passed by 0303");
        Inspection failed = preUse("D-77", MORNING, "0142", List.of(CABLE));
        assertThat(failed.describe()).contains("pre use failed");
    }

    @Test
    void aDeviceWithNothingOnRecordDoesNotGoOut() {
        MaintenanceRegister register = new MaintenanceRegister();
        List<String> reasons = register.reasonsNotToUse("D-77", MORNING);
        assertThat(reasons).contains("no quarterly inspection on record");
        assertThat(reasons).contains("no pre use check that morning");
        assertThat(register.mayGoOut("D-77", MORNING)).isFalse();
    }

    @Test
    void aDeviceWithBothChecksGoesOut() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", MORNING, "0142"));
        assertThat(register.mayGoOut("D-77", MORNING)).isTrue();
    }

    @Test
    void aQuarterlyThatRanOutStopsTheDevice() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", MORNING, "0142"));
        LocalDate late = STRIPPED.plusDays(Inspection.QUARTERLY_DAYS);
        register.record(preUse("D-77", late, "0142"));
        assertThat(register.mayGoOut("D-77", late)).isFalse();
        assertThat(register.reasonsNotToUse("D-77", late).get(0)).contains("ran out");
    }

    @Test
    void theLastDayOfTheQuarterIsStillCovered() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        LocalDate last = STRIPPED.plusDays(Inspection.QUARTERLY_DAYS - 1);
        register.record(preUse("D-77", last, "0142"));
        assertThat(register.mayGoOut("D-77", last)).isTrue();
    }

    @Test
    void yesterdaysPreUseCheckIsNotTodays() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", MORNING.minusDays(1), "0142"));
        assertThat(register.reasonsNotToUse("D-77", MORNING)).contains("no pre use check that morning");
    }

    @Test
    void aBrokenDriveCableThatMorningStopsIt() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", MORNING, "0142", List.of(CABLE)));
        assertThat(register.reasonsNotToUse("D-77", MORNING).get(0)).contains("drive cable");
    }

    @Test
    void aQuarterlyThatFoundSomethingBlockingStopsItUntilTheNextOne() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303", List.of(CABLE)));
        register.record(preUse("D-77", MORNING, "0142"));
        assertThat(register.mayGoOut("D-77", MORNING)).isFalse();
    }

    @Test
    void aLaterCleanQuarterlyClearsIt() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303", List.of(CABLE)));
        register.record(quarterly("D-77", MORNING.minusDays(1), "0303"));
        register.record(preUse("D-77", MORNING, "0142"));
        assertThat(register.mayGoOut("D-77", MORNING)).isTrue();
    }

    @Test
    void anotherDeviceIsNotCoveredByThisOnesPaperwork() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", MORNING, "0142"));
        assertThat(register.mayGoOut("D-78", MORNING)).isFalse();
    }

    @Test
    void theFleetCanBeAskedWhichDevicesAreOverdue() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        assertThat(register.overdueOn(List.of("D-77", "D-78"), MORNING)).containsExactly("D-78");
        LocalDate late = STRIPPED.plusDays(Inspection.QUARTERLY_DAYS + 1);
        assertThat(register.overdueOn(List.of("D-77"), late)).containsExactly("D-77");
    }

    @Test
    void theNextQuarterlyDateReadsOffTheLastOne() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        assertThat(register.quarterlyDueBy("D-77")).hasValue(STRIPPED.plusDays(Inspection.QUARTERLY_DAYS));
        assertThat(register.quarterlyDueBy("D-78")).isEmpty();
    }

    @Test
    void theHistoryOfOneDeviceReadsInDateOrder() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(preUse("D-77", MORNING, "0142"));
        register.record(quarterly("D-77", STRIPPED, "0303"));
        List<LocalDate> days =
                register.forDevice("D-77").stream().map(Inspection::performedOn).toList();
        assertThat(days).isSorted();
    }

    @Test
    void aRecordFallsOutOfRetentionAfterThreeYears() {
        Inspection strip = quarterly("D-77", STRIPPED, "0303");
        assertThat(strip.destroyAfter()).isEqualTo(STRIPPED.plusDays(3 * 365));
    }

    @Test
    void aQuarterlyDoesNotCoverTheDayBeforeItWasDone() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        LocalDate dayBefore = STRIPPED.minusDays(1);
        register.record(preUse("D-77", dayBefore, "0142"));
        assertThat(register.mayGoOut("D-77", dayBefore)).isFalse();
        assertThat(register.reasonsNotToUse("D-77", dayBefore)).contains("no quarterly inspection on record");
    }

    @Test
    void theDayItWasDoneIsCovered() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        register.record(preUse("D-77", STRIPPED, "0142"));
        assertThat(register.mayGoOut("D-77", STRIPPED)).isTrue();
    }

    @Test
    void theLatestQuarterlyIsTheLatestAsOfTheDayAskedAbout() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        LocalDate later = STRIPPED.plusDays(40);
        register.record(quarterly("D-77", later, "0303"));
        assertThat(register.latestQuarterly("D-77", STRIPPED.plusDays(1))
                        .orElseThrow()
                        .performedOn())
                .isEqualTo(STRIPPED);
        assertThat(register.latestQuarterly("D-77", later).orElseThrow().performedOn())
                .isEqualTo(later);
        assertThat(register.latestQuarterly("D-77").orElseThrow().performedOn()).isEqualTo(later);
    }

    @Test
    void theNextDueDateIsAnsweredAsOfTheDayAskedAbout() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        assertThat(register.quarterlyDueBy("D-77", STRIPPED.minusDays(1))).isEmpty();
    }

    @Test
    void aFleetListingDoesNotCreditAFutureQuarterly() {
        MaintenanceRegister register = new MaintenanceRegister();
        register.record(quarterly("D-77", STRIPPED, "0303"));
        assertThat(register.overdueOn(List.of("D-77"), STRIPPED.minusDays(1))).containsExactly("D-77");
    }
}
