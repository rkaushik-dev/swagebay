package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.LONG_ALLOWANCE;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.SHORT_ALLOWANCE;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.THICK_LIMIT;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.THIN_LIMIT;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.elongatedLimit;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.evaluate;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.fractionOfWall;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.groupAligned;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.slidingLimitIsContinuous;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.windowFor;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.AsmeUw51.Group;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsmeUw51Test {

    private static Indication lof(long at, long length) {
        return new Indication(Kind.IF, mm(at), mm(length));
    }

    private static List<Indication> aligned() {
        return List.of(lof(0, 4), lof(20, 4), lof(40, 4));
    }

    @Test
    void thinWallGetsTheFlatQuarterInch() {
        assertThat(elongatedLimit(mm(5))).isEqualTo(SHORT_ALLOWANCE);
        assertThat(elongatedLimit(THIN_LIMIT)).isEqualTo(inch("0.25"));
    }

    @Test
    void heavyWallGetsTheFlatThreeQuarters() {
        assertThat(elongatedLimit(mm(80))).isEqualTo(LONG_ALLOWANCE);
        assertThat(elongatedLimit(THICK_LIMIT)).isEqualTo(inch("0.75"));
    }

    @Test
    void theMiddleIsAThirdOfTheWall() {
        assertThat(elongatedLimit(mm(30))).isEqualTo(mm(10));
        assertThat(elongatedLimit(mm(45))).isEqualTo(mm(15));
    }

    @Test
    void theThreePartsJoinUpAtBothEnds() {
        assertThat(slidingLimitIsContinuous()).isTrue();
        assertThat(elongatedLimit(THIN_LIMIT)).isEqualTo(elongatedLimit(new Length(THIN_LIMIT.um() + 1)));
    }

    @Test
    void aWallOfNothingIsRefused() {
        assertThatThrownBy(() -> elongatedLimit(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> windowFor(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theWindowMovesWithTheWallRatherThanBeingTwelveInches() {
        assertThat(windowFor(mm(10))).isEqualTo(mm(120));
        assertThat(windowFor(mm(25))).isEqualTo(mm(300));
        assertThat(windowFor(mm(10))).isNotEqualTo(inch(12));
    }

    @Test
    void aCrackIsOutWhateverItsLength() {
        List<Uw51Finding> findings = evaluate(List.of(new Indication(Kind.C, mm(10), mm(2))), mm(20));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).rule()).isEqualTo("b1");
        assertThat(findings.get(0).reason()).contains("not acceptable at any length");
    }

    @Test
    void aShortLackOfFusionIsAccepted() {
        assertThat(evaluate(List.of(lof(10, 5)), mm(20))).isEmpty();
    }

    @Test
    void aLongLackOfFusionIsNot() {
        List<Uw51Finding> findings = evaluate(List.of(lof(10, 10)), mm(20));
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).rule()).isEqualTo("b2");
        assertThat(findings.get(0).reason()).contains("over the");
    }

    @Test
    void theSameIndicationPassesOnHeavierWall() {
        Indication found = lof(10, 10);
        assertThat(evaluate(List.of(found), mm(20))).isNotEmpty();
        assertThat(evaluate(List.of(found), mm(40))).isEmpty();
    }

    @Test
    void volumetricIndicationsAreNotJudgedByTheElongatedRule() {
        Indication slag = new Indication(Kind.ESI, mm(10), mm(40), mm(1));
        assertThat(evaluate(List.of(slag), mm(20))).isEmpty();
    }

    @Test
    void closeIndicationsAreGroupedTogether() {
        List<Group> groups = groupAligned(List.of(lof(0, 5), lof(20, 5)));
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(2);
        assertThat(groups.get(0).aggregate()).isEqualTo(mm(10));
    }

    @Test
    void aGapOverSixTimesTheLongestBreaksTheGroup() {
        assertThat(groupAligned(List.of(lof(0, 5), lof(100, 5)))).hasSize(2);
    }

    @Test
    void oneLongIndicationPullsItsNeighboursIn() {
        List<Group> tight = groupAligned(List.of(lof(0, 5), lof(100, 5)));
        List<Group> loose = groupAligned(List.of(lof(0, 30), lof(100, 5)));
        assertThat(tight).hasSize(2);
        assertThat(loose).hasSize(1);
    }

    @Test
    void theGroupReportsItsLongestAndItsSpan() {
        Group group = groupAligned(List.of(lof(0, 5), lof(20, 8))).get(0);
        assertThat(group.longest()).isEqualTo(mm(8));
        assertThat(group.span()).isEqualTo(mm(28));
    }

    @Test
    void anEmptySetMakesNoGroups() {
        assertThat(groupAligned(List.of())).isEmpty();
    }

    @Test
    void alignedIndicationsTotallingOverTheWallAreAFinding() {
        List<Uw51Finding> findings = evaluate(aligned(), mm(10));
        assertThat(findings).anySatisfy(finding -> assertThat(finding.rule()).isEqualTo("b3"));
        assertThat(findings).anySatisfy(finding -> assertThat(finding.reason()).contains("aligned indications total"));
    }

    @Test
    void theSameThreeOnHeavierWallAreAccepted() {
        assertThat(evaluate(aligned(), mm(20))).isEmpty();
    }

    @Test
    void aSingleIndicationIsNeverAnAlignedGroup() {
        List<Uw51Finding> findings = evaluate(List.of(lof(0, 4)), mm(10));
        assertThat(findings).noneSatisfy(finding -> assertThat(finding.rule()).isEqualTo("b3"));
    }

    @Test
    void theFractionOfTheWallIsWhatTheReportQuotes() {
        assertThat(fractionOfWall(lof(0, 5), mm(10))).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> fractionOfWall(lof(0, 5), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theFindingsNameTheSubclause() {
        List<Uw51Finding> findings = evaluate(List.of(lof(10, 10)), mm(20));
        assertThat(findings.get(0).describe()).startsWith("UW-51(b2): ");
    }
}
