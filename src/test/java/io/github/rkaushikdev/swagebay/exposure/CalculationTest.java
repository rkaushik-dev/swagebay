package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.exposure.Calculation.MINIMUM_PRACTICAL;
import static io.github.rkaushikdev.swagebay.exposure.Calculation.activityNeeded;
import static io.github.rkaushikdev.swagebay.exposure.Calculation.exposureTime;
import static io.github.rkaushikdev.swagebay.exposure.Calculation.planExposure;
import static io.github.rkaushikdev.swagebay.exposure.Calculation.totalForExposures;
import static io.github.rkaushikdev.swagebay.exposure.Corrections.distanceCorrection;
import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculationTest {

    private static ExposureChart workingChart() {
        return Catalogue.chartFor("ir192", "carbon_steel", "d7", new BigDecimal("2.0"));
    }

    private static BigDecimal reading(String value) {
        return new BigDecimal(value);
    }

    @Test
    void thirtyCurieMinutesOnAThirtyCurieSourceIsAMinute() {
        assertThat(exposureTime(reading("30"), curie(30))).isEqualTo(minutes(1));
        assertThat(exposureTime(reading("60"), curie(30))).isEqualTo(minutes(2));
        assertThat(exposureTime(reading("15"), curie(30))).isEqualTo(seconds(30));
    }

    @Test
    void theTimerIsRoundedUpToAWholeSecond() {
        Duration span = exposureTime(reading("30.1"), curie(30));
        assertThat(span).isEqualTo(seconds(61));
        assertThat(span.asTimer()).isEqualTo("1:01");
    }

    @Test
    void anEmptyPigCannotMakeAnExposure() {
        assertThatThrownBy(() -> exposureTime(reading("30"), Activity.ZERO)).isInstanceOf(TechniqueError.class);
        assertThatThrownBy(() -> exposureTime(reading("0"), curie(30))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aPlainShotOffTheChartAtTheChartDistance() {
        ExposureResult result = planExposure(workingChart(), inch(1), new CorrectionChain(), curie(60));
        assertThat(result.chartReading()).isEqualByComparingTo("30");
        assertThat(result.correctedReading()).isEqualByComparingTo("30");
        assertThat(result.exposure()).isEqualTo(seconds(30));
        assertThat(result.isPractical()).isTrue();
    }

    @Test
    void theResultCarriesTheWholeArithmetic() {
        CorrectionChain chain = new CorrectionChain().withCorrection(distanceCorrection(inch(24), inch(36)));
        ExposureResult result = planExposure(workingChart(), inch(1), chain, curie(60));
        assertThat(result.chartKey()).isEqualTo("ir192-steel-d7-2.0");
        assertThat(result.corrections().names()).containsExactly("distance");
        assertThat(result.correctedReading()).isEqualByComparingTo(Decimals.multiply(reading("30"), reading("2.25")));
        assertThat(result.exposure()).isEqualTo(seconds(68));
    }

    @Test
    void backingOffLengthensTheShotByTheSquare() {
        ExposureResult close = planExposure(workingChart(), inch(1), new CorrectionChain(), curie(60));
        CorrectionChain chain = new CorrectionChain().withCorrection(distanceCorrection(inch(24), inch(48)));
        ExposureResult far = planExposure(workingChart(), inch(1), chain, curie(60));
        assertThat(far.exposure().ms()).isEqualTo(close.exposure().ms() * 4);
    }

    @Test
    void aShotUnderTenSecondsIsFlaggedRatherThanPrinted() {
        ExposureResult result = planExposure(workingChart(), inch("0.25"), new CorrectionChain(), curie(120));
        assertThat(result.exposure()).isLessThan(MINIMUM_PRACTICAL);
        assertThat(result.isPractical()).isFalse();
        assertThat(result.warnings().get(0)).contains("ten second floor");
    }

    @Test
    void aShotThatDoesNotFitTheWindowIsFlagged() {
        ExposureResult result = planExposure(workingChart(), inch(2), new CorrectionChain(), curie(20), minutes(10));
        assertThat(result.isPractical()).isFalse();
        assertThat(result.warnings().get(0)).contains("window");
    }

    @Test
    void aShotThatFitsTheWindowIsNotFlagged() {
        ExposureResult result = planExposure(workingChart(), inch(1), new CorrectionChain(), curie(60), minutes(10));
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void bothBoundsCanFireAtOnceOnlyWhenTheyReallyDo() {
        ExposureResult result =
                planExposure(workingChart(), inch("0.25"), new CorrectionChain(), curie(120), minutes(10));
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void theDescriptionIsWhatGoesOnTheTechniqueSheet() {
        ExposureResult result = planExposure(workingChart(), inch(1), new CorrectionChain(), curie(60));
        String text = result.describe();
        assertThat(text).contains("ir192-steel-d7-2.0");
        assertThat(text).contains("25.40 mm");
        assertThat(text).contains("no corrections");
        assertThat(text).contains("0:30");
    }

    @Test
    void aThicknessOffTheChartRefusesBeforeAnyDivision() {
        assertThatThrownBy(() -> planExposure(workingChart(), inch(4), new CorrectionChain(), curie(60)))
                .isInstanceOf(TechniqueError.class);
    }

    @Test
    void workingBackwardsToTheSourceAShotWouldNeed() {
        assertThat(activityNeeded(reading("30"), minutes(1))).isEqualTo(curie(30));
        assertThat(activityNeeded(reading("30"), minutes(2))).isEqualTo(curie(15));
        assertThatThrownBy(() -> activityNeeded(reading("30"), Duration.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> activityNeeded(reading("0"), minutes(1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theSourceIsOutForTheWholeJointNotOneShot() {
        assertThat(totalForExposures(minutes(1), 3)).isEqualTo(minutes(3));
        assertThat(totalForExposures(seconds(30), 2)).isEqualTo(minutes(1));
        assertThatThrownBy(() -> totalForExposures(minutes(1), 0)).isInstanceOf(ValidationError.class);
    }
}
