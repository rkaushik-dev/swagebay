package io.github.rkaushikdev.swagebay.materials;

import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MaterialsTest {

    private static final Material STEEL = Materials.STEEL;

    @Test
    void steelIsOneAtEveryPublishedQuality() {
        for (Quality quality : Quality.values()) {
            assertThat(STEEL.factorAt(quality)).isEqualByComparingTo("1");
        }
    }

    @Test
    void steelEquivalentOfSteelIsTheThicknessItself() {
        Length wall = inch("0.375");
        assertThat(STEEL.steelEquivalent(wall, Quality.IR192)).isEqualTo(wall);
    }

    @Test
    void aluminiumShootsLikeAThirdOfTheSteelUnderIridium() {
        Material alu = Materials.material("aluminium");
        assertThat(alu.factorAt(Quality.IR192)).isEqualByComparingTo("0.35");
        assertThat(alu.steelEquivalent(mm(20), Quality.IR192)).isEqualTo(mm(7));
    }

    @Test
    void theFactorDependsOnTheBeamAndNotOnlyTheMetal() {
        Material alu = Materials.material("aluminium");
        assertThat(alu.factorAt(Quality.KV100)).isEqualByComparingTo("0.08");
        assertThat(alu.factorAt(Quality.KV220)).isEqualByComparingTo("0.18");
        assertThat(alu.factorAt(Quality.KV100)).isNotEqualByComparingTo(alu.factorAt(Quality.IR192));
    }

    @Test
    void aHoleInThePublishedTableIsReportedNotGuessed() {
        Material magnesium = Materials.material("magnesium");
        assertThatThrownBy(() -> magnesium.factorAt(Quality.IR192))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> {
                    assertThat(caught.context().get("material")).isEqualTo("magnesium");
                    assertThat(caught.context().get("quality")).isEqualTo("Ir-192");
                });
    }

    @Test
    void theHeavyMetalsGoTheOtherWay() {
        Material lead = Materials.material("lead");
        assertThat(lead.factorAt(Quality.KV100)).isEqualByComparingTo("14.0");
        assertThat(lead.steelEquivalent(mm(1), Quality.KV100)).isEqualTo(mm(14));
        assertThat(lead.factorAt(Quality.CO60)).isLessThan(lead.factorAt(Quality.IR192));
    }

    @Test
    void inconelIsFlatAcrossTheWholeRange() {
        Material inconel = Materials.material("inconel");
        Set<BigDecimal> values = Stream.of(Quality.values())
                .filter(q -> q != Quality.KV100)
                .map(q -> inconel.factorAt(q).stripTrailingZeros())
                .collect(Collectors.toSet());
        assertThat(values).containsExactly(new BigDecimal("1.3"));
    }

    @Test
    void stainlessReadsAsSteelButIsASeparateEntry() {
        assertThat(Materials.material("stainless_304").factorAt(Quality.IR192)).isEqualByComparingTo("1");
        assertThat(Materials.material("stainless_304")).isNotSameAs(STEEL);
        assertThat(Materials.material("stainless_304").densityKgM3()).isEqualTo(8000);
    }

    @Test
    void ferrousIsAPropertyOfTheMaterialNotTheFactor() {
        assertThat(STEEL.isFerrous()).isTrue();
        assertThat(Materials.material("stainless_316").isFerrous()).isTrue();
        assertThat(Materials.material("aluminium").isFerrous()).isFalse();
        assertThat(Materials.material("inconel").isFerrous()).isFalse();
    }

    @Test
    void aNegativeThicknessIsRefusedBeforeTheLookup() {
        assertThatThrownBy(() -> STEEL.steelEquivalent(new Length(-1), Quality.IR192))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCatalogueIsClosed() {
        assertThatThrownBy(() -> Materials.material("unobtainium")).isInstanceOf(ConfigurationError.class);
        List<String> keys = Materials.knownMaterials();
        assertThat(keys).contains("carbon_steel");
        assertThat(keys).isSorted();
        assertThat(keys).hasSize(12);
    }

    @Test
    void aMaterialPrintsAsTheNameAWelderWouldUse() {
        assertThat(Materials.material("aluminium_2024")).hasToString("aluminium 2024");
        assertThat(Quality.MV4_25).hasToString("4 to 25 MV");
        assertThat(STEEL).hasToString("carbon steel");
    }
}
