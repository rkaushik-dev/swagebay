package io.github.rkaushikdev.swagebay.welding;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeldingTest {

    private static final LocalDate QUALIFIED = LocalDate.of(2024, 6, 1);

    private static Qualification qualification(
            String welder, WeldingProcess process, Position position, Length coupon, LocalDate on) {
        return new Qualification(welder, process, position, coupon, on, "P1");
    }

    private static Qualification qualification() {
        return qualification("w-19", WeldingProcess.SMAW, Position.ALL, mm(12), QUALIFIED);
    }

    private static Qualification qualification(WeldingProcess process) {
        return qualification("w-19", process, Position.ALL, mm(12), QUALIFIED);
    }

    private static Qualification qualification(Length coupon) {
        return qualification("w-19", WeldingProcess.SMAW, Position.ALL, coupon, QUALIFIED);
    }

    /** One set holds every member of the other and something more. */
    private static void assertProperSubset(Set<Position> smaller, Set<Position> larger) {
        assertThat(larger).containsAll(smaller);
        assertThat(larger).hasSizeGreaterThan(smaller.size());
    }

    @Test
    void aFlatCouponBuysOnlyFlatWork() {
        assertThat(Position.FLAT.qualifies()).containsExactly(Position.FLAT);
    }

    @Test
    void anAllPositionCouponBuysEverything() {
        assertThat(Position.ALL.qualifies()).isEqualTo(EnumSet.allOf(Position.class));
    }

    @Test
    void thePositionsNest() {
        assertProperSubset(Position.FLAT.qualifies(), Position.HORIZONTAL.qualifies());
        assertProperSubset(Position.HORIZONTAL.qualifies(), Position.VERTICAL.qualifies());
    }

    @Test
    void aThinCouponQualifiesFromNothingUpToTwiceItself() {
        ThicknessRange range = Welding.thicknessRange(mm(8));
        assertThat(range.low()).isEqualTo(Length.ZERO);
        assertThat(range.high()).isEqualTo(mm(16));
    }

    @Test
    void aThickCouponCarriesAFloorAsWellAsACeiling() {
        ThicknessRange range = Welding.thicknessRange(mm(25));
        assertThat(range.low()).isEqualTo(mm(5));
        assertThat(range.high()).isEqualTo(mm(50));
    }

    @Test
    void theRangeIsNotSymmetricAboutTheCoupon() {
        ThicknessRange range = Welding.thicknessRange(mm(25));
        assertThat(range.high().minus(mm(25))).isGreaterThan(mm(25).minus(range.low()));
    }

    @Test
    void aCouponOfNothingIsRefused() {
        assertThatThrownBy(() -> Welding.thicknessRange(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> qualification(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aQualificationNeedsAWelder() {
        assertThatThrownBy(() -> qualification("  ", WeldingProcess.SMAW, Position.ALL, mm(12), QUALIFIED))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aQualificationCoversWhatItsRangesCover() {
        Qualification record = qualification("w-19", WeldingProcess.SMAW, Position.VERTICAL, mm(12), QUALIFIED);
        assertThat(record.covers(Position.HORIZONTAL, mm(10), "P1")).isTrue();
        assertThat(record.covers(Position.ALL, mm(10), "P1")).isFalse();
        assertThat(record.covers(Position.FLAT, mm(30), "P1")).isFalse();
        assertThat(record.covers(Position.FLAT, mm(10), "P8")).isFalse();
    }

    @Test
    void aQualificationPrintsItsRange() {
        String text = qualification(mm(12)).describe();
        assertThat(text).contains("w-19");
        assertThat(text).contains("5.0 mm to 24.0 mm");
        assertThat(text).contains("group P1");
    }

    @Test
    void aWelderMayWeldInsideTheirRangeWhileItIsCurrent() {
        Register register = new Register();
        register.add(qualification());
        assertThat(register.mayWeld("w-19", WeldingProcess.SMAW, Position.VERTICAL, mm(12), "P1", QUALIFIED))
                .isTrue();
    }

    @Test
    void aWelderMayNotWeldOutsideTheThicknessRange() {
        Register register = new Register();
        register.add(qualification(mm(8)));
        assertThat(register.mayWeld("w-19", WeldingProcess.SMAW, Position.FLAT, mm(20), "P1", QUALIFIED))
                .isFalse();
    }

    @Test
    void aWelderMayNotWeldAProcessTheyNeverQualifiedOn() {
        Register register = new Register();
        register.add(qualification(WeldingProcess.SMAW));
        assertThat(register.mayWeld("w-19", WeldingProcess.GTAW, Position.FLAT, mm(12), "P1", QUALIFIED))
                .isFalse();
    }

    @Test
    void aQualificationLapsesSilentlyAfterSixMonths() {
        Register register = new Register();
        register.add(qualification());
        assertThat(register.isCurrentOn("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(179)))
                .isTrue();
        assertThat(register.isCurrentOn("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(180)))
                .isFalse();
        assertThat(Welding.CONTINUITY_DAYS).isEqualTo(180);
    }

    @Test
    void usingTheProcessPushesTheLapseDateOut() {
        Register register = new Register();
        register.add(qualification());
        register.recordUse("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(100));
        assertThat(register.isCurrentOn("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(250)))
                .isTrue();
    }

    @Test
    void anEarlierUseDoesNotPullTheLapseDateBack() {
        Register register = new Register();
        register.add(qualification());
        register.recordUse("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(100));
        register.recordUse("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(20));
        assertThat(register.lapsesOn("w-19", WeldingProcess.SMAW)).hasValue(QUALIFIED.plusDays(280));
    }

    @Test
    void aUseBySomebodyNotQualifiedIsRefused() {
        Register register = new Register();
        assertThatThrownBy(() -> register.recordUse("w-99", WeldingProcess.SMAW, QUALIFIED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theSameQualificationTwiceIsRefused() {
        Register register = new Register();
        register.add(qualification());
        assertThatThrownBy(() -> register.add(qualification())).isInstanceOf(ConflictError.class);
        register.add(qualification("w-19", WeldingProcess.SMAW, Position.ALL, mm(12), QUALIFIED.plusDays(1)));
    }

    @Test
    void theLapsedListNamesTheWelderAndTheProcess() {
        Register register = new Register();
        register.add(qualification("w-19", WeldingProcess.SMAW, Position.ALL, mm(12), QUALIFIED));
        register.add(qualification("w-04", WeldingProcess.GTAW, Position.ALL, mm(12), QUALIFIED));
        register.recordUse("w-19", WeldingProcess.SMAW, QUALIFIED.plusDays(170));
        List<WelderProcess> lapsed = register.lapsedOn(QUALIFIED.plusDays(200));
        assertThat(lapsed).containsExactly(new WelderProcess("w-04", WeldingProcess.GTAW));
    }

    @Test
    void aWelderWithNothingOnTheRegisterHasNoLapseDate() {
        assertThat(new Register().lapsesOn("w-99", WeldingProcess.SMAW)).isEmpty();
    }

    @Test
    void everyQualificationAWelderHoldsComesBack() {
        Register register = new Register();
        register.add(qualification(WeldingProcess.SMAW));
        register.add(qualification(WeldingProcess.GTAW));
        assertThat(register.forWelder("w-19")).hasSize(2);
        assertThat(register.forWelder("w-99")).isEmpty();
    }
}
