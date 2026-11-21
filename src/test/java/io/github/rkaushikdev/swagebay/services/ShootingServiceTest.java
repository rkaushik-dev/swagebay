package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Calibration;
import io.github.rkaushikdev.swagebay.imaging.Density;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.HoleIndicator;
import io.github.rkaushikdev.swagebay.imaging.HoleSize;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.imaging.IndicatorEvidence;
import io.github.rkaushikdev.swagebay.imaging.NoiseMeasurement;
import io.github.rkaushikdev.swagebay.imaging.Plates;
import io.github.rkaushikdev.swagebay.imaging.Reading;
import io.github.rkaushikdev.swagebay.imaging.TechniqueClass;
import io.github.rkaushikdev.swagebay.imaging.WireIndicator;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShootingServiceTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 9, 16);
    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 9, 17, 23, 30);
    private static final LocalDateTime CHECKED = LocalDateTime.of(2024, 9, 17, 18, 0);
    private static final ImagingSystem ON_FILM = ImagingSystem.fromFilm(Films.film("d7"));
    private static final ImagingSystem ON_PLATE = ImagingSystem.fromPlate(Plates.plate("ip1_hd"));
    private static final WeldId WELD = WeldId.parse("GW-104");

    private static JobNumber prepared(UnitOfWork work) {
        JobNumber number = JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", OPENED));
        JobService.addWeld(
                work,
                number,
                new NewWeld(WELD, PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), mm(529), "w-19", OPENED));
        return number;
    }

    private static Evidence filmEvidence(boolean visible, String density) {
        BigDecimal reading = new BigDecimal(density);
        return Evidence.builder(
                        ON_FILM,
                        mm(26),
                        TechniqueClass.A,
                        IndicatorEvidence.hole(new HoleIndicator(30), HoleSize.TWO_T, visible))
                .density(Density.grade(
                        List.of(new Reading("indicator", reading, true), new Reading("weld", reading)),
                        true,
                        new Calibration(CHECKED, new BigDecimal("2.00"), new BigDecimal("2.01"), "X-Rite"),
                        SHOT))
                .build();
    }

    private static Evidence filmEvidence() {
        return filmEvidence(true, "2.50");
    }

    private static Evidence plateEvidence() {
        return Evidence.builder(ON_PLATE, mm(26), TechniqueClass.A, IndicatorEvidence.wire(new WireIndicator(11), 12))
                .noise(new NoiseMeasurement("parent", BigDecimal.valueOf(12000), BigDecimal.valueOf(100), mm("0.040")))
                .build();
    }

    private static ShotRecord aShot(int index, Evidence evidence) {
        return new ShotRecord(WELD, index, SHOT, "RT-001 rev 1", evidence);
    }

    private static ShotRecord aShot(int index) {
        return aShot(index, filmEvidence());
    }

    private static ShotRecord aShot(Evidence evidence) {
        return aShot(1, evidence);
    }

    @Test
    void aShotIsNumberedAndStored() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult result = ShootingService.recordShot(work, job, aShot(1));
            assertThat(result.number()).isEqualTo("24-0001-R0001");
            assertThat(result.needsReshooting()).isFalse();
        });
    }

    @Test
    void theNumbersRunOnForTheJob() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult first = ShootingService.recordShot(work, job, aShot(1));
            ShotResult second = ShootingService.recordShot(work, job, aShot(2));
            assertThat(first.number()).isEqualTo("24-0001-R0001");
            assertThat(second.number()).isEqualTo("24-0001-R0002");
        });
    }

    @Test
    void theSameExposureTwiceOnOneWeldIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShootingService.recordShot(work, job, aShot(1));
            assertThatThrownBy(() -> ShootingService.recordShot(work, job, aShot(1)))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    caught.context().get("exposure"))
                            .isEqualTo(1));
        });
    }

    @Test
    void anInadmissibleShotIsStillRecordedAndStillNumbered() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult result = ShootingService.recordShot(work, job, aShot(filmEvidence(false, "2.50")));
            assertThat(result.needsReshooting()).isTrue();
            assertThat(result.number()).isEqualTo("24-0001-R0001");
            RadiographRow stored = work.welds().radiograph(result.number());
            assertThat(stored.isAdmissible()).isFalse();
            assertThat(stored.getInadmissibleReason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("not visible"));
        });
    }

    @Test
    void theDensityReadingIsStoredWithTheFilm() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult result = ShootingService.recordShot(work, job, aShot(1));
            RadiographRow stored = work.welds().radiograph(result.number());
            assertThat(stored.getDensityThroughIndicator())
                    .hasValueSatisfying(density -> assertThat(density).isEqualByComparingTo("2.50"));
            assertThat(stored.getNormalisedSnr()).isEmpty();
            assertThat(stored.getMedium()).isEqualTo("film");
        });
    }

    @Test
    void aDigitalShotStoresTheNoiseFigureInstead() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult result = ShootingService.recordShot(work, job, aShot(plateEvidence()));
            RadiographRow stored = work.welds().radiograph(result.number());
            assertThat(stored.getDensityThroughIndicator()).isEmpty();
            assertThat(stored.getNormalisedSnr())
                    .hasValueSatisfying(snr -> assertThat(snr).isGreaterThan(BigDecimal.valueOf(100)));
            assertThat(stored.getMedium()).isEqualTo("computed radiography");
        });
    }

    @Test
    void theVerdictIsComputedHereAndNotHandedIn() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShotResult thin = ShootingService.recordShot(work, job, aShot(filmEvidence(true, "1.40")));
            assertThat(thin.needsReshooting()).isTrue();
            assertThat(thin.admissibility().reasons()).anyMatch(reason -> reason.contains("floor"));
        });
    }

    @Test
    void aShotAgainstAWeldThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobNumber job = prepared(work);
                    ShootingService.recordShot(
                            work, job, new ShotRecord(WeldId.parse("GW-999"), 1, SHOT, "RT-001 rev 1", filmEvidence()));
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aShotAgainstAJobThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    prepared(work);
                    ShootingService.recordShot(work, new JobNumber(24, 99), aShot(1));
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aShotNeedsAnExposureNumberAndATechnique() {
        assertThatThrownBy(() -> aShot(0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new ShotRecord(WELD, 1, SHOT, "  ", filmEvidence()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theExposuresRecordedSoFarComeBackInOrder() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShootingService.recordShot(work, job, aShot(3));
            ShootingService.recordShot(work, job, aShot(1));
            assertThat(ShootingService.exposuresRecorded(work, job, WELD)).containsExactly(1, 3);
        });
    }

    @Test
    void theMissingExposuresAreTheGapsUpToTheExpectedCount() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            ShootingService.recordShot(work, job, aShot(1));
            ShootingService.recordShot(work, job, aShot(3));
            assertThat(ShootingService.missingExposures(work, job, WELD, 4)).containsExactly(2, 4);
            assertThat(ShootingService.missingExposures(work, job, WELD, 3)).containsExactly(2);
        });
    }

    @Test
    void anExpectedCountOfNothingIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobNumber job = prepared(work);
                    ShootingService.missingExposures(work, job, WELD, 0);
                }))
                .isInstanceOf(ValidationError.class);
    }
}
