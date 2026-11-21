package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShieldingTest {

    private static final BigDecimal TINY = new BigDecimal("1e-20");

    private static BigDecimal distanceFrom(BigDecimal value, String target) {
        return value.subtract(new BigDecimal(target)).abs();
    }

    @Test
    void oneHalfValueLayerHalvesTheRate() {
        Length hvl = Shielding.halfValueLayer(IR192, "lead");
        assertThat(hvl).isEqualTo(mm("4.8"));
        BigDecimal left = Shielding.transmission(IR192, "lead", hvl);
        assertThat(distanceFrom(left, "0.5")).isLessThan(TINY);
    }

    @Test
    void tenLayersCutItByAboutAThousand() {
        Length hvl = Shielding.halfValueLayer(IR192, "lead");
        BigDecimal left = Shielding.transmission(IR192, "lead", new Length(hvl.um() * 10));
        assertThat(left).isGreaterThan(new BigDecimal("0.0009")).isLessThan(new BigDecimal("0.001"));
    }

    @Test
    void noShieldLetsEverythingThrough() {
        assertThat(Shielding.transmission(IR192, "lead", Length.ZERO)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(Shielding.attenuated(IR192, "lead", Length.ZERO, mrPerHour(200)))
                .isEqualTo(mrPerHour(200));
    }

    @Test
    void cobaltNeedsMoreThanTwiceTheLeadIridiumDoes() {
        assertThat(Shielding.halfValueLayer(CO60, "lead"))
                .isGreaterThan(Shielding.halfValueLayer(IR192, "lead").times(2));
        assertThat(Shielding.halfValueLayer(SE75, "lead")).isLessThan(Shielding.halfValueLayer(IR192, "lead"));
    }

    @Test
    void aBarrierSizedForOneIsotopeUnderperformsForAnother() {
        Barrier barrier = new Barrier("lead", Shielding.halfValueLayer(IR192, "lead"));
        assertThat(distanceFrom(barrier.transmission(IR192), "0.5")).isLessThan(TINY);
        assertThat(barrier.transmission(CO60)).isGreaterThan(new BigDecimal("0.75"));
    }

    @Test
    void aTenthValueLayerIsAboutThreeAndAThirdHalfLayers() {
        Length tvl = Shielding.tenthValueLayer(IR192, "lead");
        assertThat(tvl.ratio(Shielding.halfValueLayer(IR192, "lead")))
                .isGreaterThan(new BigDecimal("3.3"))
                .isLessThan(new BigDecimal("3.4"));
        // the layer is stored to the micrometre, so the tenth is a tenth to five places
        assertThat(distanceFrom(Shielding.transmission(IR192, "lead", tvl), "0.1"))
                .isLessThan(new BigDecimal("1e-4"));
    }

    @Test
    void theLayersABarrierRepresents() {
        Barrier barrier = new Barrier("lead", mm("9.6"));
        assertThat(barrier.layers(IR192)).isEqualByComparingTo("2");
        assertThat(distanceFrom(barrier.transmission(IR192), "0.25")).isLessThan(TINY);
    }

    @Test
    void attenuatingARealReading() {
        DoseRate shielded = Shielding.attenuated(IR192, "lead", mm("4.8"), mrPerHour(200));
        assertThat(distanceFrom(shielded.mrPerHour(), "100")).isLessThan(new BigDecimal("0.01"));
    }

    @Test
    void theThicknessNeededForAFractionInvertsTheTransmission() {
        Length lead = Shielding.thicknessForTransmission(IR192, "lead", new BigDecimal("0.25"));
        assertThat(Math.abs(lead.um() - mm("9.6").um())).isLessThanOrEqualTo(1);
        assertThat(Shielding.thicknessForTransmission(IR192, "lead", BigDecimal.ONE))
                .isEqualTo(Length.ZERO);
    }

    @Test
    void aFractionOutsideTheUnitIntervalIsRefused() {
        for (String bad : List.of("0", "-0.5", "2")) {
            assertThatThrownBy(() -> Shielding.thicknessForTransmission(IR192, "lead", new BigDecimal(bad)))
                    .isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void theBarrierThatBringsARateDownToATarget() {
        Length needed = Shielding.thicknessForRate(IR192, "lead", mrPerHour(200), mrPerHour(2));
        assertThat(Shielding.attenuated(IR192, "lead", needed, mrPerHour(200))).isLessThanOrEqualTo(mrPerHour(2));
        assertThat(needed).isGreaterThan(mm(30));
    }

    @Test
    void noBarrierIsNeededWhenTheRateIsAlreadyUnder() {
        assertThat(Shielding.thicknessForRate(IR192, "lead", mrPerHour(1), mrPerHour(2)))
                .isEqualTo(Length.ZERO);
        assertThat(Shielding.thicknessForRate(IR192, "lead", mrPerHour(2), mrPerHour(2)))
                .isEqualTo(Length.ZERO);
    }

    @Test
    void aTargetOfNothingIsRefused() {
        assertThatThrownBy(() -> Shielding.thicknessForRate(IR192, "lead", mrPerHour(200), DoseRate.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void concreteTakesAnOrderOfMagnitudeMoreThanLead() {
        assertThat(Shielding.halfValueLayer(IR192, "concrete"))
                .isGreaterThan(Shielding.halfValueLayer(IR192, "lead").times(9));
        assertThat(Shielding.halfValueLayer(IR192, "water"))
                .isGreaterThan(Shielding.halfValueLayer(IR192, "concrete").times(3));
    }

    @Test
    void anUnlistedMaterialIsRefusedRatherThanEstimated() {
        assertThatThrownBy(() -> Shielding.halfValueLayer(IR192, "gypsum"))
                .isInstanceOfSatisfying(
                        ConfigurationError.class,
                        caught -> assertThat(caught.context().get("material")).isEqualTo("gypsum"));
        assertThat(Shielding.materialsFor(IR192)).isEqualTo(Shielding.SHIELD_MATERIALS);
    }

    @Test
    void aNegativeBarrierIsRefused() {
        assertThatThrownBy(() -> new Barrier("lead", new Length(-1))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Shielding.transmission(IR192, "lead", new Length(-1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aBarrierPrintsTheWayASurveyNoteReads() {
        assertThat(new Barrier("lead", mm("4.8"))).hasToString("4.80 mm of lead");
    }
}
