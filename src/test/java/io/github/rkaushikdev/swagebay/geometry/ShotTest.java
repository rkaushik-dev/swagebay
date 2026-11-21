package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.geometry.Shots.chooseShot;
import static io.github.rkaushikdev.swagebay.geometry.Shots.coverageArc;
import static io.github.rkaushikdev.swagebay.geometry.Shots.exposuresRequired;
import static io.github.rkaushikdev.swagebay.geometry.Shots.feasibleShots;
import static io.github.rkaushikdev.swagebay.geometry.Shots.penetratedThickness;
import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShotTest {

    private static final Length HEAD = mm(25);
    private static final Length CLEARANCE = mm(5);

    private static Joint bigLine() {
        return bigLine("0");
    }

    private static Joint bigLine(String reinforcement) {
        return PipeSchedule.girthWeld("12", "40", STEEL, mm(reinforcement));
    }

    private static Joint smallLine() {
        return PipeSchedule.girthWeld("1", "80", STEEL, mm("0"));
    }

    private static Joint plate() {
        return new Joint(JointForm.PLATE_BUTT, mm(12), STEEL);
    }

    private static Map<ShotType, ShotFeasibility> byShot(Joint joint, Length head) {
        Map<ShotType, ShotFeasibility> results = new EnumMap<>(ShotType.class);
        for (ShotFeasibility feasibility : feasibleShots(joint, head, CLEARANCE)) {
            results.put(feasibility.shot(), feasibility);
        }
        return results;
    }

    @Test
    void aSingleWallShotCrossesOneWallAndOneCap() {
        Joint joint = bigLine("2.5");
        assertThat(penetratedThickness(joint, ShotType.PANORAMIC)).isEqualTo(mm("12.81"));
        assertThat(penetratedThickness(joint, ShotType.SWSI)).isEqualTo(mm("12.81"));
    }

    @Test
    void aDoubleWallShotCrossesTwoWallsAndTwoCaps() {
        Joint joint = bigLine("2.5");
        assertThat(penetratedThickness(joint, ShotType.DWSI)).isEqualTo(mm("25.62"));
        assertThat(penetratedThickness(joint, ShotType.DWDI_ELLIPTICAL)).isEqualTo(mm("25.62"));
    }

    @Test
    void theCapIsThePartPeopleForget() {
        Length bare = penetratedThickness(bigLine(), ShotType.DWSI);
        Length capped = penetratedThickness(bigLine("2.5"), ShotType.DWSI);
        assertThat(capped.minus(bare)).isEqualTo(mm(5));
    }

    @Test
    void whichShotsReadOneWallAndWhichReadTwo() {
        assertThat(ShotType.DWSI.wallsPenetrated()).isEqualTo(2);
        assertThat(ShotType.DWSI.wallsInterpreted()).isEqualTo(1);
        assertThat(ShotType.DWDI_ELLIPTICAL.wallsInterpreted()).isEqualTo(2);
        assertThat(ShotType.PANORAMIC.wallsPenetrated()).isEqualTo(1);
    }

    @Test
    void theCodeMinimumExposureCounts() {
        assertThat(ShotType.PANORAMIC.minimumExposures()).isEqualTo(1);
        assertThat(ShotType.DWDI_ELLIPTICAL.minimumExposures()).isEqualTo(2);
        assertThat(ShotType.DWDI_SUPERIMPOSED.minimumExposures()).isEqualTo(3);
        assertThat(ShotType.DWSI.minimumExposures()).isEqualTo(3);
    }

    @Test
    void theSeparationsMatchTheCounts() {
        assertThat(ShotType.DWDI_ELLIPTICAL.exposureSeparationDegrees()).isEqualTo(90);
        assertThat(ShotType.DWDI_SUPERIMPOSED.exposureSeparationDegrees()).isEqualTo(60);
        assertThat(ShotType.DWSI.exposureSeparationDegrees()).isEqualTo(120);
    }

    @Test
    void aPlateWeldTakesOneExposureWhateverTheShotTypeSays() {
        assertThat(exposuresRequired(plate(), ShotType.DWSI)).isEqualTo(1);
        assertThat(exposuresRequired(bigLine(), ShotType.DWSI)).isEqualTo(3);
    }

    @Test
    void aBigLineWithAccessGetsThePanoramicShot() {
        assertThat(chooseShot(bigLine(), HEAD, CLEARANCE)).isSameAs(ShotType.PANORAMIC);
    }

    @Test
    void aSmallBoreLineGetsTheEllipticalShot() {
        assertThat(chooseShot(smallLine(), HEAD, CLEARANCE)).isSameAs(ShotType.DWDI_ELLIPTICAL);
    }

    @Test
    void aBigLineWithNoAccessFallsBackToDoubleWallSingleImage() {
        assertThat(chooseShot(bigLine(), mm(400), CLEARANCE)).isSameAs(ShotType.DWSI);
    }

    @Test
    void doubleWallDoubleImageIsRefusedAboveThreeAndAHalfInches() {
        ShotFeasibility elliptical = byShot(bigLine(), HEAD).get(ShotType.DWDI_ELLIPTICAL);
        assertThat(elliptical.available()).isFalse();
        assertThat(elliptical.reason())
                .hasValueSatisfying(reason -> assertThat(reason).contains("89 mm"));
    }

    @Test
    void theReasonAgainstAShotIsReportedInWords() {
        Map<ShotType, ShotFeasibility> results = byShot(smallLine(), mm(400));
        assertThat(results.get(ShotType.PANORAMIC).reason()).contains("no room for the source inside the bore");
        assertThat(results.get(ShotType.SWSI).reason()).contains("no room for the source inside the bore");
        assertThat(results.get(ShotType.DWDI_ELLIPTICAL).available()).isTrue();
    }

    @Test
    void plateHasNoSecondWallToShootThrough() {
        Map<ShotType, ShotFeasibility> results = byShot(plate(), HEAD);
        assertThat(results.get(ShotType.DWSI).reason()).contains("plate has no second wall");
        assertThat(results.get(ShotType.DWDI_ELLIPTICAL).reason()).contains("plate has no second wall");
        assertThat(results.get(ShotType.CONTACT).available()).isTrue();
        assertThat(chooseShot(plate(), HEAD, CLEARANCE)).isSameAs(ShotType.CONTACT);
    }

    @Test
    void aSeamWeldCannotBeShotPanoramically() {
        Joint seam = Joint.builder(JointForm.PIPE_SEAM, mm(10), STEEL)
                .outsideDiameter(mm(600))
                .seamLength(mm(2000))
                .build();
        assertThat(byShot(seam, HEAD).get(ShotType.PANORAMIC).reason())
                .contains("a panoramic shot needs a circumferential weld");
    }

    @Test
    void theArcEachExposureCarries() {
        assertThat(coverageArc(bigLine(), 3)).isEqualByComparingTo("120");
        assertThat(coverageArc(bigLine(), 4)).isEqualByComparingTo("90");
        assertThat(coverageArc(plate(), 1)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> coverageArc(bigLine(), 0)).isInstanceOf(TechniqueError.class);
    }
}
