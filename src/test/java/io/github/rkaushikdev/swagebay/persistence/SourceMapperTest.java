package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.sources.SourceState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SourceMapperTest {

    private static final OffsetDateTime ASSAY = OffsetDateTime.of(2024, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final LocalDateTime NAIVE_ASSAY = LocalDateTime.of(2024, 8, 1, 0, 0);

    private static SourceCertificate certificate(String serial, Isotope madeOf) {
        return new SourceCertificate(serial, madeOf, curie(90), NAIVE_ASSAY, "QSA", "A424-9", mm(3), mm("3.5"));
    }

    private static SealedSource aSource(String serial, Isotope madeOf, SourceState state, List<LeakTest> tests) {
        return new SealedSource(certificate(serial, madeOf), state, curie(15), tests);
    }

    private static SealedSource aSource(String serial, Isotope madeOf, SourceState state, int tests) {
        List<LeakTest> wipes = IntStream.range(0, tests)
                .mapToObj(index ->
                        new LeakTest(NAIVE_ASSAY.plusDays(index * 30L), BigDecimal.valueOf(index), "rp" + index))
                .toList();
        return aSource(serial, madeOf, state, wipes);
    }

    private static SealedSource aSource() {
        return aSource("IR-5512", IR192, SourceState.IN_SERVICE, 1);
    }

    private static SealedSource withTests(int tests) {
        return aSource("IR-5512", IR192, SourceState.IN_SERVICE, tests);
    }

    /** Write the source to a database of its own, and read it back from there. */
    private static SealedSource stored(SealedSource source) {
        try (TestSession db = TestSession.open()) {
            db.add(SourceMapper.sourceToRow(source));
            db.commit();
            return SourceMapper.rowToSource(db.one(SourceRow.class));
        }
    }

    @Test
    void aSourceSurvivesTheDatabase() {
        SealedSource asWritten = aSource();
        SealedSource recovered = stored(asWritten);
        assertThat(recovered.serial()).isEqualTo(asWritten.serial());
        assertThat(recovered.isotope()).isSameAs(IR192);
        assertThat(recovered.certificate().assayActivity()).isEqualTo(curie(90));
    }

    @Test
    void thePelletDimensionsComeBackTheRightWayRound() {
        SealedSource recovered = stored(aSource());
        assertThat(recovered.certificate().activeDiameter()).isEqualTo(mm(3));
        assertThat(recovered.certificate().activeLength()).isEqualTo(mm("3.5"));
        assertThat(recovered.certificate().focalSize()).isEqualTo(mm("3.5"));
    }

    @Test
    void theWorkingFloorIsNotConfusedWithTheAssay() {
        SealedSource recovered = stored(aSource());
        assertThat(recovered.workingFloor()).isEqualTo(curie(15));
        assertThat(recovered.certificate().assayActivity()).isEqualTo(curie(90));
    }

    @Test
    void theStateSurvivesAsThePhraseAHumanReads() {
        SourceRow row = SourceMapper.sourceToRow(aSource("IR-5512", IR192, SourceState.AWAITING_DISPOSAL, 1));
        assertThat(row.getState()).isEqualTo("awaiting disposal");
    }

    @Test
    void everyStateSurvivesTheRoundTrip() {
        for (SourceState state : SourceState.values()) {
            assertThat(stored(aSource("IR-5512", IR192, state, 1)).state()).isSameAs(state);
        }
    }

    @Test
    void aStoredStateThatIsNotAStateIsRefused() {
        assertThatThrownBy(() -> SourceMapper.stateFromValue("mislaid")).isInstanceOf(ConfigurationError.class);
        assertThat(SourceMapper.stateFromValue("in transit")).isSameAs(SourceState.IN_TRANSIT);
    }

    @Test
    void theLeakTestsComeBackInFull() {
        SealedSource recovered = stored(withTests(3));
        assertThat(recovered.leakTests()).hasSize(3);
        assertThat(recovered.latestLeakTest())
                .hasValueSatisfying(latest -> assertThat(latest.technician()).isEqualTo("rp2"));
    }

    @Test
    void aSourceWithNoLeakTestsRoundTripsAsOne() {
        SealedSource recovered = stored(withTests(0));
        assertThat(recovered.leakTests()).isEmpty();
        assertThat(recovered.latestLeakTest()).isEmpty();
    }

    @Test
    void aFailedWipeResultSurvivesAsADecimal() {
        SealedSource source = aSource(
                "IR-5512",
                IR192,
                SourceState.IN_SERVICE,
                List.of(new LeakTest(NAIVE_ASSAY, new BigDecimal("204.5"), "rp")));
        SealedSource recovered = stored(source);
        assertThat(recovered.leakTests().get(0).becquerelsRemoved()).isEqualByComparingTo("204.5");
        assertThat(recovered.leakTests().get(0).passed()).isFalse();
    }

    @Test
    void aDifferentIsotopeComesBackAsThatIsotope() {
        assertThat(stored(aSource("IR-5512", SE75, SourceState.IN_SERVICE, 1)).isotope())
                .isSameAs(SE75);
    }

    @Test
    void theRecoveredSourceStillDecaysCorrectly() {
        SealedSource recovered = stored(aSource());
        Activity later = recovered.activityAt(NAIVE_ASSAY.plusDays(74));
        assertThat(later.curie()).isStrictlyBetween(new BigDecimal("44"), new BigDecimal("46"));
    }

    /** The column holds the key as text; the isotope itself never reaches the row. */
    @Test
    void theRowCarriesTheIsotopeKeyAndNotTheObject() {
        SourceRow row = SourceMapper.sourceToRow(aSource());
        assertThat(row.getIsotopeKey()).isEqualTo("ir192").isEqualTo(IR192.key());
    }

    @Test
    void twoSourcesCanBeStoredSideBySide() {
        try (TestSession db = TestSession.open()) {
            db.add(SourceMapper.sourceToRow(aSource("IR-5512", IR192, SourceState.IN_SERVICE, 1)));
            db.add(SourceMapper.sourceToRow(aSource("SE-2211", SE75, SourceState.IN_SERVICE, 1)));
            db.commit();
            List<SealedSource> recovered = db.all(SourceRow.class).stream()
                    .map(SourceMapper::rowToSource)
                    .toList();
            assertThat(recovered).extracting(SealedSource::serial).containsExactlyInAnyOrder("IR-5512", "SE-2211");
            assertThat(recovered)
                    .extracting(source -> source.isotope().key())
                    .containsExactlyInAnyOrder("ir192", "se75");
        }
    }

    @Test
    void theAssayInstantIsNotShiftedByTheRoundTrip() {
        SealedSource recovered = stored(aSource());
        assertThat(recovered.certificate().assayAt()).isEqualTo(NAIVE_ASSAY);
        assertThat(recovered.certificate().assayAt().toLocalDate()).isEqualTo(ASSAY.toLocalDate());
    }
}
