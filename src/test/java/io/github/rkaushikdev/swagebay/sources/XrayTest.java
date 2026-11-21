package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.sources.Tubes.milliampereSeconds;
import static io.github.rkaushikdev.swagebay.sources.Tubes.timeFor;
import static io.github.rkaushikdev.swagebay.sources.Tubes.tube;
import static io.github.rkaushikdev.swagebay.sources.Tubes.tubesReaching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class XrayTest {

    private static BigDecimal ma(long value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void aSetCarriesItsRangeAndItsFocalSpot() {
        Tube small = tube("eresco42");
        assertThat(small.minKv()).isEqualTo(10);
        assertThat(small.maxKv()).isEqualTo(200);
        assertThat(small.focalSpot()).isEqualTo(mm("1.0"));
    }

    @Test
    void aSetSaysWhatItCanReach() {
        Tube small = tube("eresco42");
        assertThat(small.canReach(150)).isTrue();
        assertThat(small.canReach(300)).isFalse();
        assertThat(small.canReach(5)).isFalse();
    }

    @Test
    void aKilovoltageOutsideTheSetIsRefusedWithTheRange() {
        assertThatThrownBy(() -> tube("eresco42").checkKv(300))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("range")).isEqualTo("10 to 200"));
    }

    @Test
    void aCrawlerSetHasExactlyOneKilovoltage() {
        Tube fixed = tube("xrs3");
        assertThat(fixed.minKv()).isEqualTo(270);
        assertThat(fixed.maxKv()).isEqualTo(270);
        assertThat(fixed.canReach(270)).isTrue();
        assertThat(fixed.canReach(269)).isFalse();
    }

    @Test
    void aCurrentOverWhatTheSetGivesIsRefused() {
        assertThatThrownBy(() -> tube("eresco42").checkCurrent(ma(20)))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("max_ma")).isEqualTo("10"));
        tube("eresco42").checkCurrent(ma(10));
    }

    @Test
    void aCurrentOfNothingIsRefusedAsAValueProblem() {
        assertThatThrownBy(() -> tube("eresco42").checkCurrent(ma(0))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theQuantityKnobScalesInBothOfItsInputs() {
        assertThat(milliampereSeconds(ma(5), seconds(60))).isEqualByComparingTo("300");
        assertThat(milliampereSeconds(ma(10), seconds(60))).isEqualByComparingTo("600");
        assertThat(milliampereSeconds(ma(5), minutes(2))).isEqualByComparingTo("600");
    }

    @Test
    void theTimeForAnExposureInvertsTheQuantity() {
        assertThat(timeFor(ma(5), ma(300))).isEqualTo(seconds(60));
        assertThat(timeFor(ma(10), ma(300))).isEqualTo(seconds(30));
    }

    @Test
    void theTimeIsRoundedUpToAWholeSecond() {
        assertThat(timeFor(ma(7), ma(100))).isEqualTo(seconds(15));
    }

    @Test
    void aQuantityOrACurrentOfNothingIsRefused() {
        assertThatThrownBy(() -> milliampereSeconds(ma(0), seconds(60))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> timeFor(ma(0), ma(300))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> timeFor(ma(5), ma(0))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aHalfDutySetRestsAsLongAsItRan() {
        assertThat(tube("eresco42").coolingAfter(minutes(2))).isEqualTo(minutes(2));
    }

    @Test
    void aLowDutySetRestsFourTimesAsLong() {
        assertThat(tube("xrs3").coolingAfter(minutes(1))).isEqualTo(minutes(4));
    }

    @Test
    void aContinuousSetNeedsNoRest() {
        assertThat(tube("mg452").coolingAfter(minutes(10))).isEqualTo(Duration.ZERO);
    }

    @Test
    void theBeamQualityStepsWithTheKilovoltage() {
        Tube small = tube("eresco42");
        assertThat(small.qualityAt(80)).isSameAs(Quality.KV100);
        assertThat(small.qualityAt(100)).isSameAs(Quality.KV100);
        assertThat(small.qualityAt(120)).isSameAs(Quality.KV150);
        assertThat(small.qualityAt(200)).isSameAs(Quality.KV220);
    }

    @Test
    void theQualityLookupRefusesAKilovoltageTheSetCannotMake() {
        assertThatThrownBy(() -> tube("eresco42").qualityAt(300)).isInstanceOf(TechniqueError.class);
    }

    @Test
    void aHighKilovoltageSetReadsTheFourHundredColumn() {
        assertThat(tube("mg452").qualityAt(400)).isSameAs(Quality.KV400);
        assertThat(tube("mg452").qualityAt(450)).isSameAs(Quality.MV1);
    }

    @Test
    void theSetsThatReachAKilovoltageComeBackFinestSpotFirst() {
        assertThat(tubesReaching(150)).extracting(Tube::key).containsExactly("eresco42", "eresco65", "mg452");
        assertThat(tubesReaching(500)).isEmpty();
    }

    @Test
    void aSetWithNoRangeOrNoSpotIsRefused() {
        assertThatThrownBy(() -> build(10, 5, ma(5), mm(1), new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> build(0, 200, ma(5), mm(1), new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> build(10, 200, ma(0), mm(1), new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> build(10, 200, ma(5), Length.ZERO, new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> build(10, 200, ma(5), mm(1), new BigDecimal("1.5")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCatalogueIsClosed() {
        assertThat(Tubes.knownTubes()).containsExactly("eresco42", "eresco65", "mg452", "xrs3");
        assertThatThrownBy(() -> tube("eresco99")).isInstanceOf(ConfigurationError.class);
        assertThat(tube("eresco42")).hasToString("ERESCO 42 MF4 10 to 200 kV");
    }

    private static Tube build(int minKv, int maxKv, BigDecimal maxMa, Length focalSpot, BigDecimal dutyCycle) {
        return new Tube("x", "x", "x", minKv, maxKv, maxMa, focalSpot, dutyCycle);
    }
}
