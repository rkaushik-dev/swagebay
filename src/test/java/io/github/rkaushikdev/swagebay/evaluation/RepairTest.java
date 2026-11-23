package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Excavation.REPAIR_MARGIN;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RepairTest {

    private static final LocalDate WHEN = LocalDate.of(2024, 7, 8);

    private static Repair repair() {
        return repair("GW-104R1", false);
    }

    private static Repair repair(String weld) {
        return repair(weld, false);
    }

    private static Repair repair(String weld, boolean approved) {
        return new Repair(WeldId.parse(weld), new Excavation(mm(200), mm(260)), "w-19", WHEN, approved);
    }

    private static ReShoot reShoot(long from, long to) {
        return new ReShoot(WeldId.parse("GW-104R1"), mm(from), mm(to));
    }

    @Test
    void theReShootHasToReachPastTheExcavation() {
        Excavation.Stretch required = new Excavation(mm(200), mm(260)).coverageRequired();
        assertThat(required.start()).isEqualTo(mm(175));
        assertThat(required.end()).isEqualTo(mm(285));
        assertThat(REPAIR_MARGIN).isEqualTo(mm(25));
    }

    @Test
    void theMarginIsClippedAtTheStartOfTheWeld() {
        Excavation cut = new Excavation(mm(10), mm(60));
        assertThat(cut.coverageRequired().start()).isEqualTo(Length.ZERO);
    }

    @Test
    void aReShootThatReachesCoversTheRepair() {
        assertThat(reShoot(150, 300).covers(repair())).isTrue();
    }

    @Test
    void aReShootThatCoversOnlyTheExcavationDoesNot() {
        ReShoot tight = reShoot(200, 260);
        assertThat(tight.covers(repair())).isFalse();
        ReShoot.Shortfall shortfall = tight.shortfall(repair());
        assertThat(shortfall.before()).isEqualTo(mm(25));
        assertThat(shortfall.after()).isEqualTo(mm(25));
    }

    @Test
    void aReShootShortAtOneEndOnlyReportsOneShortfall() {
        ReShoot.Shortfall shortfall = reShoot(100, 270).shortfall(repair());
        assertThat(shortfall.before()).isEqualTo(Length.ZERO);
        assertThat(shortfall.after()).isEqualTo(mm(15));
    }

    @Test
    void aReShootThatReachesReportsNoShortfall() {
        assertThat(reShoot(100, 400).shortfall(repair())).isEqualTo(new ReShoot.Shortfall(Length.ZERO, Length.ZERO));
    }

    @Test
    void anExcavationWithNoLengthIsRefused() {
        assertThatThrownBy(() -> new Excavation(mm(200), mm(200))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Excavation(mm(200), mm(100))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Excavation(new Length(-1), mm(100))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aReShootWithNoLengthIsRefused() {
        assertThatThrownBy(() -> reShoot(100, 100)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aRepairBelongsToARepairRoundNotTheWeldAsFirstMade() {
        assertThatThrownBy(() -> repair("GW-104"))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("weld", "GW-104"));
    }

    @Test
    void aRepairIsSignedByTheWelder() {
        assertThatThrownBy(() -> new Repair(WeldId.parse("GW-104R1"), new Excavation(mm(200), mm(260)), "  ", WHEN))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theFirstRepairNeedsNoApproval() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        assertThat(register.roundsFor(WeldId.parse("GW-104"))).containsExactly(1);
    }

    @Test
    void aSecondRepairOfTheSameWeldNeedsTheClient() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        assertThatThrownBy(() -> register.record(repair("GW-104R2")))
                .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(caught.context())
                        .containsEntry("round", 2));
        register.record(repair("GW-104R2", true));
        assertThat(register.roundsFor(WeldId.parse("GW-104"))).containsExactly(1, 2);
    }

    @Test
    void theSameRoundCannotBeRecordedTwice() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        assertThatThrownBy(() -> register.record(repair())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aRepairIsARoundOfTheWeldAndNotANewOne() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        register.record(repair("GW-104R2", true));
        assertThat(register.repairedWelds()).containsExactly(WeldId.parse("GW-104"));
        assertThat(register.repairs()).hasSize(2);
    }

    @Test
    void theRateCountsWeldsAndNotRepairs() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        register.record(repair("GW-104R2", true));
        register.record(repair("GW-110R1"));
        assertThat(register.rateOver(100)).isEqualByComparingTo("0.02");
    }

    @Test
    void aRateNeedsADenominator() {
        RepairRegister register = new RepairRegister();
        assertThatThrownBy(() -> register.rateOver(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theWeldsThatFailedTwiceAreReportedOnTheirOwn() {
        RepairRegister register = new RepairRegister();
        register.record(repair());
        register.record(repair("GW-104R2", true));
        register.record(repair("GW-110R1"));
        assertThat(register.repeatOffenders()).extracting(WeldId::toString).containsExactly("GW-104");
    }

    @Test
    void aRegisterWithNothingInItReportsNothing() {
        RepairRegister register = new RepairRegister();
        assertThat(register.repairedWelds()).isEmpty();
        assertThat(register.repeatOffenders()).isEmpty();
        assertThat(register.rateOver(50)).isEqualByComparingTo("0");
    }

    private static Repair cut(String weld, long from, long to, boolean approved) {
        return new Repair(WeldId.parse(weld), new Excavation(mm(from), mm(to)), "w-19", WHEN, approved);
    }

    @Test
    void theLengthRateCountsWeldCutOutOverWeldExamined() {
        RepairRegister register = new RepairRegister();
        register.record(cut("GW-104R1", 100, 160, false));
        register.record(cut("GW-110R1", 0, 40, false));
        assertThat(register.excavatedLength()).isEqualTo(mm(100));
        assertThat(register.lengthRateOver(Length.fromM(10))).isEqualByComparingTo("0.01");
    }

    @Test
    void aSecondRoundAddsItsCutToTheLengthButNotToTheCount() {
        RepairRegister register = new RepairRegister();
        register.record(cut("GW-104R1", 100, 160, false));
        register.record(cut("GW-104R2", 90, 170, true));
        assertThat(register.excavatedLength()).isEqualTo(mm(140));
        assertThat(register.rateOver(10)).isEqualByComparingTo("0.1");
        assertThat(register.lengthRateOver(mm(1400))).isEqualByComparingTo("0.1");
    }

    @Test
    void aLengthRateNeedsALengthExamined() {
        RepairRegister register = new RepairRegister();
        assertThat(register.excavatedLength()).isEqualTo(Length.ZERO);
        assertThat(register.lengthRateOver(mm(500))).isEqualByComparingTo("0");
        assertThatThrownBy(() -> register.lengthRateOver(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theRoundsAreFoundFromAnyRoundOfTheWeld() {
        RepairRegister register = new RepairRegister();
        register.record(cut("GW-104R2", 0, 10, true));
        register.record(cut("GW-104R1", 0, 10, false));
        register.record(cut("GW-099R1", 0, 10, false));
        assertThat(register.roundsFor(WeldId.parse("GW-104R2"))).containsExactly(1, 2);
        assertThat(register.roundsFor(WeldId.parse("GW-104"))).containsExactly(1, 2);
        assertThat(register.repairedWelds()).extracting(WeldId::toString).containsExactly("GW-99", "GW-104");
        assertThat(register.repairs())
                .extracting(repair -> repair.weld().toString())
                .containsExactly("GW-104R2", "GW-104R1", "GW-99R1");
    }
}
