package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.RoundedIndications.isolatedLimit;
import static io.github.rkaushikdev.swagebay.evaluation.RoundedIndications.randomLimit;
import static io.github.rkaushikdev.swagebay.evaluation.RoundedIndications.relevantFrom;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundedIndicationsTest {

    private static final Length WALL = mm(10);

    private static Indication pore(long at, String diameter) {
        return new Indication(Kind.P, mm(at), Length.ZERO, mm(diameter));
    }

    @Test
    void theRelevanceThresholdStepsWithTheWall() {
        assertThat(relevantFrom(mm(3))).isEqualTo(mm("0.3"));
        assertThat(relevantFrom(mm(5))).isEqualTo(inch("0.015625"));
        assertThat(relevantFrom(inch("0.25"))).isEqualTo(inch("0.015625"));
        assertThat(relevantFrom(mm(10))).isEqualTo(inch("0.03125"));
        assertThat(relevantFrom(inch(2))).isEqualTo(inch("0.03125"));
        assertThat(relevantFrom(mm(60))).isEqualTo(inch("0.0625"));
    }

    @Test
    void aRandomPoreIsAQuarterOfTheWallCappedAtFiveThirtySeconds() {
        assertThat(randomLimit(WALL)).isEqualTo(mm("2.5"));
        assertThat(randomLimit(mm(40))).isEqualTo(inch("0.15625"));
    }

    @Test
    void anIsolatedPoreIsAThirdOfTheWallCappedAtAQuarterInch() {
        assertThat(isolatedLimit(mm(9))).isEqualTo(mm(3));
        assertThat(isolatedLimit(mm(30))).isEqualTo(inch("0.25"));
        assertThat(isolatedLimit(inch(2))).isEqualTo(inch("0.25"));
        assertThat(isolatedLimit(mm(60))).isEqualTo(inch("0.375"));
    }

    @Test
    void anIsolatedThreeMillimetrePoreIsAccepted() {
        assertThat(RoundedIndications.evaluate(List.of(pore(100, "3")), WALL)).isEmpty();
    }

    @Test
    void theSamePoreWithANeighbourCloseByIsJudgedAsRandom() {
        Indication big = pore(100, "3");
        List<Uw51Finding> findings = RoundedIndications.evaluate(List.of(big, pore(110, "1")), WALL);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).rule()).isEqualTo("b4");
        assertThat(findings.get(0).reason())
                .isEqualTo("a 3.0 mm rounded indication is over the 2.5 mm allowed at 10.0 mm wall");
        assertThat(findings.get(0).indications()).containsExactly(big);
    }

    @Test
    void aNeighbourAnInchAwayLeavesAPoreIsolated() {
        Indication big = pore(100, "3");
        Indication far = pore(126, "1");
        assertThat(RoundedIndications.isIsolated(big, List.of(big, far))).isTrue();
        assertThat(RoundedIndications.isIsolated(big, List.of(big, pore(125, "1"))))
                .isFalse();
    }

    @Test
    void anIsolatedPoreOverAThirdOfTheWallIsRefused() {
        List<Uw51Finding> findings = RoundedIndications.evaluate(List.of(pore(100, "3.5")), WALL);
        assertThat(findings).singleElement().satisfies(finding -> assertThat(finding.reason())
                .startsWith("an isolated 3.5 mm rounded indication is over the 3.3 mm"));
    }

    @Test
    void poresBelowTheThresholdDoNotCountAndDoNotSpoilIsolation() {
        Indication big = pore(100, "3");
        Indication speck = pore(105, "0.7");
        assertThat(RoundedIndications.relevant(List.of(big, speck), WALL)).containsExactly(big);
        assertThat(RoundedIndications.evaluate(List.of(big, speck), WALL)).isEmpty();
    }

    @Test
    void poresLinedUpOverTwelveWallsMayNotAddUpToTheWall() {
        List<Indication> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(pore(i * 25L, "2"));
        }
        assertThat(RoundedIndications.evaluate(four, WALL)).isEmpty();
        List<Indication> five = new ArrayList<>(four);
        five.add(pore(110, "2"));
        List<Uw51Finding> findings = RoundedIndications.evaluate(five, WALL);
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.reason())
                    .isEqualTo("5 rounded indications add up to 10.0 mm in 120.0 mm, not under the 10.0 mm wall");
            assertThat(finding.indications()).hasSize(5);
        });
    }

    @Test
    void theSamePoresSpreadPastTwelveWallsAreAccepted() {
        List<Indication> spread = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spread.add(pore(i * 40L, "2"));
        }
        assertThat(RoundedIndications.evaluate(spread, WALL)).isEmpty();
    }

    @Test
    void elongatedKindsAreNotRoundedIndications() {
        Indication slag = new Indication(Kind.ESI, mm(0), mm(10), mm(5));
        assertThat(RoundedIndications.relevant(List.of(slag), WALL)).isEmpty();
    }

    @Test
    void underTheVesselCodeAPoreFindingQuotesB4() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(pore(100, "3"), pore(110, "1")));
        Verdict verdict = Acceptance.judge(Code.ASME_VIII, found, WALL);
        assertThat(verdict.disposition()).isSameAs(Disposition.REPAIR);
        assertThat(verdict.clauses()).containsExactly("UW-51(b4)");
        assertThat(verdict.findings().get(0).kind()).isSameAs(Kind.P);
    }

    @Test
    void theFindingsComeInSubclauseOrder() {
        IndicationSet found = new IndicationSet(
                mm(1000),
                List.of(
                        pore(10, "3"),
                        pore(20, "1"),
                        new Indication(Kind.IF, mm(300), mm(10)),
                        new Indication(Kind.C, mm(600), mm(5))));
        List<Uw51Finding> findings = AsmeUw51.evaluate(found.indications(), WALL);
        assertThat(findings).extracting(Uw51Finding::rule).containsExactly("b1", "b2", "b4");
    }

    @Test
    void aWallOfNothingIsRefused() {
        assertThatThrownBy(() -> relevantFrom(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> randomLimit(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> isolatedLimit(new Length(-1))).isInstanceOf(ValidationError.class);
    }
}
