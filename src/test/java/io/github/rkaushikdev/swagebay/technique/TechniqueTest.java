package io.github.rkaushikdev.swagebay.technique;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.SourceFixtures.SHOT_DAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.exposure.Correction;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.testing.GeometryFixtures;
import io.github.rkaushikdev.swagebay.testing.SourceFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TechniqueTest {

    private final Joint sixInchWeld = GeometryFixtures.sixInchWeld();
    private final Joint twelveInchWeld = GeometryFixtures.twelveInchWeld();
    private final SealedSource iridiumSource = SourceFixtures.iridiumSource();
    private final ImagingSystem onD7 = GeometryFixtures.onD7();

    private static TechniqueRequest.Builder requestFor(Joint joint, SealedSource source, ImagingSystem system) {
        return TechniqueRequest.builder(joint, source, system, Films.film("d7"), SHOT_DAY);
    }

    private static TechniqueRequest.Builder outside(Joint joint, SealedSource source, ImagingSystem system) {
        return requestFor(joint, source, system).shot(ShotType.DWSI);
    }

    private static Correction screens(TechniqueSheet sheet) {
        return sheet.exposure().corrections().corrections().stream()
                .filter(correction -> correction.name().equals("screens"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aLineWithAccessIsPlannedPanoramicAndTakesOneExposure() {
        TechniqueSheet sheet = Techniques.planTechnique(
                requestFor(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.shot()).isSameAs(ShotType.PANORAMIC);
        assertThat(sheet.exposures()).isEqualTo(1);
        assertThat(sheet.penetrated()).isEqualTo(mm("12.97"));
    }

    @Test
    void aPanoramicSourceSitsOnTheAxisSoTheDistanceIsTheRadius() {
        TechniqueSheet sheet = Techniques.planTechnique(
                requestFor(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.sourceToFilm()).isEqualTo(sixInchWeld.od().dividedBy(2));
        assertThat(sheet.geometry().objectToFilm()).isEqualTo(sixInchWeld.wall());
    }

    @Test
    void ninetyCuriesInsideASixInchLineIsTooHotToTime() {
        TechniqueSheet sheet = Techniques.planTechnique(
                requestFor(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.isClean()).isFalse();
        assertThat(sheet.warnings().get(0)).contains("ten second floor");
    }

    @Test
    void theSameWeldShotFromOutsideIsAWorkableSheet() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.isClean()).isTrue();
        assertThat(sheet.exposures()).isEqualTo(8);
        assertThat(sheet.exposure().timer()).isEqualTo("0:37");
    }

    @Test
    void theOutsideShotCrossesTwoWallsAndTwoCaps() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.penetrated()).isEqualTo(mm("25.94"));
        assertThat(sheet.penetrated()).isEqualTo(sixInchWeld.totalThickness().times(2));
    }

    @Test
    void theIndicatorIsReadOffThePenetratedThicknessNotTheWall() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.indicator().indicator().identity()).isEqualTo(30);
        assertThat(sheet.indicator().thickness()).isEqualTo(sheet.penetrated());
    }

    @Test
    void theGeometryThatProducedTheDistanceTravelsWithTheSheet() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.geometry().sourceToFilm()).isEqualTo(sheet.sourceToFilm());
        assertThat(sheet.geometry().meets(sixInchWeld.wall())).isTrue();
        assertThat(sheet.sourceToFilm()).isGreaterThan(mm(1000));
    }

    @Test
    void aFilmSideIndicatorIsAskedForAndMarked() {
        TechniqueSheet sheet = Techniques.planTechnique(outside(sixInchWeld, iridiumSource, onD7)
                .indicatorSide(IndicatorSide.FILM)
                .build());
        assertThat(sheet.indicator().needsFMarker()).isTrue();
        assertThat(sheet.indicator().describe()).contains("F marker");
    }

    @Test
    void aSlowerFilmMakesALongerExposure() {
        TechniqueSheet fast = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        TechniqueSheet slow = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).film(Films.film("d4")).build());
        assertThat(slow.exposure().exposure()).isGreaterThan(fast.exposure().exposure());
    }

    @Test
    void theSourceIsDecayedToTheDayOfTheShot() {
        TechniqueSheet early = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        TechniqueSheet late = Techniques.planTechnique(outside(sixInchWeld, iridiumSource, onD7)
                .shotAt(SHOT_DAY.plusDays(80))
                .build());
        assertThat(late.exposure().activity()).isLessThan(early.exposure().activity());
        assertThat(late.exposure().exposure()).isGreaterThan(early.exposure().exposure());
    }

    @Test
    void theTotalSourceOutIsTheShotTimesTheExposureCount() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.totalSourceOut().ms())
                .isEqualTo(sheet.exposure().exposure().ms() * sheet.exposures());
        assertThat(sheet.totalSourceOut().asTimer()).isEqualTo("4:56");
    }

    @Test
    void aSourceWithALapsedLeakTestWarnsRatherThanRefusing() {
        TechniqueSheet sheet = Techniques.planTechnique(outside(sixInchWeld, iridiumSource, onD7)
                .shotAt(SHOT_DAY.plusDays(300))
                .build());
        assertThat(sheet.isClean()).isFalse();
        assertThat(sheet.warnings()).anyMatch(warning -> warning.contains("leak test"));
    }

    @Test
    void aSpentSourceWarnsOnTheWorkingFloor() {
        iridiumSource.setWorkingFloor(curie(85));
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.warnings()).anyMatch(warning -> warning.contains("working floor"));
    }

    @Test
    void aWindowTooShortForTheJointIsWarnedAbout() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).window(minutes(2)).build());
        assertThat(sheet.warnings()).anyMatch(warning -> warning.contains("source out"));
    }

    @Test
    void aGenerousWindowProducesNoWarning() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).window(minutes(600)).build());
        assertThat(sheet.isClean()).isTrue();
    }

    @Test
    void aWallOutsideTheIsotopePracticalRangeIsWarnedAbout() {
        Joint thin = new Joint(JointForm.PLATE_BUTT, mm(8), Materials.STEEL);
        TechniqueSheet sheet =
                Techniques.planTechnique(requestFor(thin, iridiumSource, onD7).build());
        assertThat(sheet.warnings()).anyMatch(warning -> warning.contains("practical range"));
    }

    @Test
    void aHeavyLineStaysInsideTheIridiumRangeOnASingleWall() {
        TechniqueSheet sheet = Techniques.planTechnique(
                requestFor(twelveInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.warnings()).noneMatch(warning -> warning.contains("practical range"));
        assertThat(sheet.penetrated()).isEqualTo(mm("19.98"));
    }

    @Test
    void aDensityWithNoEstablishedChartIsRefusedOutright() {
        TechniqueRequest request = outside(sixInchWeld, iridiumSource, onD7)
                .density(new BigDecimal("2.2"))
                .build();
        assertThatThrownBy(() -> Techniques.planTechnique(request)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aThicknessOffTheEndOfTheChartIsRefusedOutright() {
        Joint heavy = new Joint(JointForm.PLATE_BUTT, mm(100), Materials.STEEL);
        TechniqueRequest request = requestFor(heavy, iridiumSource, onD7).build();
        assertThatThrownBy(() -> Techniques.planTechnique(request))
                .isInstanceOfSatisfying(TechniqueError.class, refused -> assertThat(refused.context())
                        .containsEntry("thickness_mm", "100"));
    }

    @Test
    void aPlateWeldTakesOneExposureAndAContactShot() {
        Joint plate = new Joint(JointForm.PLATE_BUTT, mm(20), Materials.STEEL);
        TechniqueSheet sheet =
                Techniques.planTechnique(requestFor(plate, iridiumSource, onD7).build());
        assertThat(sheet.shot()).isSameAs(ShotType.CONTACT);
        assertThat(sheet.exposures()).isEqualTo(1);
        assertThat(sheet.penetrated()).isEqualTo(mm(20));
    }

    @Test
    void theSheetPrintsAsOneLineForTheJobBook() {
        TechniqueSheet sheet = Techniques.sheetOrRefuse(
                outside(sixInchWeld, iridiumSource, onD7).build());
        String text = sheet.describe();
        assertThat(text).contains("girth weld");
        assertThat(text).contains("double wall single image");
        assertThat(text).contains("source side");
    }

    @Test
    void screensShortenTheShotAndAppearInTheChain() {
        TechniqueSheet withScreens = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        TechniqueSheet without = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).screensFitted(false).build());
        assertThat(withScreens.exposure().exposure())
                .isLessThan(without.exposure().exposure());
        assertThat(withScreens.exposure().corrections().names()).contains("screens");
    }

    @Test
    void theScreenPairNamedOnTheSheetIsTheOneForTheBeam() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(screens(sheet).note()).contains("0.13 mm front");
    }

    @Test
    void noScreensIsRecordedRatherThanLeftOut() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).screensFitted(false).build());
        assertThat(screens(sheet).note()).isEqualTo("no screens");
        assertThat(screens(sheet).factor()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void aCollimatorCostsALittleExposure() {
        TechniqueSheet bare = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        TechniqueSheet cupped = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).collimatorKey("w60").build());
        assertThat(cupped.exposure().exposure()).isGreaterThan(bare.exposure().exposure());
        assertThat(cupped.exposure().corrections().names()).contains("collimator");
    }

    @Test
    void aTighterCupCostsLessThanAWiderOne() {
        TechniqueSheet tight = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).collimatorKey("du45").build());
        TechniqueSheet wide = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).collimatorKey("w90").build());
        assertThat(tight.exposure().exposure())
                .isLessThanOrEqualTo(wide.exposure().exposure());
    }

    @Test
    void noCollimatorLeavesTheChainAlone() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.exposure().corrections().names()).doesNotContain("collimator");
    }

    @Test
    void aCollimatorNothingKnowsAboutIsRefused() {
        TechniqueRequest request =
                outside(sixInchWeld, iridiumSource, onD7).collimatorKey("w99").build();
        assertThatThrownBy(() -> Techniques.planTechnique(request)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theRightPairForTheBeamDrawsNoWarning() {
        TechniqueSheet sheet = Techniques.planTechnique(
                outside(sixInchWeld, iridiumSource, onD7).build());
        assertThat(sheet.warnings()).noneMatch(warning -> warning.contains("front lead"));
    }
}
