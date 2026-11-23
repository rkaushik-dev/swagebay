package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw52.alignedAllowance;
import static io.github.rkaushikdev.swagebay.evaluation.AsmeUw52.slagLimit;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AsmeUw52Test {

    private static final Length WALL = mm(15);

    private static IndicationSet weld(long length, Indication... indications) {
        return new IndicationSet(mm(length), List.of(indications));
    }

    private static Indication slag(long at, long length) {
        return new Indication(Kind.ESI, mm(at), mm(length), mm(1));
    }

    @Test
    void slagIsAllowedTwoThirdsOfTheWallUpToThreeQuarters() {
        assertThat(slagLimit(mm(15))).isEqualTo(mm(10));
        assertThat(slagLimit(mm(30))).isEqualTo(inch("0.75"));
        assertThatThrownBy(() -> slagLimit(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void slagUnderAQuarterInchPassesWhateverTheWall() {
        assertThat(AsmeUw52.evaluate(weld(1000, slag(100, 6)), mm(6))).isEmpty();
        List<Finding> findings = AsmeUw52.evaluate(weld(1000, slag(100, 7)), mm(6));
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.clause()).isEqualTo("UW-52(c2)");
            assertThat(finding.reason()).isEqualTo("7.0 mm is over the 4.0 mm allowed at 6.0 mm wall");
        });
    }

    @Test
    void lackOfFusionHasNoAllowanceAtAll() {
        Indication fusion = new Indication(Kind.IF, mm(100), mm(2));
        List<Finding> findings = AsmeUw52.evaluate(weld(1000, fusion), WALL);
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.clause()).isEqualTo("UW-52(c1)");
            assertThat(finding.reason())
                    .isEqualTo("incomplete fusion is not acceptable at any length on a spot examined weld");
        });
    }

    @Test
    void porosityIsNotAFactor() {
        Indication pore = new Indication(Kind.P, mm(100), Length.ZERO, mm(6));
        assertThat(AsmeUw52.evaluate(weld(1000, pore), WALL)).isEmpty();
        assertThat(AsmeUw52.judges(Kind.P)).isFalse();
        assertThat(AsmeUw52.judges(Kind.CP)).isFalse();
        assertThat(AsmeUw52.judges(Kind.HB)).isTrue();
        assertThat(AsmeUw52.judges(Kind.C)).isTrue();
        assertThat(AsmeUw52.judges(Kind.U)).isFalse();
    }

    @Test
    void slagInLineMayTotalOneWallInSixWalls() {
        IndicationSet under = weld(1000, slag(0, 7), slag(25, 7));
        assertThat(AsmeUw52.evaluate(under, WALL)).isEmpty();
        IndicationSet over = weld(1000, slag(0, 8), slag(30, 8));
        List<Finding> findings = AsmeUw52.evaluate(over, WALL);
        assertThat(findings).singleElement().satisfies(finding -> assertThat(finding.reason())
                .isEqualTo("2 inclusions in line total 16.0 mm in 90.0 mm, over the 15.0 mm allowed"));
    }

    @Test
    void threeLengthsOfSoundWeldKeepTwoInclusionsApart() {
        assertThat(AsmeUw52.evaluate(weld(1000, slag(0, 8), slag(32, 8)), WALL)).isEmpty();
        assertThat(AsmeUw52.evaluate(weld(1000, slag(0, 8), slag(31, 8)), WALL)).hasSize(1);
    }

    @Test
    void aWeldShorterThanSixWallsGetsItsShareOfTheAllowance() {
        assertThat(alignedAllowance(WALL, mm(1000))).isEqualTo(WALL);
        assertThat(alignedAllowance(WALL, mm(45))).isEqualTo(mm("7.5"));
        assertThat(AsmeUw52.evaluate(weld(45, slag(0, 4), slag(15, 4)), WALL)).hasSize(1);
        assertThatThrownBy(() -> alignedAllowance(Length.ZERO, mm(10))).isInstanceOf(ValidationError.class);
    }

    @Test
    void groupingOnTheSpotRuleBreaksOnAnExactGapButUw51DoesNot() {
        List<Indication> pair = List.of(slag(0, 8), slag(32, 8));
        assertThat(AsmeUw51.groupAligned(pair, 3, true)).hasSize(2);
        assertThat(AsmeUw51.groupAligned(pair, 3, false)).hasSize(1);
        assertThat(AsmeUw51.groupAligned(List.of(), 3, true)).isEmpty();
    }

    @Test
    void theSpotCodeIsJudgedThroughTheSameDoor() {
        IndicationSet found = weld(1000, new Indication(Kind.IF, mm(100), mm(3)));
        Verdict spot = Acceptance.judge(Code.ASME_VIII_SPOT, found, WALL);
        assertThat(spot.disposition()).isSameAs(Disposition.REPAIR);
        assertThat(spot.clauses()).containsExactly("UW-52(c1)");
        assertThat(spot.describe()).startsWith("ASME Section VIII Division 1 spot: UW-52(c1) IF: ");
        assertThat(Acceptance.judge(Code.ASME_VIII, found, WALL).accepted()).isTrue();
    }

    @Test
    void everyCodeIsAskedForItsDisposition() {
        IndicationSet found = weld(1000, slag(100, 40));
        Map<Code, Disposition> all = Acceptance.dispositions(found, WALL);
        assertThat(all.keySet()).containsExactly(Code.values());
        assertThat(all)
                .containsEntry(Code.API_1104, Disposition.ACCEPT)
                .containsEntry(Code.ASME_VIII, Disposition.ACCEPT)
                .containsEntry(Code.ASME_VIII_SPOT, Disposition.REPAIR);
        assertThat(Acceptance.codesDisagree(found, WALL)).isTrue();
    }

    @Test
    void aCrackOnASpotIsRejected() {
        IndicationSet found = weld(1000, new Indication(Kind.C, mm(100), mm(2)));
        assertThat(Acceptance.judge(Code.ASME_VIII_SPOT, found, WALL).disposition())
                .isSameAs(Disposition.REJECT);
    }

    @Test
    void theCodesNameTheParagraphTheirClausesSitUnder() {
        assertThat(Code.ASME_VIII_SPOT.paragraph()).isEqualTo("UW-52");
        assertThat(Code.ASME_VIII.paragraph()).isEqualTo("UW-51");
        assertThat(Code.API_1104.paragraph()).isEqualTo("9.3");
        assertThat(Code.ASME_VIII_SPOT.judgesByKind()).isFalse();
        assertThat(Code.ASME_VIII_SPOT.windowMovesWithTheWall()).isTrue();
    }
}
