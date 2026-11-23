package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static io.github.rkaushikdev.swagebay.sources.Devices.collimator;
import static io.github.rkaushikdev.swagebay.sources.Devices.deviceModel;
import static io.github.rkaushikdev.swagebay.sources.Devices.devicesFor;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceTest {

    private static BigDecimal degrees(long value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void aDeviceIsCertifiedForOneIsotopeAndOneCapacity() {
        DeviceModel delta = deviceModel("880d");
        assertThat(delta.isotope()).isSameAs(IR192);
        assertThat(delta.capacity()).isEqualTo(curie(130));
        assertThat(delta.accepts(IR192, curie(100))).isTrue();
        assertThat(delta.accepts(IR192, curie(140))).isFalse();
        assertThat(delta.accepts(CO60, curie(10))).isFalse();
    }

    @Test
    void loadingTheWrongIsotopeIsRefusedByName() {
        DeviceModel delta = deviceModel("880d");
        assertThatThrownBy(() -> delta.checkLoad(SE75, curie(50)))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> {
                    assertThat(caught.context().get("certified_for")).isEqualTo("ir192");
                    assertThat(caught.context().get("offered")).isEqualTo("se75");
                });
    }

    @Test
    void overloadingADeviceIsRefusedWithBothNumbers() {
        DeviceModel delta = deviceModel("880d");
        assertThatThrownBy(() -> delta.checkLoad(IR192, curie(150)))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> {
                    assertThat(caught.context().get("capacity_ci")).isEqualTo("130");
                    assertThat(caught.context().get("offered_ci")).isEqualTo("150");
                });
        delta.checkLoad(IR192, curie(130));
    }

    @Test
    void theCertificateRatesScaleWithWhatIsActuallyInTheBox() {
        DeviceModel delta = deviceModel("880d");
        assertThat(delta.metreRateAtCapacity()).isEqualTo(usvPerHour(18));
        assertThat(delta.metreRateWith(curie(65))).isEqualTo(usvPerHour(9));
        assertThat(delta.surfaceRateWith(curie(130))).isEqualTo(delta.surfaceRateAtCapacity());
    }

    @Test
    void anEmptyDeviceReadsNothing() {
        DeviceModel delta = deviceModel("880d");
        assertThat(delta.metreRateWith(curie(0)).nsvPerHour()).isZero();
    }

    @Test
    void devicesForAnIsotopeComeBackBiggestFirst() {
        assertThat(devicesFor(IR192)).extracting(DeviceModel::key).containsExactly("880s", "880e", "880d", "tsi3");
        assertThat(devicesFor(SE75)).extracting(DeviceModel::key).containsExactly("spec150", "880e");
        assertThat(devicesFor(CO60)).extracting(DeviceModel::key).containsExactly("tk100");
    }

    @Test
    void theCobaltDeviceIsTheOneNobodyWantsToCarry() {
        assertThat(deviceModel("tk100").massKg())
                .isGreaterThan(Decimals.multiply(deviceModel("880d").massKg(), BigDecimal.valueOf(5)));
        assertThat(deviceModel("spec150").massKg())
                .isLessThan(deviceModel("880d").massKg());
    }

    @Test
    void guideTubeLengthIsPartOfTheCertificate() {
        assertThat(deviceModel("880d").maxGuideTube()).isEqualTo(Length.fromM(12));
        assertThat(deviceModel("tk100").maxGuideTube()).isEqualTo(Length.fromM(6));
    }

    @Test
    void aCollimatorOpensASectorAndShieldsTheRest() {
        Collimator cup = collimator("w60");
        assertThat(cup.covers(degrees(0))).isTrue();
        assertThat(cup.covers(degrees(30))).isTrue();
        assertThat(cup.covers(degrees(31))).isFalse();
        assertThat(cup.factorAt(degrees(0))).isEqualByComparingTo("1");
        assertThat(cup.factorAt(degrees(90))).isEqualByComparingTo("0.06");
    }

    @Test
    void bearingsWrapTheWayACompassDoes() {
        Collimator cup = collimator("w90");
        assertThat(cup.covers(degrees(350))).isTrue();
        assertThat(cup.covers(degrees(-40))).isTrue();
        assertThat(cup.covers(degrees(180))).isFalse();
        assertThat(cup.factorAt(degrees(359))).isEqualByComparingTo("1");
    }

    @Test
    void noCollimatorIsAnEntryInTheTableNotANone() {
        Collimator bare = collimator("none");
        assertThat(bare.apertureDegrees()).isEqualTo(360);
        assertThat(bare.factorAt(degrees(180))).isEqualByComparingTo("1");
        assertThat(bare.massKg()).isEqualByComparingTo("0");
    }

    @Test
    void aNonsenseCollimatorIsRefusedAtConstruction() {
        assertThatThrownBy(() -> new Collimator("bad", "bad", "tungsten", 0, new BigDecimal("0.1"), BigDecimal.ONE))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Collimator("bad", "bad", "tungsten", 60, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Collimator("bad", "bad", "tungsten", 60, new BigDecimal("1.5"), BigDecimal.ONE))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void bothCataloguesAreClosed() {
        assertThat(Devices.knownDevices()).containsExactly("880d", "880e", "880s", "spec150", "tk100", "tsi3");
        assertThat(Devices.knownCollimators()).containsExactly("du45", "none", "w60", "w90");
        assertThatThrownBy(() -> deviceModel("880z")).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> collimator("lead pipe")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theEliteIsCertifiedForSeleniumAsWellAsIridium() {
        DeviceModel elite = deviceModel("880e");
        assertThat(elite.certifications())
                .extracting(DeviceModel.Certification::isotopeKey)
                .containsExactly("ir192", "se75");
        assertThat(elite.isotopeKey()).isEqualTo("ir192");
        assertThat(elite.isotope()).isSameAs(IR192);
        assertThat(elite.capacity()).isEqualTo(curie(150));
        assertThat(elite.accepts(SE75, curie(80))).isTrue();
        assertThat(elite.accepts(SE75, curie(81))).isFalse();
        assertThat(elite.accepts(IR192, curie(150))).isTrue();
        assertThat(elite.accepts(CO60, curie(1))).isFalse();
    }

    @Test
    void eachIsotopeIsHeldToItsOwnLineOfTheCertificate() {
        DeviceModel elite = deviceModel("880e");
        elite.checkLoad(SE75, curie(80));
        assertThatThrownBy(() -> elite.checkLoad(SE75, curie(100)))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(
                                caught.context().get("capacity_ci"))
                        .isEqualTo("80"));
        assertThatThrownBy(() -> elite.checkLoad(CO60, curie(10)))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(
                                caught.context().get("certified_for"))
                        .isEqualTo("ir192, se75"));
    }

    @Test
    void theRateAtAMetreScalesOnTheLineForTheIsotopeLoaded() {
        DeviceModel elite = deviceModel("880e");
        assertThat(elite.metreRateWith(SE75, curie(40))).isEqualTo(usvPerHour("2.5"));
        assertThat(elite.metreRateWith(IR192, curie(75))).isEqualTo(elite.metreRateWith(curie(75)));
        assertThatThrownBy(() -> elite.metreRateWith(CO60, curie(1))).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aCertificateCoversAtLeastOneIsotopeAndEachOnce() {
        DeviceModel.Certification line = deviceModel("880d").certifications().get(0);
        assertThatThrownBy(() -> new DeviceModel("x", "x", "x", List.of(), BigDecimal.ONE, Length.fromM(1), "II"))
                .isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() ->
                        new DeviceModel("x", "x", "x", List.of(line, line), BigDecimal.ONE, Length.fromM(1), "II"))
                .isInstanceOf(ConfigurationError.class);
    }
}
