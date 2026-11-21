package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.CONTRAST_STEP;
import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.DEFAULT_DENSITY;
import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.choose;
import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.contrastPenalty;
import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.lowestWorkable;
import static io.github.rkaushikdev.swagebay.exposure.Kilovoltage.tableBands;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Tubes.tube;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class KilovoltageTest {

    private static final BigDecimal DENSE = new BigDecimal("2.5");

    @Test
    void theTableGivesTheLowestThatWillPenetrate() {
        assertThat(lowestWorkable(mm(6))).isEqualTo(120);
        assertThat(lowestWorkable(mm(13))).isEqualTo(150);
        assertThat(lowestWorkable(mm(25))).isEqualTo(200);
    }

    @Test
    void theBandsAreClosedAtTheTop() {
        assertThat(lowestWorkable(mm(10))).isEqualTo(140);
        assertThat(lowestWorkable(mm("10.1"))).isEqualTo(150);
    }

    @Test
    void aThickerPartNeedsAHarderBeam() {
        List<Integer> values =
                Stream.of(3, 10, 25, 50, 80).map(t -> lowestWorkable(mm(t))).toList();
        assertThat(values).isSorted();
    }

    @Test
    void aThicknessPastTheTableIsRefused() {
        assertThatThrownBy(() -> lowestWorkable(mm(100)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(
                                caught.context().get("table_ends_at_mm"))
                        .isEqualTo("80"));
    }

    @Test
    void aThicknessOfNothingIsRefused() {
        assertThatThrownBy(() -> lowestWorkable(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aChoiceThatMatchesTheTableSaysSo() {
        KilovoltageChoice picked = choose(mm(13), tube("eresco42"));
        assertThat(picked.kv()).isEqualTo(150);
        assertThat(picked.isTableValue()).isTrue();
        assertThat(picked.reasons()).isEmpty();
        assertThat(picked.describe()).isEqualTo("150 kV");
    }

    @Test
    void aSetWithAFloorPushesTheChoiceUpAndSaysWhy() {
        KilovoltageChoice picked = choose(mm(3), tube("xrs3"));
        assertThat(picked.kv()).isEqualTo(270);
        assertThat(picked.fromTable()).isEqualTo(90);
        assertThat(picked.reasons().get(0)).contains("will not go below 270");
    }

    @Test
    void aHigherDensityCostsAStep() {
        KilovoltageChoice plain = choose(mm(13), tube("eresco42"), new BigDecimal("2.0"));
        KilovoltageChoice dense = choose(mm(13), tube("eresco42"), DENSE);
        assertThat(dense.kv()).isEqualTo(plain.kv() + CONTRAST_STEP);
        assertThat(dense.reasons().get(0)).contains("density");
    }

    @Test
    void aSetThatCannotReachTheThicknessIsRefused() {
        assertThatThrownBy(() -> choose(mm(65), tube("eresco42")))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("max_kv")).isEqualTo(200));
    }

    @Test
    void aSpecificationCapHoldsTheChoiceDown() {
        KilovoltageChoice capped = choose(mm(13), tube("eresco42"), DENSE, OptionalInt.of(150));
        assertThat(capped.kv()).isEqualTo(150);
        assertThat(capped.reasons().get(capped.reasons().size() - 1)).contains("caps it at 150");
    }

    @Test
    void aCapBelowWhatWillPenetrateIsRefused() {
        assertThatThrownBy(() -> choose(mm(25), tube("eresco42"), DEFAULT_DENSITY, OptionalInt.of(120)))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("needed")).isEqualTo(200));
    }

    @Test
    void aCapAboveTheChoiceChangesNothing() {
        KilovoltageChoice picked = choose(mm(13), tube("eresco42"), DEFAULT_DENSITY, OptionalInt.of(300));
        assertThat(picked.kv()).isEqualTo(150);
        assertThat(picked.isTableValue()).isTrue();
    }

    @Test
    void theStepsAboveTheTableAreCounted() {
        KilovoltageChoice plain = choose(mm(13), tube("eresco42"));
        KilovoltageChoice dense = choose(mm(13), tube("eresco42"), DENSE);
        assertThat(plain.stepsAboveTable()).isZero();
        assertThat(dense.stepsAboveTable()).isEqualTo(1);
    }

    @Test
    void theContrastPenaltyIsZeroAtTheTableValue() {
        assertThat(contrastPenalty(choose(mm(13), tube("eresco42")))).isEqualByComparingTo("0");
    }

    @Test
    void theContrastPenaltyGrowsWithTheSteps() {
        KilovoltageChoice dense = choose(mm(13), tube("eresco42"), DENSE);
        assertThat(contrastPenalty(dense)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void theChoicePrintsWhatItGaveUpAndWhy() {
        KilovoltageChoice dense = choose(mm(13), tube("eresco42"), DENSE);
        String text = dense.describe();
        assertThat(text).startsWith("170 kV, up from 150");
        assertThat(text).contains("density");
    }

    @Test
    void theTableRisesInBothColumns() {
        List<Kilovoltage.Band> bands = tableBands();
        assertThat(bands).extracting(Kilovoltage.Band::ceiling).isSorted();
        assertThat(bands).extracting(Kilovoltage.Band::kv).isSorted();
        assertThat(bands).hasSize(11);
    }
}
