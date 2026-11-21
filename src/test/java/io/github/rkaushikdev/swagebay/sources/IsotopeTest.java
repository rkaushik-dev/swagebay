package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.isotope;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.isotopesCovering;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IsotopeTest {

    @Test
    void iridiumHasTheHalfLifeTheSourceCertificateQuotes() {
        assertThat(IR192.halfLifeDays()).isEqualByComparingTo("73.83");
        assertThat(IR192.halfLife()).isEqualTo(Duration.fromHours("1771.92"));
    }

    @Test
    void cobaltLivesYearsAndYtterbiumLivesAMonth() {
        assertThat(CO60.halfLifeDays()).isGreaterThan(new BigDecimal("1900"));
        assertThat(isotope("yb169").halfLifeDays()).isEqualByComparingTo("32.0");
        assertThat(CO60.halfLife()).isGreaterThan(IR192.halfLife());
    }

    @Test
    void theGammaConstantIsTheNumberTheSafetySideNeeds() {
        assertThat(IR192.gammaConstant()).isEqualByComparingTo("0.48");
        assertThat(CO60.gammaConstant()).isGreaterThan(IR192.gammaConstant());
        assertThat(SE75.gammaConstant()).isLessThan(IR192.gammaConstant());
    }

    @Test
    void thePracticalRangeIsNarrowerThanThePhysics() {
        assertThat(IR192.covers(mm(20))).isTrue();
        assertThat(IR192.covers(mm(12))).isTrue();
        assertThat(IR192.covers(mm(63))).isTrue();
        assertThat(IR192.covers(mm(8))).isFalse();
        assertThat(IR192.covers(mm(80))).isFalse();
    }

    @Test
    void seleniumTakesTheThinWallIridiumWashesOut() {
        assertThat(SE75.covers(mm(8))).isTrue();
        assertThat(IR192.covers(mm(8))).isFalse();
        assertThat(SE75.steelMax()).isLessThan(IR192.steelMax());
    }

    @Test
    void cobaltTakesTheVesselWallNothingElseWill() {
        assertThat(CO60.covers(mm(120))).isTrue();
        assertThat(IR192.covers(mm(120))).isFalse();
        assertThat(CO60.steelMin()).isEqualTo(mm(50));
    }

    @Test
    void askingWhichIsotopesCoverAWallReturnsThemSoftestFirst() {
        assertThat(isotopesCovering(mm(26))).extracting(Isotope::key).containsExactly("se75", "ir192", "cs137");
        assertThat(isotopesCovering(mm(20))).extracting(Isotope::key).containsExactly("se75", "ir192");
        assertThat(isotopesCovering(mm(150))).containsExactly(CO60);
        assertThat(isotopesCovering(mm(300))).isEmpty();
    }

    @Test
    void theE94HoleIsReportedAndNotPaperedOver() {
        assertThat(IR192.qualityForEquivalence()).isSameAs(Quality.IR192);
        assertThat(CO60.qualityForEquivalence()).isSameAs(Quality.CO60);
        assertThatThrownBy(SE75::qualityForEquivalence)
                .isInstanceOfSatisfying(
                        ConfigurationError.class,
                        caught -> assertThat(caught.context().get("isotope")).isEqualTo("se75"));
        assertThatThrownBy(() -> isotope("yb169").qualityForEquivalence()).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void cobaltIsTwoLinesAndIridiumIsASpread() {
        assertThat(IR192.isBroadSpectrum()).isTrue();
        assertThat(SE75.isBroadSpectrum()).isTrue();
        assertThat(CO60.isBroadSpectrum()).isFalse();
        assertThat(isotope("cs137").isBroadSpectrum()).isFalse();
    }

    @Test
    void caesiumIsASingleLineAtSixSixTwo() {
        Isotope cs = isotope("cs137");
        assertThat(cs.energyKevLow()).isEqualTo(662);
        assertThat(cs.energyKevHigh()).isEqualTo(662);
    }

    @Test
    void theCatalogueIsClosedAndSorted() {
        assertThat(Isotopes.knownIsotopes()).containsExactly("co60", "cs137", "ir192", "se75", "yb169");
        assertThatThrownBy(() -> isotope("ra226")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void anIsotopePrintsAsTheSymbolOnThePig() {
        assertThat(IR192).hasToString("Ir-192");
        assertThat(CO60).hasToString("Co-60");
        assertThat(IR192.name()).isEqualTo("iridium 192");
    }
}
