package io.github.rkaushikdev.swagebay.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DoseQuantityTest {

    private static Assignment adult() {
        return new Assignment("0142");
    }

    @Test
    void eachQuantityHasItsOwnAdultLimit() {
        assertThat(WorkerClass.ADULT.limit(DoseQuantity.DEEP)).isEqualTo(Dose.fromMsv(50));
        assertThat(WorkerClass.ADULT.limit(DoseQuantity.LENS)).isEqualTo(Dose.fromMsv(150));
        assertThat(WorkerClass.ADULT.limit(DoseQuantity.SHALLOW)).isEqualTo(Dose.fromMsv(500));
        assertThat(WorkerClass.ADULT.limit()).isEqualTo(WorkerClass.ADULT.limit(DoseQuantity.DEEP));
    }

    @Test
    void aMinorIsHeldToATenthOfEveryLimit() {
        assertThat(WorkerClass.MINOR.limit(DoseQuantity.DEEP)).isEqualTo(Dose.fromMsv(5));
        assertThat(WorkerClass.MINOR.limit(DoseQuantity.LENS)).isEqualTo(Dose.fromMsv(15));
        assertThat(WorkerClass.MINOR.limit(DoseQuantity.SHALLOW)).isEqualTo(Dose.fromMsv(50));
    }

    @Test
    void aDeclaredPregnantWorkerKeepsTheAdultLensAndSkinLimits() {
        assertThat(WorkerClass.DECLARED_PREGNANT.limit(DoseQuantity.DEEP)).isEqualTo(Dose.fromMsv(5));
        assertThat(WorkerClass.DECLARED_PREGNANT.limit(DoseQuantity.LENS)).isEqualTo(Dose.fromMsv(150));
        assertThat(WorkerClass.DECLARED_PREGNANT.limit(DoseQuantity.SHALLOW)).isEqualTo(Dose.fromMsv(500));
    }

    @Test
    void aHandDoseShowsUpWhereTheDeepReadingDoesNot() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0142", Dose.fromMsv(1));
        ledger.record("0142", DoseQuantity.SHALLOW, Dose.fromMsv(160));
        assertThat(ledger.takenBy("0142")).isEqualTo(Dose.fromMsv(1));
        assertThat(ledger.takenBy("0142", DoseQuantity.SHALLOW)).isEqualTo(Dose.fromMsv(160));
        assertThat(ledger.takenBy("0142", DoseQuantity.LENS)).isEqualTo(Dose.ZERO);
        assertThat(ledger.governing(adult())).isSameAs(DoseQuantity.SHALLOW);
        assertThat(ledger.fractionFor(adult(), DoseQuantity.SHALLOW)).isEqualByComparingTo("0.32");
        assertThat(ledger.investigationLevel(adult())).isEqualTo(2);
        assertThat(ledger.fractionFor(adult())).isEqualByComparingTo("0.02");
    }

    @Test
    void anyQuantityOverItsLimitIsOverTheLimit() {
        DoseLedger ledger = new DoseLedger(2024);
        ledger.record("0142", DoseQuantity.LENS, Dose.fromMsv(150));
        assertThat(ledger.overLimit(adult())).isFalse();
        ledger.record("0142", DoseQuantity.LENS, new Dose(1));
        assertThat(ledger.overLimit(adult())).isTrue();
        assertThat(ledger.remaining(adult())).isEqualTo(Dose.fromMsv(50));
    }

    @Test
    void theDeepDoseGovernsOnATie() {
        DoseLedger ledger = new DoseLedger(2024);
        assertThat(ledger.governing(adult())).isSameAs(DoseQuantity.DEEP);
        ledger.record("0142", Dose.fromMsv(5));
        ledger.record("0142", DoseQuantity.LENS, Dose.fromMsv(15));
        assertThat(ledger.governing(adult())).isSameAs(DoseQuantity.DEEP);
        assertThat(ledger.investigationLevel(adult())).isEqualTo(1);
    }

    @Test
    void theTotalsReportedAreTheDeepOnes() {
        DoseLedger ledger = new DoseLedger(2024, Map.of("0201", Dose.fromMsv(2)));
        ledger.record("0201", DoseQuantity.SHALLOW, Dose.fromMsv(30));
        assertThat(ledger.doses()).containsExactly(Map.entry("0201", Dose.fromMsv(2)));
        assertThat(ledger.badges()).containsExactly("0201");
        assertThatThrownBy(() -> ledger.record(" ", DoseQuantity.LENS, Dose.fromMsv(1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theLevelsAreReadOffTheShareOfALimit() {
        assertThat(Dosimetry.investigationLevel(new BigDecimal("0.0999"))).isZero();
        assertThat(Dosimetry.investigationLevel(new BigDecimal("0.10"))).isEqualTo(1);
        assertThat(Dosimetry.investigationLevel(new BigDecimal("0.30"))).isEqualTo(2);
        assertThat(List.of(DoseQuantity.values()))
                .extracting(DoseQuantity::value)
                .containsExactly("deep dose", "lens of the eye", "skin and extremities");
        assertThat(DoseQuantity.LENS).hasToString("lens of the eye");
    }
}
