package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DosimetryTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 5);
    private static final LocalDate DUE = LocalDate.of(2025, 1, 1);

    private static List<Dosimeter> fullKit(LocalDate due) {
        return List.of(
                new Dosimeter(DosimeterKind.BADGE, "B-4471"),
                new Dosimeter(DosimeterKind.DIRECT_READING, "D-118", due),
                new Dosimeter(DosimeterKind.ALARMING_RATEMETER, "A-77", due));
    }

    private static List<Dosimeter> fullKit() {
        return fullKit(DUE);
    }

    private static Assignment assigned(String badge, WorkerClass worker, List<Dosimeter> kit) {
        return new Assignment(badge, worker, kit);
    }

    private static Assignment assigned(String badge, WorkerClass worker) {
        return assigned(badge, worker, fullKit());
    }

    private static Assignment assigned(List<Dosimeter> kit) {
        return assigned("0142", WorkerClass.ADULT, kit);
    }

    private static Assignment assigned() {
        return assigned(fullKit());
    }

    @Test
    void aFullKitGetsSomebodyInsideTheBoundary() {
        assertThat(assigned().mayEnterBoundaryOn(TODAY)).isTrue();
        assertThat(assigned().missingOn(TODAY)).isEmpty();
    }

    @Test
    void everyMissingInstrumentIsNamed() {
        Assignment bare = new Assignment("0201");
        List<String> missing = bare.missingOn(TODAY);
        assertThat(missing).hasSize(3);
        assertThat(missing).contains("no alarming ratemeter");
        assertThat(bare.mayEnterBoundaryOn(TODAY)).isFalse();
    }

    @Test
    void theAlarmIsTheOneThatCanSaveAnybody() {
        Assignment withoutAlarm = assigned(fullKit().subList(0, 2));
        assertThat(withoutAlarm.mayEnterBoundaryOn(TODAY)).isFalse();
        assertThat(withoutAlarm.missingOn(TODAY)).containsExactly("no alarming ratemeter");
        assertThat(DosimeterKind.ALARMING_RATEMETER.warnsBeforeTheDose()).isTrue();
        assertThat(DosimeterKind.BADGE.warnsBeforeTheDose()).isFalse();
    }

    @Test
    void onlyTheBadgeSaysNothingOnTheDay() {
        assertThat(DosimeterKind.BADGE.readsOnTheDay()).isFalse();
        assertThat(DosimeterKind.DIRECT_READING.readsOnTheDay()).isTrue();
        assertThat(DosimeterKind.ALARMING_RATEMETER.readsOnTheDay()).isTrue();
    }

    @Test
    void anInstrumentOutOfCalibrationCountsAsMissing() {
        Assignment stale = assigned(fullKit(LocalDate.of(2024, 1, 1)));
        List<String> missing = stale.missingOn(TODAY);
        assertThat(missing).hasSize(2);
        assertThat(missing).allMatch(entry -> entry.contains("out of calibration"));
    }

    @Test
    void aBadgeNeedsNoCalibrationDateAndTheOthersDo() {
        new Dosimeter(DosimeterKind.BADGE, "B-1");
        assertThatThrownBy(() -> new Dosimeter(DosimeterKind.ALARMING_RATEMETER, "A-1"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Dosimeter(DosimeterKind.BADGE, "   ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void doseAccumulatesAcrossAYear() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0142", usv(500));
        ledger.record("0142", usv(300));
        assertThat(ledger.takenBy("0142")).isEqualTo(usv(800));
        assertThat(ledger.takenBy("0201")).isEqualTo(Dose.ZERO);
    }

    @Test
    void aDoseNeedsABadgeToGoAgainst() {
        DoseLedger ledger = new DoseLedger(2024);
        assertThatThrownBy(() -> ledger.record("  ", usv(500))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theInvestigationLevelsFireLongBeforeTheLimit() {
        DoseLedger ledger = new DoseLedger(2024);
        Assignment person = assigned();
        assertThat(ledger.investigationLevel(person)).isZero();
        ledger.record("0142", Dose.fromMsv(6));
        assertThat(ledger.investigationLevel(person)).isEqualTo(1);
        ledger.record("0142", Dose.fromMsv(10));
        assertThat(ledger.investigationLevel(person)).isEqualTo(2);
        assertThat(ledger.overLimit(person)).isFalse();
    }

    @Test
    void theAnnualLimitIsWhereTheLawPutsIt() {
        assertThat(Dosimetry.ANNUAL_LIMIT).isEqualTo(Dose.fromMsv(50));
        assertThat(WorkerClass.ADULT.limit()).isEqualTo(Dosimetry.ANNUAL_LIMIT);
    }

    @Test
    void aDeclaredPregnantWorkerIsHeldToATenthOfIt() {
        DoseLedger ledger = new DoseLedger(2024);
        Assignment expecting = assigned("0303", WorkerClass.DECLARED_PREGNANT);
        ledger.record("0303", Dose.fromMsv(4));
        assertThat(ledger.investigationLevel(expecting)).isEqualTo(2);
        assertThat(ledger.overLimit(expecting)).isFalse();
        ledger.record("0303", Dose.fromMsv(2));
        assertThat(ledger.overLimit(expecting)).isTrue();
    }

    @Test
    void theSameDoseReadsDifferentlyAgainstTheTwoLimits() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0142", Dose.fromMsv(4));
        ledger.record("0303", Dose.fromMsv(4));
        Assignment adult = assigned("0142", WorkerClass.ADULT);
        Assignment expecting = assigned("0303", WorkerClass.DECLARED_PREGNANT);
        assertThat(ledger.investigationLevel(adult)).isZero();
        assertThat(ledger.investigationLevel(expecting)).isEqualTo(2);
    }

    @Test
    void whatIsLeftOfTheYearIsReported() {
        DoseLedger ledger = new DoseLedger(2024);
        Assignment person = assigned();
        assertThat(ledger.remaining(person)).isEqualTo(Dosimetry.ANNUAL_LIMIT);
        ledger.record("0142", Dose.fromMsv(20));
        assertThat(ledger.remaining(person)).isEqualTo(Dose.fromMsv(30));
        ledger.record("0142", Dose.fromMsv(40));
        assertThat(ledger.remaining(person)).isEqualTo(Dose.ZERO);
    }

    @Test
    void theFractionTakenIsWhatTheMonthlyReportPrints() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0142", Dose.fromMsv(5));
        assertThat(ledger.fractionFor(assigned())).isEqualByComparingTo("0.1");
    }

    @Test
    void theLedgerListsWhoItHolds() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0201", usv(10));
        ledger.record("0142", usv(10));
        assertThat(ledger.badges()).containsExactly("0142", "0201");
        assertThat(WorkerClass.MINOR).hasToString("minor");
    }
}
