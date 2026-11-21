package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Assessment.assess;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AssessmentTest {

    private static final Length WALL = mm(10);

    private static IndicationSet weld(Indication... indications) {
        return weld(1000, indications);
    }

    private static IndicationSet weld(long length, Indication... indications) {
        return new IndicationSet(mm(length), List.of(indications));
    }

    private static Indication slag(long at, long length) {
        return slag(at, length, 1);
    }

    private static Indication slag(long at, long length, long width) {
        return new Indication(Kind.ESI, mm(at), mm(length), mm(width));
    }

    private static Indication[] isolated(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Indication(Kind.ISI, mm(i * 40L), mm(2), mm(2)))
                .toArray(Indication[]::new);
    }

    private static Indication undercut(String depthFraction) {
        return new Indication(Kind.U, mm(100), mm(20)).withDepthFraction(new BigDecimal(depthFraction));
    }

    @Test
    void aCleanWeldIsAccepted() {
        Assessment verdict = assess(weld(), WALL);
        assertThat(verdict.accepted()).isTrue();
        assertThat(verdict.findings()).isEmpty();
        assertThat(verdict.describe()).isEqualTo("accept");
    }

    @Test
    void aShortInclusionInsideEveryLimitIsAccepted() {
        assertThat(assess(weld(slag(100, 20, 1)), WALL).accepted()).isTrue();
    }

    @Test
    void anInclusionOverTheLengthLimitIsARepair() {
        Assessment verdict = assess(weld(slag(100, 60)), WALL);
        assertThat(verdict.disposition()).isSameAs(Disposition.REPAIR);
        assertThat(verdict.clauses()).contains("9.3.8");
        assertThat(verdict.findings().get(0).reason()).contains("over the");
    }

    @Test
    void anInclusionOverTheWidthLimitFailsOnWidthAlone() {
        Assessment verdict = assess(weld(slag(100, 20, 3)), WALL);
        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.findings().get(0).reason()).contains("across");
    }

    @Test
    void theAggregateRuleCatchesWhatTheIndividualRuleLetsThrough() {
        Indication[] bunched =
                IntStream.range(0, 4).mapToObj(i -> slag(i * 30L, 20)).toArray(Indication[]::new);
        Assessment verdict = assess(weld(bunched), WALL);
        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.findings())
                .anySatisfy(finding -> assertThat(finding.reason()).contains("in 305 mm"));
    }

    @Test
    void theSameInclusionsSpreadOutAreAccepted() {
        Indication[] spread =
                IntStream.range(0, 4).mapToObj(i -> slag(i * 350L, 20)).toArray(Indication[]::new);
        assertThat(assess(weld(1500, spread), WALL).accepted()).isTrue();
    }

    @Test
    void aCrackIsRejectedRatherThanRepaired() {
        Assessment verdict = assess(weld(new Indication(Kind.C, mm(100), mm(10))), WALL);
        assertThat(verdict.disposition()).isSameAs(Disposition.REJECT);
        assertThat(verdict.clauses()).contains("9.3.10");
    }

    @Test
    void aShallowCraterCrackUnderFourMillimetresIsTheOneException() {
        assertThat(assess(weld(new Indication(Kind.C, mm(100), mm(3))), WALL).accepted())
                .isTrue();
    }

    @Test
    void aCrackRejectsTheWeldEvenAlongsideRepairableFindings() {
        Assessment verdict = assess(weld(new Indication(Kind.C, mm(100), mm(10)), slag(400, 60)), WALL);
        assertThat(verdict.disposition()).isSameAs(Disposition.REJECT);
        assertThat(verdict.findings()).hasSize(3);
        assertThat(verdict.findings()).extracting(Finding::kind).containsOnly(Kind.C, Kind.ESI);
    }

    @Test
    void internalConcavityOfAnyLengthIsAccepted() {
        assertThat(assess(weld(new Indication(Kind.IC, mm(100), mm(200))), WALL).accepted())
                .isTrue();
    }

    @Test
    void burnThroughIsJudgedAgainstTheWallItIsIn() {
        Indication big = new Indication(Kind.BT, mm(100), mm(8));
        assertThat(assess(weld(big), mm(10)).accepted()).isFalse();
        assertThat(assess(weld(big), mm(40)).accepted()).isTrue();
    }

    @Test
    void aPoreIsJudgedAcrossAndAgainstAQuarterOfTheWall() {
        Indication fat = new Indication(Kind.P, mm(100), Length.ZERO, mm(4));
        assertThat(assess(weld(fat), mm(10)).accepted()).isFalse();
        Indication small = new Indication(Kind.P, mm(100), Length.ZERO, mm(2));
        assertThat(assess(weld(small), mm(10)).accepted()).isTrue();
    }

    @Test
    void isolatedSlagIsCappedOnHowManyNotOnlyHowBig() {
        Assessment verdict = assess(weld(isolated(6)), WALL);
        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.findings())
                .anySatisfy(finding -> assertThat(finding.reason()).contains("in one window is over"));
    }

    @Test
    void fourIsolatedInclusionsAreAllowed() {
        assertThat(assess(weld(isolated(4)), WALL).accepted()).isTrue();
    }

    @Test
    void aShortWeldIsJudgedOnAPercentageInsteadOfTheWindow() {
        IndicationSet longWeld = weld(1000, new Indication(Kind.IP, mm(0), mm(20)));
        assertThat(assess(longWeld, WALL).accepted()).isTrue();
        IndicationSet shortWeld = weld(200, new Indication(Kind.IP, mm(0), mm(20)));
        Assessment verdict = assess(shortWeld, WALL);
        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.findings().get(0).reason()).contains("short weld");
    }

    @Test
    void theShortWeldRuleIsStricterNotMoreLenient() {
        Indication aggregate = new Indication(Kind.IF, mm(0), mm(15));
        assertThat(assess(weld(1000, aggregate), WALL).accepted()).isTrue();
        assertThat(assess(weld(150, aggregate), WALL).accepted()).isFalse();
    }

    @Test
    void undercutIsJudgedOnDepthAsWellAsLength() {
        Assessment verdict = assess(weld(undercut("0.2")), mm(10));
        assertThat(verdict.accepted()).isFalse();
        assertThat(verdict.findings().get(0).reason()).contains("deep is over");
    }

    @Test
    void shallowUndercutOfTheSameLengthIsAccepted() {
        assertThat(assess(weld(undercut("0.05")), mm(10)).accepted()).isTrue();
    }

    @Test
    void undercutDepthIsReadAgainstTheWallItSitsIn() {
        Indication same = undercut("0.1");
        assertThat(assess(weld(same), mm(4)).accepted()).isTrue();
        assertThat(assess(weld(same), mm(20)).accepted()).isFalse();
    }

    @Test
    void undercutWithNoDepthRecordedIsNotJudgedOnDepth() {
        assertThat(assess(weld(new Indication(Kind.U, mm(100), mm(20))), mm(10)).accepted())
                .isTrue();
    }

    @Test
    void aWallOfNothingIsRefusedBeforeAnythingIsAssessed() {
        assertThatThrownBy(() -> assess(weld(), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theFindingsNameTheClauseTheyCameFrom() {
        Assessment verdict = assess(weld(slag(100, 60)), WALL);
        assertThat(verdict.findings().get(0).describe()).startsWith("9.3.8 ESI: ");
        assertThat(inch(2)).isLessThan(mm(60));
    }
}
