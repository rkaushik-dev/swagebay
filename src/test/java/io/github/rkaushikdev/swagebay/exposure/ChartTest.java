package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChartTest {

    private static final List<Map.Entry<String, String>> READINGS = List.of(
            entry("0.25", "4"),
            entry("0.50", "8"),
            entry("0.75", "16"),
            entry("1.00", "30"),
            entry("1.25", "55"),
            entry("1.50", "100"),
            entry("1.75", "180"),
            entry("2.00", "320"));

    private static ExposureChart chart() {
        return ExposureChart.fromInches("ir192-d7-2.0", "ir192", "carbon_steel", "d7", "2.0", "24", READINGS);
    }

    private static BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private static ExposureChart bad(ChartPoint... points) {
        return new ExposureChart("bad", "ir192", "carbon_steel", "d7", decimal(2), inch(24), List.of(points));
    }

    @Test
    void aPointOnTheChartReadsBackExactly() {
        assertThat(chart().factorFor(inch("1.00"))).isEqualByComparingTo("30");
        assertThat(chart().factorFor(inch("0.25"))).isEqualByComparingTo("4");
        assertThat(chart().factorFor(inch("2.00"))).isEqualByComparingTo("320");
    }

    @Test
    void theChartKnowsWhatItWasDrawnFor() {
        ExposureChart card = chart();
        assertThat(card.isotopeKey()).isEqualTo("ir192");
        assertThat(card.filmKey()).isEqualTo("d7");
        assertThat(card.density()).isEqualByComparingTo("2.0");
        assertThat(card.referenceSfd()).isEqualTo(inch(24));
    }

    @Test
    void interpolationIsLogarithmicNotLinear() {
        BigDecimal midpoint = chart().factorFor(inch("1.125"));
        BigDecimal linear = Decimals.divide(Decimals.add(decimal(30), decimal(55)), 2);
        BigDecimal geometric = Decimals.sqrt(Decimals.multiply(decimal(30), decimal(55)));
        assertThat(midpoint.subtract(geometric).abs()).isLessThan(new BigDecimal("0.01"));
        assertThat(midpoint).isLessThan(linear);
    }

    @Test
    void linearReadingWouldComeOutLongInEveryInterval() {
        ExposureChart card = chart();
        for (int i = 0; i + 1 < READINGS.size(); i++) {
            Map.Entry<String, String> low = READINGS.get(i);
            Map.Entry<String, String> high = READINGS.get(i + 1);
            Length middle = Length.fromInch(low.getKey())
                    .plus(Length.fromInch(high.getKey()))
                    .dividedBy(2);
            BigDecimal linear =
                    Decimals.divide(Decimals.add(new BigDecimal(low.getValue()), new BigDecimal(high.getValue())), 2);
            assertThat(card.factorFor(middle)).isLessThan(linear);
        }
    }

    @Test
    void theCurveRisesAllTheWayAlong() {
        ExposureChart card = chart();
        List<BigDecimal> values = new ArrayList<>();
        for (int t = 7; t < 50; t += 3) {
            values.add(card.factorFor(mm(t)));
        }
        assertThat(values).isSorted();
    }

    @Test
    void aThicknessOffTheEndIsRefusedAndNamesTheRange() {
        ExposureChart card = chart();
        assertThatThrownBy(() -> card.factorFor(inch("2.5"))).isInstanceOfSatisfying(TechniqueError.class, caught -> {
            assertThat(caught.context().get("from_mm")).isEqualTo("6.35");
            assertThat(caught.context().get("to_mm")).isEqualTo("50.8");
        });
        assertThatThrownBy(() -> card.factorFor(inch("0.1"))).isInstanceOf(TechniqueError.class);
    }

    @Test
    void theChartSaysWhatItCovers() {
        ExposureChart card = chart();
        assertThat(card.covers(inch("1.0"))).isTrue();
        assertThat(card.covers(card.thinnest())).isTrue();
        assertThat(card.covers(card.thickest())).isTrue();
        assertThat(card.covers(inch("2.01"))).isFalse();
    }

    @Test
    void readingTheChartBackwardsFindsTheThickness() {
        ExposureChart card = chart();
        assertThat(card.thicknessFor(decimal(30))).isEqualTo(inch("1.00"));
        Length recovered = card.thicknessFor(card.factorFor(inch("1.30")));
        assertThat(Math.abs(recovered.um() - inch("1.30").um())).isLessThan(20);
    }

    @Test
    void anExposureOffTheEndIsRefusedBackwardsToo() {
        ExposureChart card = chart();
        assertThatThrownBy(() -> card.thicknessFor(decimal(1000))).isInstanceOf(TechniqueError.class);
        assertThatThrownBy(() -> card.thicknessFor(decimal(1))).isInstanceOf(TechniqueError.class);
        assertThatThrownBy(() -> card.thicknessFor(decimal(0))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aChartNeedsTwoPointsToBeAChart() {
        assertThatThrownBy(() -> new ExposureChart(
                        "thin",
                        "ir192",
                        "carbon_steel",
                        "d7",
                        decimal(2),
                        inch(24),
                        List.of(new ChartPoint(mm(10), decimal(5)))))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aChartOutOfOrderIsRefused() {
        assertThatThrownBy(() -> bad(new ChartPoint(mm(20), decimal(10)), new ChartPoint(mm(10), decimal(5))))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.message())
                        .contains("out of order"));
    }

    @Test
    void aChartThatFallsIsRefused() {
        assertThatThrownBy(() -> bad(new ChartPoint(mm(10), decimal(50)), new ChartPoint(mm(20), decimal(10))))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.message())
                        .contains("rise"));
    }

    @Test
    void twoPointsAtOneThicknessAreRefused() {
        assertThatThrownBy(() -> bad(new ChartPoint(mm(10), decimal(5)), new ChartPoint(mm(10), decimal(10))))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aPointNeedsARealThicknessAndARealExposure() {
        assertThatThrownBy(() -> new ChartPoint(Length.ZERO, decimal(5))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new ChartPoint(mm(10), decimal(0))).isInstanceOf(ValidationError.class);
    }
}
