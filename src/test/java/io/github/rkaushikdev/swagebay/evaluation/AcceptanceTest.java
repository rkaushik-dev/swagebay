package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Acceptance.codesDisagree;
import static io.github.rkaushikdev.swagebay.evaluation.Acceptance.judge;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptanceTest {

    private static final Length WALL = mm(10);

    private static IndicationSet weld(Indication... indications) {
        return new IndicationSet(mm(1000), List.of(indications));
    }

    private static Indication lof(long length) {
        return new Indication(Kind.IF, mm(0), mm(length));
    }

    @Test
    void aCleanWeldIsAcceptedUnderBothCodes() {
        IndicationSet clean = weld();
        assertThat(judge(Code.API_1104, clean, WALL).accepted()).isTrue();
        assertThat(judge(Code.ASME_VIII, clean, WALL).accepted()).isTrue();
        assertThat(codesDisagree(clean, WALL)).isFalse();
    }

    @Test
    void theVerdictCarriesTheCodeItWasReachedUnder() {
        Verdict verdict = judge(Code.API_1104, weld(), WALL);
        assertThat(verdict.code()).isSameAs(Code.API_1104);
        assertThat(verdict.describe()).isEqualTo("accept to API 1104");
        assertThat(verdict).isInstanceOf(Verdict.class);
    }

    @Test
    void theTwoCodesDisagreeAboutATenMillimetreLackOfFusion() {
        IndicationSet found = weld(lof(10));
        assertThat(judge(Code.API_1104, found, WALL).accepted()).isTrue();
        assertThat(judge(Code.ASME_VIII, found, WALL).accepted()).isFalse();
        assertThat(codesDisagree(found, WALL)).isTrue();
    }

    @Test
    void theAsmeFindingQuotesAnAsmeClause() {
        Verdict verdict = judge(Code.ASME_VIII, weld(lof(10)), WALL);
        assertThat(verdict.clauses()).containsExactly("UW-51(b2)");
        assertThat(verdict.describe()).startsWith("ASME Section VIII Division 1: ");
    }

    @Test
    void theApiFindingQuotesAnApiClause() {
        Verdict verdict = judge(Code.API_1104, weld(lof(40)), WALL);
        assertThat(verdict.clauses()).containsExactly("9.3.4");
    }

    @Test
    void aCrackIsRejectedUnderBoth() {
        IndicationSet found = weld(new Indication(Kind.C, mm(10), mm(20)));
        for (Code code : Code.values()) {
            assertThat(judge(code, found, WALL).disposition()).isSameAs(Disposition.REJECT);
        }
        assertThat(codesDisagree(found, WALL)).isFalse();
    }

    @Test
    void slagIsJudgedByApiAndLeftAloneByTheAsmeElongatedRule() {
        IndicationSet found = weld(new Indication(Kind.ESI, mm(10), mm(60), mm(1)));
        assertThat(judge(Code.API_1104, found, WALL).accepted()).isFalse();
        assertThat(judge(Code.ASME_VIII, found, WALL).accepted()).isTrue();
    }

    @Test
    void theCodesAreToldApartByTheShapeOfTheirRules() {
        assertThat(Code.API_1104.judgesByKind()).isTrue();
        assertThat(Code.ASME_VIII.judgesByKind()).isFalse();
        assertThat(Code.ASME_VIII.windowMovesWithTheWall()).isTrue();
        assertThat(Code.API_1104.windowMovesWithTheWall()).isFalse();
    }

    @Test
    void aWallOfNothingIsRefusedUnderEitherCode() {
        for (Code code : Code.values()) {
            assertThatThrownBy(() -> judge(code, weld(), Length.ZERO)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void theSameIndicationsFlipWithTheWallUnderAsmeOnly() {
        IndicationSet found = weld(lof(10));
        assertThat(judge(Code.ASME_VIII, found, mm(10)).accepted()).isFalse();
        assertThat(judge(Code.ASME_VIII, found, mm(40)).accepted()).isTrue();
        assertThat(judge(Code.API_1104, found, mm(10)).accepted()).isTrue();
        assertThat(judge(Code.API_1104, found, mm(40)).accepted()).isTrue();
    }

    @Test
    void theCodePrintsTheWayASpecificationNamesIt() {
        assertThat(Code.API_1104).hasToString("API 1104");
        assertThat(Code.ASME_VIII).hasToString("ASME Section VIII Division 1");
    }
}
