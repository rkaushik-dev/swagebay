package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static io.github.rkaushikdev.swagebay.sources.Output.ONE_METRE;
import static io.github.rkaushikdev.swagebay.sources.Output.doseRateAt;
import static io.github.rkaushikdev.swagebay.sources.Output.outputAtOneMetre;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import org.junit.jupiter.api.Test;

class OutputTest {

    @Test
    void aHundredCuriesOfIridiumReadsFortyEightRoentgenAtAMetre() {
        DoseRate rate = outputAtOneMetre(IR192, curie(100));
        assertThat(rate.mrPerHour()).isEqualByComparingTo("48000");
        assertThat(rate.msvPerHour()).isEqualByComparingTo("480");
    }

    @Test
    void cobaltIsNearlyThreeTimesHotterThanIridiumCurieForCurie() {
        DoseRate ir = outputAtOneMetre(IR192, curie(10));
        DoseRate co = outputAtOneMetre(CO60, curie(10));
        assertThat(co.nsvPerHour()).isGreaterThan(ir.nsvPerHour() * 2);
        assertThat(outputAtOneMetre(SE75, curie(10))).isLessThan(ir);
    }

    @Test
    void anEmptyPigReadsNothing() {
        assertThat(outputAtOneMetre(IR192, Activity.ZERO)).isEqualTo(DoseRate.ZERO);
    }

    @Test
    void theRateAtAMetreMatchesTheGeneralForm() {
        assertThat(doseRateAt(IR192, curie(100), ONE_METRE)).isEqualTo(outputAtOneMetre(IR192, curie(100)));
    }

    @Test
    void halvingTheDistanceQuadruplesTheRate() {
        DoseRate near = doseRateAt(IR192, curie(100), Length.fromM(2));
        DoseRate far = doseRateAt(IR192, curie(100), Length.fromM(4));
        assertThat(Math.abs(near.nsvPerHour() - far.nsvPerHour() * 4)).isLessThan(10);
    }

    @Test
    void backingOffByTenCutsTheRateByAHundred() {
        DoseRate close = doseRateAt(IR192, curie(90), Length.fromM(1));
        DoseRate away = doseRateAt(IR192, curie(90), Length.fromM(10));
        assertThat(Math.abs(close.nsvPerHour() - away.nsvPerHour() * 100)).isLessThan(100);
    }

    @Test
    void aRateNeedsSomewhereToBeMeasured() {
        assertThatThrownBy(() -> doseRateAt(IR192, curie(90), Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> doseRateAt(IR192, curie(90), new Length(-1))).isInstanceOf(ValidationError.class);
    }
}
