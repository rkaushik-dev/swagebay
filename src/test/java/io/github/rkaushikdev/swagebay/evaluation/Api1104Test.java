package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Api1104.ACCUMULATION_FRACTION;
import static io.github.rkaushikdev.swagebay.evaluation.Api1104.UNDERCUT_ALWAYS_ACCEPTABLE;
import static io.github.rkaushikdev.swagebay.evaluation.Api1104.WINDOW;
import static io.github.rkaushikdev.swagebay.evaluation.Api1104.allCriteria;
import static io.github.rkaushikdev.swagebay.evaluation.Api1104.criterion;
import static io.github.rkaushikdev.swagebay.evaluation.Api1104.undercutDepthLimit;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Api1104Test {

    @Test
    void theWindowEveryAggregateIsWrittenOn() {
        assertThat(inch(12)).isEqualTo(WINDOW);
        assertThat(ACCUMULATION_FRACTION).isEqualByComparingTo("0.08");
    }

    @Test
    void inadequatePenetrationIsCappedAtAnInchThreeWays() {
        Criterion row = criterion(Kind.IP);
        assertThat(row.clause()).isEqualTo("9.3.1");
        assertThat(row.maxLength()).contains(inch(1));
        assertThat(row.maxAggregate()).contains(inch(1));
        assertThat(row.shortWeldFraction())
                .hasValueSatisfying(fraction -> assertThat(fraction).isEqualByComparingTo("0.08"));
    }

    @Test
    void penetrationWithHighLowIsAllowedMore() {
        Criterion plain = criterion(Kind.IP);
        Criterion withHighLow = criterion(Kind.IPD);
        assertThat(withHighLow.maxLength().orElseThrow())
                .isGreaterThan(plain.maxLength().orElseThrow());
        assertThat(withHighLow.maxAggregate().orElseThrow())
                .isGreaterThan(plain.maxAggregate().orElseThrow());
    }

    @Test
    void elongatedSlagIsCappedOnWidthAsWellAsLength() {
        Criterion row = criterion(Kind.ESI);
        assertThat(row.maxLength()).contains(inch(2));
        assertThat(row.maxWidth()).contains(mm("1.6"));
        assertThat(row.maxAggregate()).contains(inch(2));
    }

    @Test
    void isolatedSlagIsCappedOnWidthAndOnCount() {
        Criterion row = criterion(Kind.ISI);
        assertThat(row.maxWidth()).contains(mm(3));
        assertThat(row.maxCount()).contains(4);
        assertThat(row.maxLength()).isEmpty();
    }

    @Test
    void aCrackHasTheSmallestAllowanceInTheWholeTable() {
        Criterion row = criterion(Kind.C);
        assertThat(row.maxLength()).contains(mm(4));
        assertThat(row.note()).contains("crater");
        assertThat(Kind.C.isAlwaysRejectable()).isTrue();
    }

    @Test
    void internalConcavityHasNoLengthLimitAtAll() {
        Criterion row = criterion(Kind.IC);
        assertThat(row.maxLength()).isEmpty();
        assertThat(row.maxAggregate()).isEmpty();
        assertThat(row.note()).contains("any length");
    }

    @Test
    void burnThroughBindsOnTheFractionOnThinWall() {
        Criterion row = criterion(Kind.BT);
        assertThat(row.individualLengthLimit(mm(10))).contains(mm(5));
        assertThat(row.individualLengthLimit(mm(6))).contains(mm(3));
    }

    @Test
    void burnThroughBindsOnTheFixedNumberOnHeavyWall() {
        Criterion row = criterion(Kind.BT);
        assertThat(row.individualLengthLimit(mm(40))).contains(inch("0.5"));
        assertThat(row.individualLengthLimit(mm(100))).contains(inch("0.5"));
    }

    @Test
    void theCrossoverIsWhereTheTwoNumbersMeet() {
        Criterion row = criterion(Kind.BT);
        Length crossover = inch("0.5").times(2);
        assertThat(row.individualLengthLimit(crossover)).contains(inch("0.5"));
        assertThat(row.individualLengthLimit(new Length(crossover.um() - 1000)).orElseThrow())
                .isLessThan(inch("0.5"));
    }

    @Test
    void aPoreIsCappedAtThreeMillimetresOrAQuarterOfTheWall() {
        Criterion row = criterion(Kind.P);
        assertThat(row.maxWidth()).contains(mm(3));
        assertThat(row.individualLengthLimit(mm(8))).contains(mm(2));
        assertThat(row.individualLengthLimit(mm(40))).isPresent();
    }

    @Test
    void aRowWithNoFractionReturnsItsFixedLimitUnchanged() {
        Criterion row = criterion(Kind.IP);
        assertThat(row.individualLengthLimit(mm(6))).contains(inch(1));
        assertThat(row.individualLengthLimit(mm(60))).contains(inch(1));
    }

    @Test
    void aWallOfNothingIsRefusedBeforeAnyLookup() {
        assertThatThrownBy(() -> criterion(Kind.BT).individualLengthLimit(Length.ZERO))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> undercutDepthLimit(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void undercutDepthBindsOnTheFractionOnThinWall() {
        assertThat(undercutDepthLimit(mm(4))).isEqualTo(mm("0.5"));
        assertThat(undercutDepthLimit(mm(6))).isEqualTo(mm("0.75"));
    }

    @Test
    void undercutDepthBindsOnTheFixedNumberOnHeavyWall() {
        assertThat(undercutDepthLimit(mm(10))).isEqualTo(mm("0.8"));
        assertThat(undercutDepthLimit(mm(30))).isEqualTo(mm("0.8"));
        assertThat(UNDERCUT_ALWAYS_ACCEPTABLE).isLessThan(mm("0.8"));
    }

    @Test
    void everyKindInTheTaxonomyHasARow() {
        Set<Kind> covered = allCriteria().stream().map(Criterion::kind).collect(Collectors.toSet());
        assertThat(covered).isEqualTo(EnumSet.allOf(Kind.class));
    }

    @Test
    void everyRowNamesTheClauseItCameFrom() {
        for (Criterion row : allCriteria()) {
            assertThat(row.clause()).startsWith("9.3.");
        }
    }

    /**
     * Every kind has a row in the published table, so the refusal is shown on a table that lacks
     * one: the lookup reports the missing kind rather than falling back to some default row.
     */
    @Test
    void aKindWithNoRowIsRefusedRatherThanDefaulted() {
        List<Criterion> withoutCracks =
                allCriteria().stream().filter(row -> row.kind() != Kind.C).toList();
        assertThatThrownBy(() -> Api1104.criterion(Kind.C, withoutCracks))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.context())
                        .containsEntry("kind", "C"));
    }
}
