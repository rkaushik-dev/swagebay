package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SealedTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 3, 1, 0, 0);
    private static final Activity FULL_LOAD = curie(100);

    private static SourceCertificate certificate() {
        return certificate("IR-4471", FULL_LOAD, "3.0", "3.0");
    }

    private static SourceCertificate certificate(String serial, Activity activity, String diameter, String length) {
        return new SourceCertificate(serial, IR192, activity, ASSAY, "QSA", "A424-9", mm(diameter), mm(length));
    }

    private static SealedSource source() {
        SealedSource src = new SealedSource(certificate());
        src.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        return src;
    }

    private static LeakTest wipe(LocalDateTime at, long becquerels) {
        return new LeakTest(at, BigDecimal.valueOf(becquerels), "rp");
    }

    @Test
    void theCertificateCarriesThePelletSizeTheGeometryNeeds() {
        SourceCertificate cert = certificate("IR-4471", FULL_LOAD, "3.0", "4.5");
        assertThat(cert.focalSize()).isEqualTo(mm("4.5"));
        assertThat(certificate("IR-4471", FULL_LOAD, "4.0", "2.0").focalSize()).isEqualTo(mm("4.0"));
    }

    @Test
    void aCertificateWithoutASerialOrAPelletIsRefused() {
        assertThatThrownBy(() -> certificate("   ", FULL_LOAD, "3.0", "3.0")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> certificate("IR-4471", FULL_LOAD, "0", "3.0")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> certificate("IR-4471", Activity.ZERO, "3.0", "3.0"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theSourceDecaysOnItsOwnCertificate() {
        SealedSource src = source();
        assertThat(src.activityAt(ASSAY)).isEqualTo(curie(100));
        Activity later = src.activityAt(ASSAY.plusDays(Decimals.truncate(IR192.halfLifeDays())));
        assertThat(later.curie()).isStrictlyBetween(new BigDecimal("50"), new BigDecimal("51"));
    }

    @Test
    void aFreshSourceWithACurrentLeakTestIsFit() {
        SealedSource src = source();
        assertThat(src.isFitAt(ASSAY.plusDays(30))).isTrue();
        assertThat(src.reasonsNotToUse(ASSAY.plusDays(30))).isEmpty();
    }

    @Test
    void aLeakTestExpiresAfterSixMonths() {
        SealedSource src = source();
        assertThat(src.leakTestCurrentAt(ASSAY.plusDays(179))).isTrue();
        assertThat(src.leakTestCurrentAt(ASSAY.plusDays(181))).isFalse();
        assertThat(src.reasonsNotToUse(ASSAY.plusDays(181))).contains("leak test is not current");
    }

    @Test
    void aSourceWithNoLeakTestAtAllIsNotFit() {
        SealedSource src = new SealedSource(certificate());
        assertThat(src.latestLeakTest()).isEmpty();
        assertThat(src.reasonsNotToUse(ASSAY)).contains("leak test is not current");
    }

    @Test
    void aFailedWipePutsTheSourceStraightIntoDisposal() {
        SealedSource src = source();
        src.recordLeakTest(wipe(ASSAY.plusDays(10), 400));
        assertThat(src.state()).isSameAs(SourceState.AWAITING_DISPOSAL);
        List<String> reasons = src.reasonsNotToUse(ASSAY.plusDays(11));
        assertThat(reasons).contains("source is awaiting disposal");
        assertThat(reasons).contains("leak test is not current");
    }

    @Test
    void theWipeLimitIsOneHundredAndEightyFiveBecquerels() {
        assertThat(wipe(ASSAY, 184).passed()).isTrue();
        assertThat(wipe(ASSAY, 185).passed()).isFalse();
        assertThat(wipe(ASSAY, 1000).passed()).isFalse();
    }

    @Test
    void theLatestWipeIsTheOneThatCounts() {
        SealedSource src = source();
        src.recordLeakTest(wipe(ASSAY.plusDays(170), 2));
        assertThat(src.leakTestCurrentAt(ASSAY.plusDays(300))).isTrue();
        assertThat(src.latestLeakTest().orElseThrow().performedAt()).isEqualTo(ASSAY.plusDays(170));
    }

    @Test
    void aSourceBelowTheWorkingFloorStaysInThePig() {
        SealedSource src = source();
        src.setWorkingFloor(curie(60));
        LocalDateTime late = ASSAY.plusDays(80);
        assertThat(src.reasonsNotToUse(late)).contains("activity is below the working floor");
    }

    @Test
    void transitAndDisposalStatesBlockUseAndStoredDoesNot() {
        assertThat(SourceState.IN_SERVICE.canBeUsed()).isTrue();
        assertThat(SourceState.STORED.canBeUsed()).isTrue();
        assertThat(SourceState.IN_TRANSIT.canBeUsed()).isFalse();
        assertThat(SourceState.DISPOSED.canBeUsed()).isFalse();
        SealedSource src = source();
        src.setState(SourceState.IN_TRANSIT);
        assertThat(src.isFitAt(ASSAY)).isFalse();
    }

    @Test
    void everyReasonIsReportedNotJustTheFirst() {
        SealedSource src = new SealedSource(certificate(), SourceState.DISPOSED);
        src.setWorkingFloor(curie(500));
        assertThat(src.reasonsNotToUse(ASSAY)).hasSize(3);
    }

    @Test
    void aSourcePrintsAsTheIsotopeAndTheSerial() {
        assertThat(source()).hasToString("Ir-192 IR-4471");
        assertThat(source().isotope()).isSameAs(IR192);
        assertThat(CO60).isNotSameAs(IR192);
    }
}
