package io.github.rkaushikdev.swagebay.technique;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.SourceFixtures.ASSAY;
import static io.github.rkaushikdev.swagebay.testing.SourceFixtures.SHOT_DAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.exposure.Catalogue;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.materials.Material;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.GeometryFixtures;
import io.github.rkaushikdev.swagebay.testing.SourceFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SteelChartTest {

    private static final BigDecimal DENSITY = new BigDecimal("2.0");

    private static Joint sixInch(String material) {
        return PipeSchedule.girthWeld("6", "80", Materials.material(material), mm("2.0"));
    }

    private static TechniqueSheet sheet(Joint joint, SealedSource source) {
        return Techniques.planTechnique(
                TechniqueRequest.builder(joint, source, GeometryFixtures.onD7(), Films.film("d7"), SHOT_DAY)
                        .shot(ShotType.DWSI)
                        .build());
    }

    @Test
    void aMaterialWithItsOwnChartIsReadOffIt() {
        Techniques.ChartReading reading =
                Techniques.chartReading(Isotopes.IR192, Materials.material("aluminium"), DENSITY, mm(20));
        assertThat(reading.chart().materialKey()).isEqualTo("aluminium");
        assertThat(reading.thickness()).isEqualTo(mm(20));
        assertThat(reading.note()).isEmpty();
    }

    @Test
    void copperIsReadOffTheSteelChartAtItsEquivalentThickness() {
        Material copper = Materials.material("copper");
        Techniques.ChartReading reading = Techniques.chartReading(Isotopes.IR192, copper, DENSITY, mm(20));
        assertThat(reading.chart().materialKey()).isEqualTo("carbon_steel");
        assertThat(reading.thickness()).isEqualTo(mm(22));
        assertThat(reading.note())
                .contains("copper read off the carbon steel chart as 22.00 mm of steel, factor 1.1 at Ir-192");
    }

    @Test
    void aCopperWeldTakesLongerThanTheSameSteelWeld() {
        SealedSource source = SourceFixtures.iridiumSource();
        TechniqueSheet steel = sheet(GeometryFixtures.sixInchWeld(), source);
        TechniqueSheet copper = sheet(sixInch("copper"), source);
        assertThat(copper.exposure().thickness()).isGreaterThan(steel.exposure().thickness());
        assertThat(copper.exposure().exposure()).isGreaterThan(steel.exposure().exposure());
        assertThat(copper.warnings()).anyMatch(warning -> warning.startsWith("copper read off the carbon steel chart"));
        assertThat(steel.warnings()).noneMatch(warning -> warning.contains("read off the"));
    }

    @Test
    void stainlessReadsAsSteelButTheSheetSaysSo() {
        SealedSource source = SourceFixtures.iridiumSource();
        TechniqueSheet steel = sheet(GeometryFixtures.sixInchWeld(), source);
        TechniqueSheet stainless = sheet(sixInch("stainless_304"), source);
        assertThat(stainless.exposure().exposure()).isEqualTo(steel.exposure().exposure());
        assertThat(stainless.warnings())
                .anyMatch(warning -> warning.endsWith("as 25.94 mm of steel, factor 1 at Ir-192"));
    }

    @Test
    void anIsotopeWithNoEquivalenceColumnCannotBorrowTheSteelChart() {
        SealedSource selenium = new SealedSource(
                new SourceCertificate("SE-0101", Isotopes.SE75, curie(80), ASSAY, "QSA", "S-1", mm(3), mm(3)));
        assertThat(Catalogue.findChart("se75", "carbon_steel", "d7", DENSITY)).isPresent();
        assertThatThrownBy(() -> Techniques.chartReading(Isotopes.SE75, Materials.material("copper"), DENSITY, mm(8)))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("no chart has been established");
        assertThatThrownBy(() -> sheet(sixInch("copper"), selenium)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void noSteelChartAtThatDensityIsStillARefusal() {
        assertThatThrownBy(() -> Techniques.chartReading(
                        Isotopes.IR192, Materials.material("copper"), new BigDecimal("3.0"), mm(20)))
                .isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(
                        () -> Techniques.chartReading(Isotopes.IR192, Materials.STEEL, new BigDecimal("3.0"), mm(20)))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theCatalogueFindsAChartWithoutThrowing() {
        assertThat(Catalogue.findChart("ir192", "carbon_steel", "d5", DENSITY))
                .hasValueSatisfying(chart -> assertThat(chart.key()).isEqualTo("ir192-steel-d5-2.0"));
        assertThat(Catalogue.findChart("ir192", "inconel", "d7", DENSITY)).isEmpty();
    }
}
