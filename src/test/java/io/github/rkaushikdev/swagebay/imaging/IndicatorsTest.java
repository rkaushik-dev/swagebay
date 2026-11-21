package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.thou;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorsTest {

    @Test
    void anIdentityNumberIsThousandthsOfAnInch() {
        assertThat(new HoleIndicator(30).thickness()).isEqualTo(thou(30));
        assertThat(new HoleIndicator(30).thickness()).isEqualTo(inch("0.030"));
        assertThat(new HoleIndicator(5).thickness()).isEqualTo(thou(5));
    }

    @Test
    void theHolesAreMultiplesOfThePlaqueThickness() {
        HoleIndicator plaque = new HoleIndicator(30);
        assertThat(plaque.holeDiameter(HoleSize.ONE_T)).isEqualTo(thou(30));
        assertThat(plaque.holeDiameter(HoleSize.TWO_T)).isEqualTo(thou(60));
        assertThat(plaque.holeDiameter(HoleSize.FOUR_T)).isEqualTo(thou(120));
    }

    @Test
    void aThinPlaqueHitsTheDrillingFloor() {
        HoleIndicator thin = new HoleIndicator(5);
        assertThat(thin.holeDiameter(HoleSize.ONE_T)).isEqualTo(thou(10));
        assertThat(thin.holeDiameter(HoleSize.TWO_T)).isEqualTo(thou(10));
        assertThat(thin.holeDiameter(HoleSize.FOUR_T)).isEqualTo(thou(20));
    }

    @Test
    void thicknessSensitivityIsThePlainPercentage() {
        HoleIndicator plaque = new HoleIndicator(20);
        assertThat(plaque.thicknessSensitivityAt(inch(1))).isEqualByComparingTo("2");
        assertThat(plaque.thicknessSensitivityAt(inch("0.5"))).isEqualByComparingTo("4");
    }

    @Test
    void equivalentSensitivityReadsTheHoleAsWellAsThePlaque() {
        HoleIndicator plaque = new HoleIndicator(20);
        BigDecimal twoT = plaque.equivalentSensitivityAt(inch(1), HoleSize.TWO_T);
        BigDecimal fourT = plaque.equivalentSensitivityAt(inch(1), HoleSize.FOUR_T);
        assertThat(fourT).isGreaterThan(twoT);
        assertThat(twoT).isStrictlyBetween(new BigDecimal("1.9"), new BigDecimal("2.1"));
    }

    @Test
    void aThinPlaqueWithABigHoleCanMatchAThickOneWithASmallHole() {
        BigDecimal thin = new HoleIndicator(20).equivalentSensitivityAt(inch(1), HoleSize.FOUR_T);
        BigDecimal thick = new HoleIndicator(40).equivalentSensitivityAt(inch(1), HoleSize.ONE_T);
        assertThat(thin.subtract(thick).abs()).isLessThan(new BigDecimal("0.6"));
    }

    @Test
    void sensitivityNeedsSomethingToHaveBeenPenetrated() {
        assertThatThrownBy(() -> new HoleIndicator(20).thicknessSensitivityAt(Length.ZERO))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new HoleIndicator(20).equivalentSensitivityAt(Length.ZERO, HoleSize.TWO_T))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new WireIndicator(10).sensitivityAt(Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theHoleLabelsAreWhatGetsWrittenOnTheReport() {
        assertThat(HoleSize.TWO_T).hasToString("2T");
        assertThat(HoleSize.FOUR_T.label()).isEqualTo("4T");
        assertThat(new HoleIndicator(30)).hasToString("hole 30");
        assertThat(new HoleIndicator(30).label()).isEqualTo("30");
    }

    @Test
    void anUnpublishedIdentityIsRefused() {
        assertThatThrownBy(() -> new HoleIndicator(11)).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new HoleIndicator(0)).isInstanceOf(ConfigurationError.class);
        assertThat(Indicators.holeIdentities()).contains(12);
    }

    @Test
    void theNextPlaqueUpIsTheNextPublishedOneNotTheNextInteger() {
        assertThat(Indicators.nextThickerIdentity(50)).isEqualTo(60);
        assertThat(Indicators.nextThickerIdentity(160)).isEqualTo(200);
        assertThatThrownBy(() -> Indicators.nextThickerIdentity(280)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void wireDiametersAreTheIsoSeries() {
        assertThat(new WireIndicator(1).diameter()).isEqualTo(mm("3.20"));
        assertThat(new WireIndicator(10).diameter()).isEqualTo(mm("0.40"));
        assertThat(new WireIndicator(19).diameter()).isEqualTo(mm("0.05"));
        assertThat(new WireIndicator(13)).hasToString("W13");
    }

    @Test
    void aHigherWireNumberIsAThinnerWire() {
        List<Length> diameters = Indicators.wireNumbers().stream()
                .map(n -> new WireIndicator(n).diameter())
                .toList();
        assertThat(diameters).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void wireSensitivityIsTheDiameterOverWhatWasPenetrated() {
        assertThat(new WireIndicator(6).sensitivityAt(mm(50))).isEqualByComparingTo("2");
        assertThat(new WireIndicator(9).sensitivityAt(mm(25))).isEqualByComparingTo("2");
    }

    @Test
    void theSetsOverlapSoACrewCanCarryTwoAndCoverTheRange() {
        assertThat(Indicators.wireSet("A")).extracting(WireIndicator::number).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(Indicators.wireSet("b")).extracting(WireIndicator::number).containsExactly(6, 7, 8, 9, 10, 11, 12);
        assertThat(Indicators.wireSetsContaining(6)).containsExactly("A", "B");
        assertThat(Indicators.wireSetsContaining(13)).containsExactly("C", "D");
        assertThat(Indicators.wireSetsContaining(1)).containsExactly("A");
    }

    @Test
    void anUnknownWireOrSetIsRefused() {
        assertThatThrownBy(() -> new WireIndicator(20)).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> Indicators.wireSet("E")).isInstanceOf(ConfigurationError.class);
    }
}
