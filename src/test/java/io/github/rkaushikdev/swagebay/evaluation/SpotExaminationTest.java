package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Disposition.ACCEPT;
import static io.github.rkaushikdev.swagebay.evaluation.Disposition.REJECT;
import static io.github.rkaushikdev.swagebay.evaluation.Disposition.REPAIR;
import static io.github.rkaushikdev.swagebay.evaluation.SpotExamination.standing;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpotExaminationTest {

    @Test
    void nothingShotIsNothingDecided() {
        assertThat(standing(List.of())).isEqualTo(new SpotExamination.NotShot());
        assertThat(standing(List.of()).settled()).isFalse();
        assertThat(standing(List.of()).describe()).isEqualTo("the spot has not been shot");
    }

    @Test
    void aSpotThatPassesAcceptsTheIncrement() {
        SpotExamination.Standing standing = standing(List.of(ACCEPT));
        assertThat(standing).isEqualTo(new SpotExamination.Accepted(false));
        assertThat(standing.settled()).isTrue();
        assertThat(standing.describe()).isEqualTo("accepted on the spot");
    }

    @Test
    void aFailedSpotCallsForTwoTracers() {
        assertThat(standing(List.of(REPAIR))).isEqualTo(new SpotExamination.TracersDue(2));
        assertThat(standing(List.of(REJECT, ACCEPT))).isEqualTo(new SpotExamination.TracersDue(1));
        assertThat(standing(List.of(REPAIR)).describe()).isEqualTo("2 tracer spots are due");
        assertThat(standing(List.of(REPAIR, ACCEPT)).describe()).isEqualTo("1 tracer spot is due");
        assertThat(standing(List.of(REPAIR)).settled()).isFalse();
    }

    @Test
    void eitherTracerFailingRejectsTheIncrement() {
        assertThat(standing(List.of(REPAIR, REPAIR))).isEqualTo(new SpotExamination.Rejected(1));
        assertThat(standing(List.of(REPAIR, ACCEPT, REJECT))).isEqualTo(new SpotExamination.Rejected(2));
        assertThat(standing(List.of(REPAIR, ACCEPT, REPAIR, ACCEPT))).isEqualTo(new SpotExamination.Rejected(2));
        assertThat(standing(List.of(REPAIR, REPAIR)).describe())
                .isEqualTo("tracer 1 failed: reweld the increment or radiograph all of it");
        assertThat(standing(List.of(REPAIR, REPAIR)).settled()).isTrue();
    }

    @Test
    void twoGoodTracersLeaveTheFirstDefectToRepair() {
        SpotExamination.Standing due = standing(List.of(REPAIR, ACCEPT, ACCEPT));
        assertThat(due).isEqualTo(new SpotExamination.RepairDue(0));
        assertThat(due.describe()).isEqualTo("the defect on the first spot is to be repaired and shot again");
        assertThat(standing(List.of(REPAIR, ACCEPT, ACCEPT, REPAIR))).isEqualTo(new SpotExamination.RepairDue(1));
        assertThat(standing(List.of(REPAIR, ACCEPT, ACCEPT, REPAIR, REPAIR)).describe())
                .isEqualTo("the repair of the first spot failed 2 times");
        assertThat(standing(List.of(REPAIR, ACCEPT, ACCEPT, REPAIR)).describe())
                .isEqualTo("the repair of the first spot failed 1 time");
    }

    @Test
    void aRepairThatPassesAcceptsTheIncrement() {
        SpotExamination.Standing standing = standing(List.of(REPAIR, ACCEPT, ACCEPT, REPAIR, ACCEPT));
        assertThat(standing).isEqualTo(new SpotExamination.Accepted(true));
        assertThat(standing.describe()).isEqualTo("accepted after two tracers and a repair");
    }

    @Test
    void everyCodeReachesItsDispositionTheSameWay() {
        Finding slag = new Finding("9.3.8", Kind.ESI, "too long");
        Finding crack = new Finding("9.3.10", Kind.C, "a crack");
        assertThat(Disposition.of(List.of())).isSameAs(ACCEPT);
        assertThat(Disposition.of(List.of(slag))).isSameAs(REPAIR);
        assertThat(Disposition.of(List.of(slag, crack))).isSameAs(REJECT);
        IndicationSet found = new IndicationSet(mm(500), List.of(new Indication(Kind.IF, mm(10), mm(3))));
        for (Code code : Code.values()) {
            Verdict verdict = Acceptance.judge(code, found, mm(15));
            assertThat(verdict.disposition()).isSameAs(Disposition.of(verdict.findings()));
        }
    }
}
