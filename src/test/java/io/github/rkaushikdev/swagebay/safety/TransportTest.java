package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TransportTest {

    private static TransportPackage aPackage(
            String reference,
            Isotope isotope,
            Activity activity,
            PackageType packageType,
            String surface,
            String metre) {
        return new TransportPackage(reference, isotope, activity, packageType, usvPerHour(surface), usvPerHour(metre));
    }

    private static TransportPackage aPackage(PackageType packageType) {
        return aPackage("880D-221", IR192, curie(90), packageType, "1800", "18");
    }

    private static TransportPackage aPackage() {
        return aPackage(PackageType.TYPE_B);
    }

    private static TransportPackage reading(String reference, String surface, String metre) {
        return aPackage(reference, IR192, curie(90), PackageType.TYPE_B, surface, metre);
    }

    @Test
    void theTransportIndexIsMilliremAtAMetreRoundedUp() {
        assertThat(Transport.transportIndex(usvPerHour(18))).isEqualByComparingTo("1.8");
        assertThat(Transport.transportIndex(usvPerHour(1))).isEqualByComparingTo("0.1");
        assertThat(Transport.transportIndex(usvPerHour("10.1"))).isEqualByComparingTo("1.1");
        assertThat(Transport.transportIndex(DoseRate.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void theIndexNeverRoundsDown() {
        assertThat(Transport.transportIndex(usvPerHour("10.01"))).isEqualByComparingTo("1.1");
        assertThat(Transport.transportIndex(usvPerHour("0.01"))).isEqualByComparingTo("0.1");
    }

    @Test
    void aWorkingProjectorCarriesAYellowThreeLabel() {
        assertThat(aPackage().label()).isSameAs(Label.YELLOW_III);
    }

    @Test
    void aQuietPackageCarriesAWhiteOne() {
        TransportPackage quiet = reading("880D-221", "4", "0");
        assertThat(quiet.label()).isSameAs(Label.WHITE_I);
    }

    @Test
    void theSurfaceReadingCanBeWhatPushesTheLabelUp() {
        assertThat(Transport.labelFor(usvPerHour(400), usvPerHour(5))).isSameAs(Label.YELLOW_II);
        assertThat(Transport.labelFor(usvPerHour(600), usvPerHour(5))).isSameAs(Label.YELLOW_III);
    }

    @Test
    void theMetreReadingCanBeWhatPushesItUpInstead() {
        assertThat(Transport.labelFor(usvPerHour(4), usvPerHour(0))).isSameAs(Label.WHITE_I);
        assertThat(Transport.labelFor(usvPerHour(4), usvPerHour(5))).isSameAs(Label.YELLOW_II);
        assertThat(Transport.labelFor(usvPerHour(4), usvPerHour(50))).isSameAs(Label.YELLOW_III);
    }

    @Test
    void aPackageOverEveryLimitHasNoLabelAtAll() {
        assertThatThrownBy(() -> Transport.labelFor(usvPerHour(3000), usvPerHour(200)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(
                                caught.context().get("transport_index"))
                        .isEqualTo("20.0"));
    }

    @Test
    void theA1LimitsAreThePublishedOnes() {
        assertThat(Transport.a1Limit(IR192)).isEqualTo(Activity.fromTbq(1));
        assertThat(Transport.a1Limit(CO60)).isEqualTo(Activity.fromTbq("0.4"));
        assertThat(Transport.a1Limit(SE75)).isEqualTo(Activity.fromTbq(3));
    }

    @Test
    void anIsotopeWithNoPublishedLimitIsRefused() {
        Isotope madeUp = new Isotope(
                "xx99",
                "x",
                "X-99",
                BigDecimal.TEN,
                new BigDecimal("0.5"),
                100,
                200,
                IR192.steelMin(),
                IR192.steelMax(),
                Optional.empty());
        assertThatThrownBy(() -> Transport.a1Limit(madeUp)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aWorkingProjectorLoadIsOverTheTypeALimitAndNeedsATypeB() {
        assertThat(curie(90)).isGreaterThan(Transport.a1Limit(IR192));
        assertThat(aPackage().faults()).isEmpty();
        TransportPackage asTypeA = aPackage(PackageType.TYPE_A);
        assertThat(asTypeA.faults()).hasSize(1);
        assertThat(asTypeA.faults().get(0)).contains("A1 limit");
    }

    @Test
    void aSmallSourceTravelsTypeAQuiteHappily() {
        TransportPackage small = aPackage("880D-221", IR192, curie(20), PackageType.TYPE_A, "40", "4");
        assertThat(curie(20)).isLessThan(Transport.a1Limit(IR192));
        assertThat(small.faults()).isEmpty();
    }

    @Test
    void cobaltHasTheTightestLimitOfTheLot() {
        TransportPackage heavy = aPackage("880D-221", CO60, curie(20), PackageType.TYPE_A, "1800", "18");
        assertThat(heavy.faults().get(0)).contains("A1 limit");
    }

    @Test
    void aRadiographySourceNeverTravelsExcepted() {
        TransportPackage excepted = aPackage(PackageType.EXCEPTED);
        assertThat(excepted.faults().get(0)).contains("never travels as an excepted package");
    }

    @Test
    void aPackageThatCannotBeLabelledSaysSoInItsFaults() {
        TransportPackage over = reading("880D-221", "3000", "200");
        assertThat(over.faults()).anyMatch(fault -> fault.contains("over the limits for every label"));
    }

    @Test
    void aShipmentTotalsTheIndices() {
        Shipment load = new Shipment(List.of(aPackage(), reading("880S-114", "1800", "18")));
        assertThat(load.totalIndex()).isEqualByComparingTo("3.6");
        assertThat(load.overVehicleCeiling()).isFalse();
        assertThat(load.faults()).isEmpty();
    }

    @Test
    void aShipmentOverTheVehicleCeilingIsTheRoadsideFinding() {
        TransportPackage hot = reading("880D-221", "1800", "300");
        Shipment load = new Shipment(IntStream.range(0, 2)
                .mapToObj(i -> reading("p" + i, "1800", "300"))
                .toList());
        assertThat(hot.transportIndex()).isEqualByComparingTo("30");
        assertThat(load.totalIndex()).isEqualByComparingTo("60");
        assertThat(load.overVehicleCeiling()).isTrue();
        assertThat(load.faults()).anyMatch(fault -> fault.contains("vehicle ceiling"));
        assertThat(Transport.VEHICLE_INDEX_CEILING).isEqualByComparingTo("50");
    }

    @Test
    void shipmentFaultsNameThePackageTheyCameFrom() {
        Shipment load = new Shipment(List.of(aPackage(PackageType.EXCEPTED)));
        assertThat(load.faults().get(0)).startsWith("880D-221: ");
    }

    @Test
    void aPackageNeedsAReference() {
        assertThatThrownBy(() -> reading("   ", "1800", "18")).isInstanceOf(ValidationError.class);
        assertThat(PackageType.TYPE_B).hasToString("Type B package");
        assertThat(Label.YELLOW_III).hasToString("Yellow-III");
    }
}
