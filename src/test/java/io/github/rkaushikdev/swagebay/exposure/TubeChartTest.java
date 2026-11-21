package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.exposure.TubeCharts.split;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.sources.Tubes.tube;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TubeChartTest {

    private static final List<Map.Entry<String, String>> READINGS_150 =
            List.of(entry("6", "12"), entry("10", "24"), entry("13", "40"), entry("19", "95"));
    private static final List<Map.Entry<String, String>> READINGS_180 =
            List.of(entry("10", "12"), entry("13", "18"), entry("19", "40"), entry("25", "88"));
    private static final List<Map.Entry<String, String>> READINGS_200 =
            List.of(entry("13", "10"), entry("19", "20"), entry("25", "40"), entry("32", "88"));

    private static TubeChart aChart() {
        return new TubeChart(
                "steel-d7-2.0-700",
                "carbon_steel",
                "d7",
                new BigDecimal("2.0"),
                mm(700),
                List.of(
                        Curve.fromMm(150, READINGS_150),
                        Curve.fromMm(180, READINGS_180),
                        Curve.fromMm(200, READINGS_200)));
    }

    private static BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private static Curve.Point point(long thicknessMm, long mas) {
        return new Curve.Point(mm(thicknessMm), decimal(mas));
    }

    @Test
    void aPointOnACurveReadsBackExactly() {
        assertThat(aChart().masFor(150, mm(13))).isEqualByComparingTo("40");
        assertThat(aChart().masFor(200, mm(25))).isEqualByComparingTo("40");
    }

    @Test
    void aHarderBeamNeedsLessExposureAtTheSameThickness() {
        TubeChart chart = aChart();
        assertThat(chart.masFor(200, mm(19))).isLessThan(chart.masFor(180, mm(19)));
        assertThat(chart.masFor(180, mm(19))).isLessThan(chart.masFor(150, mm(19)));
    }

    @Test
    void interpolationAlongACurveIsLogarithmic() {
        BigDecimal middle = aChart().masFor(150, mm(8));
        BigDecimal geometric = Decimals.sqrt(Decimals.multiply(decimal(12), decimal(24)));
        assertThat(middle.subtract(geometric).abs()).isLessThan(new BigDecimal("0.01"));
    }

    @Test
    void aThicknessOffTheEndOfACurveIsRefused() {
        assertThatThrownBy(() -> aChart().masFor(150, mm(30)))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("to_mm")).isEqualTo("19"));
    }

    @Test
    void aKilovoltageWithNoCurveIsRefusedAndNamesTheOnesThereAre() {
        assertThatThrownBy(() -> aChart().masFor(165, mm(13)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(
                                caught.context().get("established"))
                        .isEqualTo("150, 180, 200"));
    }

    @Test
    void theNearestEstablishedCurveCanBeAskedForExplicitly() {
        TubeChart chart = aChart();
        assertThat(chart.nearestKilovoltage(155)).isEqualTo(150);
        assertThat(chart.nearestKilovoltage(175)).isEqualTo(180);
        assertThat(chart.nearestKilovoltage(300)).isEqualTo(200);
    }

    @Test
    void aTieGoesToTheHarderBeam() {
        assertThat(aChart().nearestKilovoltage(165)).isEqualTo(180);
    }

    @Test
    void theChartListsItsKilovoltagesInOrder() {
        assertThat(aChart().kilovoltages()).containsExactly(150, 180, 200);
    }

    @Test
    void aChartWithNoCurvesOrARepeatedOneIsRefused() {
        assertThatThrownBy(() -> new TubeChart("x", "carbon_steel", "d7", decimal(2), mm(700), List.of()))
                .isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new TubeChart(
                        "x",
                        "carbon_steel",
                        "d7",
                        decimal(2),
                        mm(700),
                        List.of(Curve.fromMm(150, READINGS_150), Curve.fromMm(150, READINGS_150))))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aCurveThatIsOutOfOrderOrFallsIsRefused() {
        assertThatThrownBy(() -> new Curve(150, List.of(point(19, 95), point(6, 12))))
                .isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new Curve(150, List.of(point(6, 95), point(19, 12))))
                .isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new Curve(150, List.of(point(6, 12)))).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void anExposureSplitsIntoACurrentAndATime() {
        TubeCharts.Split split = split(decimal(40), tube("eresco42"));
        assertThat(split.current()).isEqualByComparingTo("10");
        assertThat(split.time()).isEqualTo(seconds(4));
    }

    @Test
    void aLowerCurrentGivesALongerTime() {
        TubeCharts.Split fast = split(decimal(40), tube("eresco42"), decimal(10));
        TubeCharts.Split slow = split(decimal(40), tube("eresco42"), decimal(5));
        assertThat(slow.time()).isGreaterThan(fast.time());
    }

    @Test
    void aCurrentOverTheSetIsRefused() {
        assertThatThrownBy(() -> split(decimal(40), tube("eresco42"), decimal(20)))
                .isInstanceOf(TechniqueError.class);
    }

    @Test
    void anExposureUnderASecondIsRefusedRatherThanPrinted() {
        assertThatThrownBy(() -> split(new BigDecimal("0.5"), tube("eresco42")))
                .isInstanceOfSatisfying(
                        TechniqueError.class,
                        caught -> assertThat(caught.context().get("ma")).isEqualTo("10"));
    }

    @Test
    void anExposureOfNothingIsRefused() {
        assertThatThrownBy(() -> split(decimal(0), tube("eresco42"))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aCurveSaysWhatItCovers() {
        Curve curve = Curve.fromMm(150, READINGS_150);
        assertThat(curve.thinnest()).isEqualTo(mm(6));
        assertThat(curve.thickest()).isEqualTo(mm(19));
        assertThat(curve.covers(mm(10))).isTrue();
        assertThat(curve.covers(new Length(1))).isFalse();
    }
}
