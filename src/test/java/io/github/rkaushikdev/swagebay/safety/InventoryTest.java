package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Devices.deviceModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 3, 1, 0, 0);
    private static final LocalDate TODAY = LocalDate.of(2024, 6, 6);

    private static SealedSource source() {
        SourceCertificate certificate =
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3));
        SealedSource held = new SealedSource(certificate);
        held.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        return held;
    }

    private static Inventory stocked() {
        Inventory inventory = new Inventory();
        inventory.addSource(source());
        inventory.addDevice(new DeviceUnit("880D-221", deviceModel("880d")));
        return inventory;
    }

    private static Trip trip(String crew, String site, LocalDate wentOut) {
        return new Trip("IR-5512", "880D-221", crew, site, wentOut);
    }

    @Test
    void aSourceAtTheShopReadsAsBeingAtTheShop() {
        assertThat(stocked().locationOf("IR-5512", TODAY)).isEqualTo("shop");
    }

    @Test
    void aSourceOutOnAJobReadsAsBeingAtTheSite() {
        Inventory inventory = stocked();
        inventory.sendOut(trip("0142", "Come By Chance", LocalDate.of(2024, 6, 3)));
        assertThat(inventory.locationOf("IR-5512", TODAY)).isEqualTo("Come By Chance");
        assertThat(inventory.openTrip("IR-5512")).isPresent();
    }

    @Test
    void aSourceThatCameBackReadsAsTheShopAgain() {
        Inventory inventory = stocked();
        inventory.sendOut(trip("0142", "site", LocalDate.of(2024, 6, 3)));
        inventory.bringBack("IR-5512", LocalDate.of(2024, 6, 5));
        assertThat(inventory.locationOf("IR-5512", TODAY)).isEqualTo("shop");
        assertThat(inventory.locationOf("IR-5512", LocalDate.of(2024, 6, 4))).isEqualTo("site");
    }

    @Test
    void aSourceCannotGoOutTwice() {
        Inventory inventory = stocked();
        inventory.sendOut(trip("0142", "site", LocalDate.of(2024, 6, 3)));
        assertThatThrownBy(() -> inventory.sendOut(trip("0201", "other", LocalDate.of(2024, 6, 4))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSourceThatIsNotOutCannotComeBack() {
        Inventory inventory = stocked();
        assertThatThrownBy(() -> inventory.bringBack("IR-5512", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSourceCannotComeBackBeforeItWentOut() {
        Inventory inventory = stocked();
        inventory.sendOut(trip("0142", "site", LocalDate.of(2024, 6, 3)));
        assertThatThrownBy(() -> inventory.bringBack("IR-5512", LocalDate.of(2024, 6, 1)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Trip(
                        "IR-5512", "880D-221", "0142", "site", LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSourceOrDeviceNotOnTheInventoryCannotBeSentOut() {
        Inventory inventory = stocked();
        assertThatThrownBy(() -> inventory.sendOut(new Trip("IR-9999", "880D-221", "0142", "site", TODAY)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> inventory.sendOut(new Trip("IR-5512", "880D-999", "0142", "site", TODAY)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void nothingGoesOnTheInventoryTwice() {
        Inventory inventory = stocked();
        assertThatThrownBy(() -> inventory.addSource(source())).isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> inventory.addDevice(new DeviceUnit("880D-221", deviceModel("880d"))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSourceNeverSightedAndNeverOutIsUnaccountedFor() {
        Inventory inventory = stocked();
        assertThat(inventory.unaccountedOn(TODAY)).containsExactly("IR-5512");
    }

    @Test
    void aRecentSightingAccountsForIt() {
        Inventory inventory = stocked();
        inventory.addSighting(new Sighting("IR-5512", LocalDate.of(2024, 5, 1), "0142", "shop"));
        assertThat(inventory.unaccountedOn(TODAY)).isEmpty();
    }

    @Test
    void aSightingOlderThanTheQuarterDoesNot() {
        Inventory inventory = stocked();
        LocalDate stale = TODAY.minusDays(Inventory.INTERVAL.toDays()).minusDays(1);
        inventory.addSighting(new Sighting("IR-5512", stale, "0142", "shop"));
        assertThat(inventory.unaccountedOn(TODAY)).containsExactly("IR-5512");
    }

    @Test
    void aSourceOutOnAJobIsAccountedForWithoutASighting() {
        Inventory inventory = stocked();
        inventory.sendOut(trip("0142", "site", LocalDate.of(2024, 6, 3)));
        assertThat(inventory.unaccountedOn(TODAY)).isEmpty();
    }

    @Test
    void theLatestSightingIsTheOneThatCounts() {
        Inventory inventory = stocked();
        inventory.addSighting(new Sighting("IR-5512", LocalDate.of(2024, 1, 5), "0142", "shop"));
        inventory.addSighting(new Sighting("IR-5512", LocalDate.of(2024, 5, 5), "0201", "shop"));
        assertThat(inventory.lastSighting("IR-5512").orElseThrow().seenBy()).isEqualTo("0201");
        assertThat(inventory.unaccountedOn(TODAY)).isEmpty();
    }

    @Test
    void exposuresAreTotalledByCrew() {
        Inventory inventory = stocked();
        inventory.sendOut(new Trip(
                "IR-5512", "880D-221", "0142", "site", LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 2), 24));
        inventory.sendOut(new Trip(
                "IR-5512", "880D-221", "0142", "site", LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 4), 18));
        assertThat(inventory.exposuresBy("0142")).isEqualTo(42);
        assertThat(inventory.exposuresBy("0201")).isZero();
    }

    @Test
    void aDeviceAndASightingBothNeedSigning() {
        assertThatThrownBy(() -> new DeviceUnit("  ", deviceModel("880d"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Sighting("IR-5512", TODAY, "   ", "shop")).isInstanceOf(ValidationError.class);
        assertThat(new DeviceUnit("880D-221", deviceModel("880d"))).hasToString("880 Delta 880D-221");
    }
}
