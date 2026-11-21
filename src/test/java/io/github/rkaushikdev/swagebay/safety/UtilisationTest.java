package io.github.rkaushikdev.swagebay.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.sources.Exchange;
import io.github.rkaushikdev.swagebay.sources.Loading;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UtilisationTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 6, 9);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private static Use aUse(LocalDate on, String device, String source, String site, List<String> crew, int exposures) {
        return new Use(on, device, source, site, crew, exposures);
    }

    private static Use aUse() {
        return aUse(MONDAY, "D-77", "IR-5512", "Come By Chance", List.of("0142"), 14);
    }

    private static Use on(LocalDate day) {
        return aUse(day, "D-77", "IR-5512", "Come By Chance", List.of("0142"), 14);
    }

    private static Use withCrew(String... crew) {
        return aUse(MONDAY, "D-77", "IR-5512", "Come By Chance", List.of(crew), 14);
    }

    private static Use withExposures(int exposures) {
        return aUse(MONDAY, "D-77", "IR-5512", "Come By Chance", List.of("0142"), exposures);
    }

    /** A loading record that has the device holding that source, as an exchange already made put it there. */
    private static Loading holding(String device, String source) {
        Loading loading = new Loading();
        loading.replay(new Exchange(device, Optional.empty(), Optional.of(source), MONDAY, "0142", "QSA-1"));
        return loading;
    }

    @Test
    void aUseNamesEverythingTheConditionAsksFor() {
        Use use = aUse();
        assertThat(use.usedOn()).isEqualTo(MONDAY);
        assertThat(use.deviceSerial()).isEqualTo("D-77");
        assertThat(use.sourceSerial()).isEqualTo("IR-5512");
        assertThat(use.radiographers()).containsExactly("0142");
    }

    @Test
    void aUseWithNobodyOnItIsRefused() {
        assertThatThrownBy(() -> withCrew()).isInstanceOf(ValidationError.class);
    }

    @Test
    void aBlankBadgeIsNotARadiographer() {
        assertThatThrownBy(() -> withCrew("0142", "  ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void theSameBadgeTwiceOnOneEntryIsRefused() {
        assertThatThrownBy(() -> withCrew("0142", "0142")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aUseWithNoDeviceOrNoSourceIsRefused() {
        assertThatThrownBy(() -> aUse(MONDAY, "  ", "IR-5512", "Come By Chance", List.of("0142"), 14))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> aUse(MONDAY, "D-77", "  ", "Come By Chance", List.of("0142"), 14))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aUseThatDoesNotSayWhereIsRefused() {
        assertThatThrownBy(() -> aUse(MONDAY, "D-77", "IR-5512", "   ", List.of("0142"), 14))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void negativeExposuresAreRefused() {
        assertThatThrownBy(() -> withExposures(-1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aDayOutWithNothingShotIsStillAUse() {
        Use use = withExposures(0);
        assertThat(use.wasAMobilisation()).isTrue();
        assertThat(aUse().wasAMobilisation()).isFalse();
    }

    @Test
    void anEntryPrintsTheWayAnInspectorReadsIt() {
        String text = withCrew("0201", "0142").describe();
        assertThat(text).contains("D-77 with IR-5512");
        assertThat(text).contains("14 exposures");
        assertThat(text).endsWith("0142, 0201");
    }

    @Test
    void theLogKeepsWhatWasRecorded() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        assertThat(log.forDevice("D-77", MONDAY, TUESDAY)).hasSize(1);
    }

    @Test
    void aUseNamingASourceTheDeviceWasNotHoldingIsRefused() {
        Loading loading = holding("D-77", "IR-5512");
        UtilisationLog log = new UtilisationLog();
        Use claimed = aUse(MONDAY, "D-77", "IR-9999", "Come By Chance", List.of("0142"), 14);
        assertThatThrownBy(() -> log.record(claimed, loading))
                .isInstanceOfSatisfying(
                        ConflictError.class,
                        caught -> assertThat(caught.context().get("holding")).isEqualTo("IR-5512"));
    }

    @Test
    void aUseAgreeingWithTheLoadingRecordGoesIn() {
        Loading loading = holding("D-77", "IR-5512");
        UtilisationLog log = new UtilisationLog();
        log.record(aUse(), loading);
        assertThat(log.uses()).hasSize(1);
    }

    @Test
    void aDeviceTheLoadingRecordKnowsNothingAboutIsNotSecondGuessed() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse(), new Loading());
        assertThat(log.uses()).hasSize(1);
    }

    @Test
    void oneDeviceOverAWindowReadsInDateOrder() {
        UtilisationLog log = new UtilisationLog();
        log.record(on(TUESDAY));
        log.record(on(MONDAY));
        assertThat(log.forDevice("D-77", MONDAY, TUESDAY))
                .extracting(Use::usedOn)
                .containsExactly(MONDAY, TUESDAY);
    }

    @Test
    void aWindowThatEndsBeforeTheEntryDoesNotIncludeIt() {
        UtilisationLog log = new UtilisationLog();
        log.record(on(TUESDAY));
        assertThat(log.forDevice("D-77", MONDAY, MONDAY)).isEmpty();
    }

    @Test
    void oneSourceCanBeFollowedAcrossDevices() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        log.record(aUse(TUESDAY, "D-78", "IR-5512", "Come By Chance", List.of("0142"), 14));
        assertThat(log.forSource("IR-5512", MONDAY, TUESDAY)).hasSize(2);
    }

    @Test
    void oneBadgeCanBeFollowedAcrossSites() {
        UtilisationLog log = new UtilisationLog();
        log.record(withCrew("0142", "0201"));
        log.record(aUse(TUESDAY, "D-77", "IR-5512", "Point Tupper", List.of("0142"), 14));
        assertThat(log.sitesWorked("0142", MONDAY, TUESDAY)).containsExactly("Come By Chance", "Point Tupper");
        assertThat(log.sitesWorked("0201", MONDAY, TUESDAY)).containsExactly("Come By Chance");
    }

    @Test
    void twoEntriesOnOneDayCountAsOneDayUsed() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        log.record(aUse(MONDAY, "D-77", "IR-5512", "Point Tupper", List.of("0142"), 14));
        assertThat(log.daysUsed("D-77", MONDAY, TUESDAY)).isEqualTo(1);
    }

    @Test
    void exposuresAddUpPerDevice() {
        UtilisationLog log = new UtilisationLog();
        log.record(withExposures(14));
        log.record(aUse(TUESDAY, "D-77", "IR-5512", "Come By Chance", List.of("0142"), 6));
        log.record(aUse(MONDAY, "D-78", "IR-5512", "Come By Chance", List.of("0142"), 3));
        assertThat(log.exposuresByDevice(MONDAY, TUESDAY)).containsOnly(entry("D-77", 20), entry("D-78", 3));
    }

    @Test
    void aDayTheDeviceWasOutWithNothingLoggedIsFound() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        Trip trip = new Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY);
        assertThat(log.unloggedDays(List.of(trip))).containsExactly(new DeviceDay("D-77", TUESDAY));
    }

    @Test
    void aFullyLoggedTripHasNoGaps() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        log.record(on(TUESDAY));
        Trip trip = new Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY, TUESDAY);
        assertThat(log.unloggedDays(List.of(trip))).isEmpty();
    }

    @Test
    void aTripStillOutIsCheckedUpToTheDayItLeft() {
        UtilisationLog log = new UtilisationLog();
        Trip trip = new Trip("IR-5512", "D-77", "0142", "Come By Chance", MONDAY);
        assertThat(log.unloggedDays(List.of(trip))).containsExactly(new DeviceDay("D-77", MONDAY));
    }

    @Test
    void anEntryFallsOutOfRetentionAfterThreeYears() {
        Use use = aUse();
        assertThat(use.destroyAfter()).isEqualTo(MONDAY.plusDays(Use.RETENTION_DAYS));
        UtilisationLog log = new UtilisationLog();
        log.record(use);
        assertThat(log.dueForDestruction(use.destroyAfter())).containsExactly(use);
        assertThat(log.dueForDestruction(use.destroyAfter().minusDays(1))).isEmpty();
    }

    @Test
    void entriesAfterADateCanBePulledForALateAmendment() {
        UtilisationLog log = new UtilisationLog();
        log.record(aUse());
        log.record(on(TUESDAY));
        assertThat(log.entriesAfter(MONDAY)).hasSize(1);
    }
}
