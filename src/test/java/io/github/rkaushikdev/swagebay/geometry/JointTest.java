package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JointTest {

    private static Joint girth() {
        return girth("168.3", "7.11", "0");
    }

    private static Joint girth(String od, String wall) {
        return girth(od, wall, "0");
    }

    private static Joint girthOd(String od) {
        return girth(od, "7.11", "0");
    }

    private static Joint girth(String od, String wall, String reinforcement) {
        return Joint.builder(JointForm.PIPE_GIRTH, mm(wall), STEEL)
                .outsideDiameter(mm(od))
                .reinforcement(mm(reinforcement))
                .build();
    }

    @Test
    void aSixInchLineGivesTheDiametersTheLineListSays() {
        Joint joint = girth();
        assertThat(joint.od()).isEqualTo(mm("168.3"));
        assertThat(joint.insideDiameter()).isEqualTo(mm("154.08"));
        assertThat(joint.diameterToWall()).isGreaterThan(BigDecimal.valueOf(23));
    }

    @Test
    void circumferencesComeOutInWholeMicrometres() {
        Joint joint = girth();
        assertThat(joint.outsideCircumference()).isEqualTo(new Length(528730));
        assertThat(joint.insideCircumference()).isLessThan(joint.outsideCircumference());
        assertThat(joint.insideCircumference()).isLessThan(joint.meanCircumference());
        assertThat(joint.meanCircumference()).isLessThan(joint.outsideCircumference());
    }

    @Test
    void reinforcementIsMeasuredNotSpecifiedAndAddsToThePath() {
        Joint bare = girth("168.3", "7.11", "0");
        Joint capped = girth("168.3", "7.11", "2.4");
        assertThat(bare.totalThickness()).isEqualTo(mm("7.11"));
        assertThat(capped.totalThickness()).isEqualTo(mm("9.51"));
    }

    @Test
    void smallBoreIsDecidedAtThreeAndAHalfInches() {
        assertThat(inch("3.5")).isEqualTo(Joint.SMALL_BORE_LIMIT);
        assertThat(girthOd("88.9").isSmallBore()).isTrue();
        assertThat(girthOd("60.3").isSmallBore()).isTrue();
        assertThat(girthOd("88.91").isSmallBore()).isFalse();
        assertThat(girth().isSmallBore()).isFalse();
    }

    @Test
    void plateCarriesNoDiameterAndSaysSoWhenAsked() {
        Joint plate = new Joint(JointForm.PLATE_BUTT, mm(12), STEEL);
        assertThat(plate.isSmallBore()).isFalse();
        assertThatThrownBy(plate::od).isInstanceOf(ValidationError.class);
        assertThatThrownBy(plate::outsideCircumference).isInstanceOf(ValidationError.class);
    }

    @Test
    void aPipeJointWithoutADiameterIsRefusedAtConstruction() {
        assertThatThrownBy(() -> new Joint(JointForm.PIPE_GIRTH, mm(7), STEEL)).isInstanceOf(ValidationError.class);
    }

    @Test
    void plateWithADiameterIsRefusedToo() {
        assertThatThrownBy(() -> Joint.builder(JointForm.PLATE_BUTT, mm(12), STEEL)
                        .outsideDiameter(mm(100))
                        .build())
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aWallThatClosesTheBoreIsRefused() {
        assertThatThrownBy(() -> girth("20", "10")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> girth("20", "12")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aZeroOrNegativeWallIsRefused() {
        assertThatThrownBy(() -> new Joint(JointForm.PLATE_BUTT, Length.ZERO, STEEL))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Joint(JointForm.PLATE_BUTT, new Length(-1), STEEL))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void negativeReinforcementIsRefused() {
        assertThatThrownBy(() -> Joint.builder(JointForm.PLATE_BUTT, mm(12), STEEL)
                        .reinforcement(new Length(-1))
                        .build())
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aProjectorHeadFitsABigLineAndNotASmallOne() {
        Joint big = girth("323.9", "9.53");
        Joint small = girth("33.4", "3.38");
        Length head = mm(25);
        Length clearance = mm(5);
        assertThat(big.admitsInternalSource(head, clearance)).isTrue();
        assertThat(small.admitsInternalSource(head, clearance)).isFalse();
    }

    @Test
    void onlyTheRoundFormsAreCircumferential() {
        assertThat(JointForm.PIPE_GIRTH.isCircumferential()).isTrue();
        assertThat(JointForm.NOZZLE.isCircumferential()).isTrue();
        assertThat(JointForm.SOCKET.isCircumferential()).isTrue();
        assertThat(JointForm.PLATE_BUTT.isCircumferential()).isFalse();
        assertThat(JointForm.PIPE_SEAM.isCircumferential()).isFalse();
    }

    @Test
    void theDescriptionIsWhatGoesOnTheTechniqueSheet() {
        assertThat(girth().describe()).isEqualTo("carbon steel girth weld, 168.30 mm od, 7.11 mm wall");
        Joint plate = new Joint(JointForm.PLATE_BUTT, mm(12), Materials.material("aluminium"));
        assertThat(plate.describe()).isEqualTo("aluminium plate butt weld, 12.00 mm wall");
    }
}
