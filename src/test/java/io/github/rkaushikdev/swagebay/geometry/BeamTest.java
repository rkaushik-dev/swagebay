package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.geometry.BeamGeometry.minimumSourceToFilm;
import static io.github.rkaushikdev.swagebay.geometry.BeamGeometry.minimumSourceToObject;
import static io.github.rkaushikdev.swagebay.geometry.BeamGeometry.unsharpnessLimit;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import org.junit.jupiter.api.Test;

class BeamTest {

    @Test
    void theThreeDistancesAddUp() {
        BeamGeometry beam = new BeamGeometry(mm(600), mm(10), mm(3));
        assertThat(beam.sourceToFilm()).isEqualTo(mm(610));
    }

    @Test
    void buildingFromTheTapeMeasurementSplitsItCorrectly() {
        BeamGeometry beam = BeamGeometry.fromSfd(mm(610), mm(10), mm(3));
        assertThat(beam.sourceToObject()).isEqualTo(mm(600));
        assertThat(beam.sourceToFilm()).isEqualTo(mm(610));
    }

    @Test
    void aGeometryWhereTheObjectDoesNotFitIsRefused() {
        assertThatThrownBy(() -> BeamGeometry.fromSfd(mm(10), mm(10), mm(3))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> BeamGeometry.fromSfd(mm(5), mm(10), mm(3))).isInstanceOf(ValidationError.class);
    }

    @Test
    void unsharpnessIsThePelletScaledByTheDistanceRatio() {
        BeamGeometry beam = new BeamGeometry(mm(600), mm(10), mm(3));
        assertThat(beam.geometricUnsharpness()).isEqualTo(new Length(50));
    }

    @Test
    void movingTheSourceBackCutsTheBlur() {
        BeamGeometry near = new BeamGeometry(mm(300), mm(10), mm(3));
        BeamGeometry far = new BeamGeometry(mm(900), mm(10), mm(3));
        assertThat(far.geometricUnsharpness()).isLessThan(near.geometricUnsharpness());
        assertThat(near.geometricUnsharpness()).isEqualTo(new Length(100));
    }

    @Test
    void filmHardAgainstThePipeGivesNoBlurAtAll() {
        BeamGeometry flat = new BeamGeometry(mm(600), Length.ZERO, mm(3));
        assertThat(flat.geometricUnsharpness()).isEqualTo(Length.ZERO);
        assertThat(flat.magnification()).isEqualByComparingTo("1");
    }

    @Test
    void magnificationIsTheRatioOfTheTwoFarDistances() {
        BeamGeometry beam = new BeamGeometry(mm(600), mm(600), mm(3));
        assertThat(beam.magnification()).isEqualByComparingTo("2");
    }

    @Test
    void theAsmeLimitsStepRatherThanCurving() {
        assertThat(unsharpnessLimit(inch("1.99"))).isEqualTo(inch("0.020"));
        assertThat(unsharpnessLimit(inch(2))).isEqualTo(inch("0.030"));
        assertThat(unsharpnessLimit(inch("2.99"))).isEqualTo(inch("0.030"));
        assertThat(unsharpnessLimit(inch(3))).isEqualTo(inch("0.040"));
        assertThat(unsharpnessLimit(inch(4))).isEqualTo(inch("0.070"));
        assertThat(unsharpnessLimit(inch(10))).isEqualTo(inch("0.070"));
    }

    @Test
    void aThicknessOfNothingHasNoLimitToLookUp() {
        assertThatThrownBy(() -> unsharpnessLimit(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> unsharpnessLimit(new Length(-1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aShotThatMeetsTheLimitPassesTheCheckSilently() {
        BeamGeometry beam = new BeamGeometry(mm(600), mm(10), mm(3));
        assertThat(beam.meets(mm(10))).isTrue();
        assertThatCode(() -> beam.check(mm(10))).doesNotThrowAnyException();
    }

    @Test
    void aShotOverTheLimitNamesAllThreeNumbers() {
        BeamGeometry beam = new BeamGeometry(mm(100), mm(20), mm(3));
        assertThat(beam.meets(mm(10))).isFalse();
        assertThatThrownBy(() -> beam.check(mm(10)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("limit_mm", "0.508")
                        .containsEntry("sod_mm", "100"));
    }

    @Test
    void theSameGeometryCanFailOnThinWallAndPassOnThick() {
        BeamGeometry beam = new BeamGeometry(mm(400), mm(80), mm(3));
        assertThat(beam.meets(inch(1))).isFalse();
        assertThat(beam.meets(inch(5))).isTrue();
    }

    @Test
    void theMinimumDistanceIsRoundedUpNotDown() {
        Length closest = minimumSourceToObject(mm(3), mm(10), inch("0.020"));
        assertThat(closest.um()).isEqualTo(59056);
        assertThat(new BeamGeometry(closest, mm(10), mm(3)).meets(mm(10))).isTrue();
        Length tooClose = new Length(closest.um() - 500);
        assertThat(new BeamGeometry(tooClose, mm(10), mm(3)).meets(mm(10))).isFalse();
    }

    @Test
    void aZeroGapPutsNoDistanceRequirementOnTheShot() {
        assertThat(minimumSourceToObject(mm(3), Length.ZERO, inch("0.020"))).isEqualTo(new Length(1));
    }

    @Test
    void aLimitOfNothingIsRefused() {
        assertThatThrownBy(() -> minimumSourceToObject(mm(3), mm(10), Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theDistanceBoxOnTheSheetIsTheSodPlusTheGap() {
        Length sfd = minimumSourceToFilm(mm(3), mm(10), mm(10));
        assertThat(sfd)
                .isEqualTo(minimumSourceToObject(mm(3), mm(10), inch("0.020")).plus(mm(10)));
        assertThat(BeamGeometry.fromSfd(sfd, mm(10), mm(3)).meets(mm(10))).isTrue();
    }

    @Test
    void aGeometryNeedsARealSourceAndARealDistance() {
        assertThatThrownBy(() -> new BeamGeometry(Length.ZERO, mm(10), mm(3))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new BeamGeometry(mm(600), new Length(-1), mm(3)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new BeamGeometry(mm(600), mm(10), Length.ZERO)).isInstanceOf(ValidationError.class);
    }
}
