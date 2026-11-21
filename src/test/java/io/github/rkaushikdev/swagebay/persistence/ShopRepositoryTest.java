package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.testing.TestDatabase.assertTheSchemaRefuses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.safety.DeviceDay;
import io.github.rkaushikdev.swagebay.safety.Trip;
import io.github.rkaushikdev.swagebay.safety.Use;
import io.github.rkaushikdev.swagebay.safety.UtilisationLog;
import io.github.rkaushikdev.swagebay.sources.Exchange;
import io.github.rkaushikdev.swagebay.sources.Loading;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShopRepositoryTest {

    private static final LocalDate SWAP = LocalDate.of(2025, 8, 13);
    private static final LocalDate MONDAY = LocalDate.of(2025, 8, 18);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private static Exchange anExchange(String removed, String installed, String device, LocalDate on) {
        return new Exchange(
                device, Optional.ofNullable(removed), Optional.ofNullable(installed), on, "0142", "QSA-88213");
    }

    private static Exchange anExchange() {
        return anExchange(null, "IR-5512", "D-77", SWAP);
    }

    private static Exchange onDevice(String device, String installed) {
        return anExchange(null, installed, device, SWAP);
    }

    private static Exchange later(String removed, String installed) {
        return anExchange(removed, installed, "D-77", SWAP.plusDays(180));
    }

    private static Use aUse(LocalDate on, String site, List<String> crew) {
        return new Use(on, "D-77", "IR-5512", site, crew, 14);
    }

    private static Use aUse() {
        return aUse(MONDAY, "Come By Chance", List.of("0142"));
    }

    @Test
    void anExchangeComesBackTheWayItWentIn() {
        List<Exchange> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            found = work.shop().exchangesFor("D-77");
        }
        assertThat(found).hasSize(1);
        assertThat(found.get(0).installedSerial()).contains("IR-5512");
        assertThat(found.get(0).shipment()).isEqualTo("QSA-88213");
    }

    @Test
    void anotherDevicesExchangesAreNotInThisHistory() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            work.shop().recordExchange(onDevice("D-78", "IR-5512"));
            assertThat(work.shop().exchangesFor("D-77")).hasSize(1);
        }
    }

    @Test
    void oneShipmentCanCarryBothHalvesOfASwap() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            work.shop().recordExchange(onDevice("D-78", "IR-6000"));
            assertThat(work.shop().exchangesOn("QSA-88213")).hasSize(2);
        }
    }

    @Test
    void theLoadingIsReplayedFromTheExchanges() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            assertThat(work.shop().loading().sourceIn("D-77")).contains("IR-5512");
        }
    }

    @Test
    void anUnloadLeavesTheDeviceEmpty() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            work.shop().recordExchange(later("IR-5512", null));
            assertThat(work.shop().loading().sourceIn("D-77")).isEmpty();
        }
    }

    @Test
    void aSwapLeavesTheNewSourceIn() {
        Loading loading;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(anExchange());
            work.shop().recordExchange(later("IR-5512", "IR-6000"));
            loading = work.shop().loading();
        }
        assertThat(loading.sourceIn("D-77")).contains("IR-6000");
        assertThat(loading.deviceHolding("IR-6000")).contains("D-77");
    }

    @Test
    void theReplayReadsInDateOrderWhateverOrderItWasWritten() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordExchange(later("IR-5512", "IR-6000"));
            work.shop().recordExchange(anExchange());
            assertThat(work.shop().loading().sourceIn("D-77")).contains("IR-6000");
        }
    }

    @Test
    void aUseComesBackWithItsCrew() {
        List<Use> found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse(MONDAY, "Come By Chance", List.of("0142", "0201")));
            found = work.shop().usesBetween(MONDAY, TUESDAY);
        }
        assertThat(found.get(0).radiographers()).containsExactly("0142", "0201");
        assertThat(found.get(0).exposures()).isEqualTo(14);
    }

    @Test
    void aWindowThatEndsBeforeTheEntryDoesNotIncludeIt() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse(TUESDAY, "Come By Chance", List.of("0142")));
            assertThat(work.shop().usesBetween(MONDAY, MONDAY)).isEmpty();
        }
    }

    /** The row is written as it is recorded, so the second one is refused there or at the flush. */
    @Test
    void theSameDeviceAtTheSameSiteOnTheSameDayGoesInOnce() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            assertTheSchemaRefuses(() -> {
                work.shop().recordUse(aUse());
                work.flush();
            });
        }
    }

    @Test
    void theSameDeviceAtTwoSitesInOneDayIsTwoEntries() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            work.shop().recordUse(aUse(MONDAY, "Point Tupper", List.of("0142")));
            assertThat(work.shop().usesBetween(MONDAY, MONDAY)).hasSize(2);
        }
    }

    @Test
    void theLogAnswersTheDomainQuestions() {
        UtilisationLog log;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            log = work.shop().logBetween(MONDAY, TUESDAY);
        }
        Trip trip = new Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY);
        assertThat(log.unloggedDays(List.of(trip))).containsExactly(new DeviceDay("D-77", TUESDAY));
        assertThat(log.daysUsed("D-77", MONDAY, TUESDAY)).isEqualTo(1);
    }

    @Test
    void oneEntryCanBeFoundByDeviceDayAndSite() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            work.flush();
            assertThat(work.shop().useOn("D-77", MONDAY, "Come By Chance"))
                    .hasValueSatisfying(use -> assertThat(use.exposures()).isEqualTo(14));
            assertThat(work.shop().useOn("D-77", TUESDAY, "Come By Chance")).isEmpty();
        }
    }

    @Test
    void exposuresCanBeToppedUpWhenTheFilmsAreCounted() {
        Use found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            work.flush();
            found = work.shop().addExposures("D-77", MONDAY, "Come By Chance", 6);
        }
        assertThat(found.exposures()).isEqualTo(20);
    }

    @Test
    void toppingUpADayNobodyLoggedIsRefused() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            assertThatThrownBy(() -> work.shop().addExposures("D-77", MONDAY, "Come By Chance", 6))
                    .isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void aNegativeTopUpIsRefused() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.shop().recordUse(aUse());
            work.flush();
            assertThatThrownBy(() -> work.shop().addExposures("D-77", MONDAY, "Come By Chance", -1))
                    .isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void bothRegistersSurviveTheTransactionThatWroteThem() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            work.shop().recordExchange(anExchange());
            work.shop().recordUse(aUse());
        });
        try (UnitOfWork work = made.unitOfWork()) {
            assertThat(work.shop().loading().sourceIn("D-77")).contains("IR-5512");
            assertThat(work.shop().usesBetween(MONDAY, TUESDAY)).hasSize(1);
        }
    }
}
