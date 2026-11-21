package io.github.rkaushikdev.swagebay.exposure;

import static io.github.rkaushikdev.swagebay.exposure.Catalogue.allCharts;
import static io.github.rkaushikdev.swagebay.exposure.Catalogue.chart;
import static io.github.rkaushikdev.swagebay.exposure.Catalogue.chartFor;
import static io.github.rkaushikdev.swagebay.exposure.Catalogue.chartKeys;
import static io.github.rkaushikdev.swagebay.exposure.Catalogue.chartsForFilmSubstitution;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogueTest {

    private static final BigDecimal PLAIN = new BigDecimal("2.0");

    @Test
    void theShopOwnsSixCurves() {
        assertThat(allCharts()).hasSize(6);
        assertThat(chartKeys()).hasSize(6);
        assertThat(chartKeys()).contains("ir192-steel-d7-2.0");
    }

    @Test
    void everyChartIsInternallySound() {
        for (ExposureChart card : allCharts()) {
            assertThat(card.thinnest()).isLessThan(card.thickest());
            assertThat(card.factorFor(card.thinnest())).isLessThan(card.factorFor(card.thickest()));
        }
    }

    @Test
    void theWorkingChartIsTheIridiumSteelOne() {
        ExposureChart card = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        assertThat(card.key()).isEqualTo("ir192-steel-d7-2.0");
        assertThat(card.factorFor(inch("1.00"))).isEqualByComparingTo("30");
        assertThat(card.referenceSfd()).isEqualTo(inch(24));
    }

    @Test
    void aSlowerFilmTakesAboutTwiceTheExposure() {
        ExposureChart fast = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        ExposureChart slow = chartFor("ir192", "carbon_steel", "d5", PLAIN);
        BigDecimal ratio = Decimals.divide(slow.factorFor(inch(1)), fast.factorFor(inch(1)));
        assertThat(ratio).isStrictlyBetween(new BigDecimal("1.7"), new BigDecimal("2.0"));
    }

    @Test
    void aHigherDensityCostsMoreExposureAtTheSameThickness() {
        ExposureChart plain = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        ExposureChart dense = chartFor("ir192", "carbon_steel", "d7", new BigDecimal("2.5"));
        assertThat(dense.factorFor(inch(1))).isGreaterThan(plain.factorFor(inch(1)));
    }

    @Test
    void seleniumIsSlowerThanIridiumAtTheSameThickness() {
        ExposureChart ir = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        ExposureChart se = chartFor("se75", "carbon_steel", "d7", PLAIN);
        assertThat(se.factorFor(inch("0.5")))
                .isGreaterThan(Decimals.multiply(ir.factorFor(inch("0.5")), BigDecimal.valueOf(4)));
    }

    @Test
    void theChartsCoverTheRangesTheirIsotopesAreUsedOver() {
        ExposureChart se = chartFor("se75", "carbon_steel", "d7", PLAIN);
        ExposureChart co = chartFor("co60", "carbon_steel", "d7", PLAIN);
        assertThat(se.thickest()).isLessThan(inch("1.01"));
        assertThat(co.thinnest()).isGreaterThanOrEqualTo(inch(2));
        assertThat(co.thickest()).isEqualTo(inch(8));
    }

    @Test
    void theCobaltChartIsDrawnAtALongerDistance() {
        ExposureChart co = chartFor("co60", "carbon_steel", "d7", PLAIN);
        ExposureChart ir = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        assertThat(co.referenceSfd()).isEqualTo(inch(36));
        assertThat(ir.referenceSfd()).isEqualTo(inch(24));
    }

    @Test
    void aluminiumShootsFarFasterThanSteel() {
        ExposureChart alu = chartFor("ir192", "aluminium", "d7", PLAIN);
        ExposureChart steel = chartFor("ir192", "carbon_steel", "d7", PLAIN);
        assertThat(alu.factorFor(inch(1))).isLessThan(Decimals.divide(steel.factorFor(inch(1)), 5));
    }

    @Test
    void anUnestablishedCombinationIsRefusedAndNamesAllFour() {
        assertThatThrownBy(() -> chartFor("ir192", "inconel", "d7", PLAIN))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> {
                    assertThat(caught.context().get("material")).isEqualTo("inconel");
                    assertThat(caught.context().get("film")).isEqualTo("d7");
                    assertThat(caught.context().get("density")).isEqualTo("2.0");
                });
    }

    @Test
    void aDensityIsNeverSubstitutedSilently() {
        assertThatThrownBy(() -> chartFor("ir192", "carbon_steel", "d7", new BigDecimal("2.2")))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void filmSubstitutionReturnsTheChartsThatMatchEverythingElse() {
        assertThat(chartsForFilmSubstitution("ir192", "carbon_steel", PLAIN))
                .extracting(ExposureChart::filmKey)
                .containsExactlyInAnyOrder("d7", "d5");
        assertThat(chartsForFilmSubstitution("co60", "carbon_steel", new BigDecimal("2.5")))
                .isEmpty();
    }

    @Test
    void aChartCanBeFetchedByKey() {
        assertThat(chart("se75-steel-d7-2.0").isotopeKey()).isEqualTo("se75");
        assertThatThrownBy(() -> chart("ir192-steel-d3-2.0")).isInstanceOf(ConfigurationError.class);
    }
}
