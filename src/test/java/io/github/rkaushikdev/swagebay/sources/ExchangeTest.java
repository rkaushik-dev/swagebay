package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Devices.deviceModel;
import static io.github.rkaushikdev.swagebay.sources.Exchanges.activityLost;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.safety.DeviceUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExchangeTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 4, 1, 0, 0);
    private static final LocalDate SWAP = LocalDate.of(2025, 6, 4);
    private static final DeviceUnit CAMERA = new DeviceUnit("D-77", deviceModel("880d"));
    private static final DeviceUnit COBALT_CAMERA = new DeviceUnit("D-99", deviceModel("tk100"));

    private static SealedSource aSource() {
        return aSource("IR-5512");
    }

    private static SealedSource aSource(String serial) {
        return aSource(serial, 90, IR192, ASSAY);
    }

    /** A source wiped clean at that moment, or never wiped when the moment is null. */
    private static SealedSource aSource(String serial, long activity, Isotope isotope, LocalDateTime wiped) {
        SourceCertificate certificate =
                new SourceCertificate(serial, isotope, curie(activity), ASSAY, "QSA", "A424-9", mm(3), mm(3));
        SealedSource source = new SealedSource(certificate);
        if (wiped != null) {
            source.recordLeakTest(new LeakTest(wiped, BigDecimal.ZERO, "rp"));
        }
        return source;
    }

    private static Exchange anExchange() {
        return anExchange(null, "IR-5512");
    }

    private static Exchange anExchange(String removed, String installed) {
        return anExchange(removed, installed, "D-77", SWAP);
    }

    /** An exchange signed and shipped, a null serial meaning no source on that side. */
    private static Exchange anExchange(String removed, String installed, String device, LocalDate on) {
        return new Exchange(
                device, Optional.ofNullable(removed), Optional.ofNullable(installed), on, "0142", "QSA-88213");
    }

    private static Optional<SealedSource> none() {
        return Optional.empty();
    }

    @Test
    void anExchangeThatMovedNothingIsRefused() {
        assertThatThrownBy(() -> anExchange(null, null)).isInstanceOf(ValidationError.class);
    }

    @Test
    void anExchangeNobodySignedIsRefused() {
        assertThatThrownBy(
                        () -> new Exchange("D-77", Optional.empty(), Optional.of("IR-5512"), SWAP, "  ", "QSA-88213"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void anExchangeWithNoShipmentIsRefused() {
        assertThatThrownBy(() -> new Exchange("D-77", Optional.empty(), Optional.of("IR-5512"), SWAP, "0142", "   "))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theSameSourceOutAndInIsRefused() {
        assertThatThrownBy(() -> anExchange("IR-5512", "IR-5512")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aLoadReadsAsALoad() {
        assertThat(anExchange().isLoad()).isTrue();
        assertThat(anExchange().isUnload()).isFalse();
    }

    @Test
    void anUnloadReadsAsAnUnload() {
        assertThat(anExchange("IR-5512", null).isUnload()).isTrue();
    }

    @Test
    void eachKindOfExchangeSaysWhatItDid() {
        assertThat(anExchange().describe()).contains("into D-77");
        assertThat(anExchange("IR-5512", null).describe()).contains("out of D-77");
        assertThat(anExchange("IR-4400", "IR-5512").describe()).contains("out and");
    }

    @Test
    void aLoadPutsTheSourceInTheDevice() {
        Loading loading = new Loading();
        SealedSource source = aSource();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(source));
        assertThat(loading.sourceIn("D-77")).contains("IR-5512");
        assertThat(loading.deviceHolding("IR-5512")).contains("D-77");
        assertThat(source.state()).isSameAs(SourceState.IN_SERVICE);
    }

    @Test
    void aDeviceNobodyLoadedHoldsNothing() {
        assertThat(new Loading().sourceIn("D-77")).isEmpty();
        assertThat(new Loading().deviceHolding("IR-5512")).isEmpty();
    }

    @Test
    void aSecondLoadWithoutAnUnloadIsRefused() {
        Loading loading = new Loading();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(aSource()));
        assertThatThrownBy(() ->
                        loading.apply(anExchange(null, "IR-6000"), CAMERA, none(), Optional.of(aSource("IR-6000"))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theRefusedLoadLeavesTheDeviceAsItWas() {
        Loading loading = new Loading();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(aSource()));
        assertThatThrownBy(() ->
                        loading.apply(anExchange(null, "IR-6000"), CAMERA, none(), Optional.of(aSource("IR-6000"))))
                .isInstanceOf(ConflictError.class);
        assertThat(loading.sourceIn("D-77")).contains("IR-5512");
        assertThat(loading.history()).hasSize(1);
    }

    @Test
    void anUnloadSendsTheSourceIntoTransit() {
        Loading loading = new Loading();
        SealedSource old = aSource();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(old));
        loading.apply(anExchange("IR-5512", null), CAMERA, Optional.of(old), none());
        assertThat(loading.sourceIn("D-77")).isEmpty();
        assertThat(old.state()).isSameAs(SourceState.IN_TRANSIT);
    }

    @Test
    void aSwapDoesBothHalvesAtOnce() {
        Loading loading = new Loading();
        SealedSource old = aSource();
        SealedSource fresh = aSource("IR-6000");
        loading.apply(anExchange(), CAMERA, none(), Optional.of(old));
        loading.apply(anExchange("IR-5512", "IR-6000"), CAMERA, Optional.of(old), Optional.of(fresh));
        assertThat(loading.sourceIn("D-77")).contains("IR-6000");
        assertThat(old.state()).isSameAs(SourceState.IN_TRANSIT);
        assertThat(fresh.state()).isSameAs(SourceState.IN_SERVICE);
    }

    @Test
    void takingOutASourceTheDeviceDoesNotHoldIsRefused() {
        Loading loading = new Loading();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(aSource()));
        assertThatThrownBy(() -> loading.apply(anExchange("IR-9999", null), CAMERA))
                .isInstanceOfSatisfying(
                        ConflictError.class,
                        caught -> assertThat(caught.context().get("holding")).isEqualTo("IR-5512"));
    }

    @Test
    void aSourceAlreadyInAnotherDeviceCannotBeLoadedAgain() {
        Loading loading = new Loading();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(aSource()));
        DeviceUnit other = new DeviceUnit("D-78", deviceModel("880s"));
        assertThatThrownBy(() ->
                        loading.apply(anExchange(null, "IR-5512", "D-78", SWAP), other, none(), Optional.of(aSource())))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anExchangeAgainstADifferentDeviceIsRefused() {
        assertThatThrownBy(() -> new Loading().apply(anExchange(null, "IR-5512", "D-78", SWAP), CAMERA))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSourceThatIsNotTheOneNamedIsRefused() {
        assertThatThrownBy(() -> new Loading().apply(anExchange(), CAMERA, none(), Optional.of(aSource("IR-9999"))))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSourceWithNoCurrentWipeTestDoesNotGoIn() {
        SealedSource stale = aSource("IR-5512", 90, IR192, ASSAY.minusDays(400));
        assertThatThrownBy(() -> new Loading().apply(anExchange(), CAMERA, none(), Optional.of(stale)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSourceAwaitingDisposalDoesNotGoIn() {
        SealedSource failed = aSource("IR-5512", 90, IR192, null);
        failed.recordLeakTest(new LeakTest(ASSAY.minusDays(1), BigDecimal.valueOf(400), "rp"));
        failed.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        assertThatThrownBy(() -> new Loading().apply(anExchange(), CAMERA, none(), Optional.of(failed)))
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining("awaiting disposal");
    }

    @Test
    void theWrongIsotopeForTheDeviceIsRefused() {
        SealedSource cobalt = aSource("CO-100", 20, CO60, ASSAY);
        assertThatThrownBy(() -> new Loading().apply(anExchange(null, "CO-100"), CAMERA, none(), Optional.of(cobalt)))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void moreCuriesThanTheCertificateCoversIsRefused() {
        SealedSource hot = aSource("IR-5512", 400, IR192, ASSAY);
        assertThatThrownBy(() -> new Loading().apply(anExchange(), CAMERA, none(), Optional.of(hot)))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aCobaltSourceGoesIntoTheCobaltDevice() {
        SealedSource cobalt = aSource("CO-100", 20, CO60, ASSAY);
        Loading loading = new Loading();
        loading.apply(anExchange(null, "CO-100", "D-99", SWAP), COBALT_CAMERA, none(), Optional.of(cobalt));
        assertThat(loading.sourceIn("D-99")).contains("CO-100");
    }

    @Test
    void theHistoryOfOneDeviceReadsInDateOrder() {
        Loading loading = new Loading();
        SealedSource old = aSource();
        SealedSource fresh = aSource("IR-6000", 90, IR192, ASSAY.plusDays(200));
        loading.apply(anExchange(), CAMERA, none(), Optional.of(old));
        Exchange later = anExchange("IR-5512", "IR-6000", "D-77", SWAP.plusDays(180));
        loading.apply(later, CAMERA, Optional.of(old), Optional.of(fresh));
        List<LocalDate> dates =
                loading.exchangesFor("D-77").stream().map(Exchange::performedOn).toList();
        assertThat(dates).isSorted();
        assertThat(loading.exchangesFor("D-78")).isEmpty();
    }

    @Test
    void theLoadedDevicesAreTheOnesHoldingSomething() {
        Loading loading = new Loading();
        loading.apply(anExchange(), CAMERA, none(), Optional.of(aSource()));
        assertThat(loading.loadedDevices()).containsExactly("D-77");
    }

    @Test
    void theActivityADeviceUsedUpIsTheDecayOverItsTimeIn() {
        LocalDate wentIn = ASSAY.toLocalDate();
        assertThat(activityLost(aSource(), wentIn, wentIn.plusDays(74))).isStrictlyBetween(curie(44), curie(46));
    }

    @Test
    void noTimeInTheDeviceLosesNothing() {
        LocalDate wentIn = ASSAY.toLocalDate();
        assertThat(activityLost(aSource(), wentIn, wentIn)).isEqualTo(curie(0));
    }

    @Test
    void comingOutBeforeGoingInIsRefused() {
        LocalDate wentIn = ASSAY.toLocalDate();
        assertThatThrownBy(() -> activityLost(aSource(), wentIn, wentIn.minusDays(1)))
                .isInstanceOf(ValidationError.class);
    }
}
