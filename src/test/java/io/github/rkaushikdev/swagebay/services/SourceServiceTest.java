package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SourceServiceTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 10, 1, 0, 0);
    private static final LocalDate WENT = LocalDate.of(2024, 10, 7);

    private static SealedSource aSource(String serial, boolean tested) {
        SourceCertificate certificate =
                new SourceCertificate(serial, Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3));
        SealedSource source = new SealedSource(certificate, curie(15));
        if (tested) {
            source.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        }
        return source;
    }

    private static SealedSource aSource() {
        return aSource("IR-5512", true);
    }

    private static void stocked(UnitOfWork work, boolean tested) {
        SourceService.registerSource(work, aSource("IR-5512", tested));
        if (work.sources().findDevice("880D-221").isEmpty()) {
            SourceService.registerDevice(work, "880D-221", "880d");
        }
        WorkFixtures.passInspections(work, "880D-221", WENT);
    }

    private static void stocked(UnitOfWork work) {
        stocked(work, true);
    }

    private static Booking aBooking(String serial, String site) {
        return new Booking(serial, "880D-221", "0142", site, WENT);
    }

    private static Booking aBooking() {
        return aBooking("IR-5512", "Come By Chance");
    }

    @Test
    void aSourceGoesOnTheInventoryOnce() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(work.sources().get("IR-5512").getIsotopeKey()).isEqualTo("ir192");
            assertThatThrownBy(() -> SourceService.registerSource(work, aSource()))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aDeviceGoesOnOnceToo() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThatThrownBy(() -> SourceService.registerDevice(work, "880D-221", "880d"))
                    .isInstanceOf(ConflictError.class);
            assertThatThrownBy(() -> SourceService.registerDevice(work, "  ", "880d"))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aSourceInTheShopReadsAsTheShop() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(SourceService.location(work, "IR-5512")).isEqualTo("shop");
        });
    }

    @Test
    void aSourceOutOnAJobReadsAsTheSite() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            assertThat(SourceService.location(work, "IR-5512")).isEqualTo("Come By Chance");
        });
    }

    @Test
    void aSourceCannotBeOutOnTwoJobs() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            assertThatThrownBy(() -> SourceService.sendOut(work, aBooking("IR-5512", "Somewhere Else")))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aSourceThatIsNotOutCannotComeBack() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThatThrownBy(() -> SourceService.bringBack(work, "IR-5512", WENT))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void bringingASourceBackFreesItForTheNextJob() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            SourceService.bringBack(work, "IR-5512", WENT.plusDays(2), 24);
            assertThat(SourceService.location(work, "IR-5512")).isEqualTo("shop");
            SourceService.sendOut(work, aBooking("IR-5512", "Next One"));
            assertThat(SourceService.location(work, "IR-5512")).isEqualTo("Next One");
        });
    }

    @Test
    void aSourceCannotComeBackBeforeItWentOut() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            assertThatThrownBy(() -> SourceService.bringBack(work, "IR-5512", WENT.minusDays(1)))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aNegativeExposureCountIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            assertThatThrownBy(() -> SourceService.bringBack(work, "IR-5512", WENT, -1))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aBookingNeedsACrewAndASite() {
        assertThatThrownBy(() -> new Booking("IR-5512", "880D-221", "  ", "site", WENT))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Booking("IR-5512", "880D-221", "0142", "  ", WENT))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSourceOrDeviceNotOnTheInventoryCannotBeBookedOut() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThatThrownBy(() -> SourceService.sendOut(work, aBooking("IR-9999", "Come By Chance")))
                    .isInstanceOf(NotFoundError.class);
        });
    }

    @Test
    void aFitSourceIsReportedFit() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(SourceService.fitness(work, "IR-5512", ASSAY.plusDays(30))
                            .fit())
                    .isTrue();
        });
    }

    @Test
    void anUntestedSourceIsReportedUnfitWithTheReason() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work, false);
            Fitness report = SourceService.fitness(work, "IR-5512", ASSAY);
            assertThat(report.fit()).isFalse();
            assertThat(report.reasons()).contains("leak test is not current");
            assertThat(report.describe()).contains("leak test");
        });
    }

    @Test
    void fitnessDependsOnTheDayItIsAskedAbout() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(SourceService.fitness(work, "IR-5512", ASSAY.plusDays(30))
                            .fit())
                    .isTrue();
            assertThat(SourceService.fitness(work, "IR-5512", ASSAY.plusDays(300))
                            .fit())
                    .isFalse();
        });
    }

    @Test
    void aRetiredSourceCannotBeBookedOut() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.retire(work, "IR-5512");
            assertThatThrownBy(() -> SourceService.sendOut(work, aBooking()))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    (String) caught.context().get("reasons"))
                            .contains("awaiting disposal"));
        });
    }

    @Test
    void aSourceOutOnAJobCannotBeRetired() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.sendOut(work, aBooking());
            assertThatThrownBy(() -> SourceService.retire(work, "IR-5512")).isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aSightingIsRecordedAgainstTheSource() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            SourceService.recordSighting(work, "IR-5512", LocalDate.of(2024, 10, 5), "0142", "shop");
            assertThat(work.sources().get("IR-5512").getSightings()).hasSize(1);
            assertThatThrownBy(() ->
                            SourceService.recordSighting(work, "IR-5512", LocalDate.of(2024, 10, 5), "  ", "shop"))
                    .isInstanceOf(ValidationError.class);
        });
    }
}
