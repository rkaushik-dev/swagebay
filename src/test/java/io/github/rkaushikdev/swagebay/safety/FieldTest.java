package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static io.github.rkaushikdev.swagebay.sources.Devices.collimator;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FieldTest {

    private static final BigDecimal DOWN = BigDecimal.ZERO;
    private static final BigDecimal SIDE = BigDecimal.valueOf(180);

    private static RadiationField bare(long load) {
        return new RadiationField(IR192, curie(load));
    }

    private static RadiationField bare() {
        return bare(74);
    }

    private static RadiationField collimated() {
        return new RadiationField(IR192, curie(74), collimator("w60"));
    }

    private static RadiationField behind(Barrier... barriers) {
        return new RadiationField(IR192, curie(74), List.of(barriers));
    }

    @Test
    void theBoundariesComeOutWhereFieldExperienceSaysTheyDo() {
        Map<BoundaryKind, Length> boundaries = bare().boundaries(DOWN);
        assertThat(boundaries.get(BoundaryKind.RESTRICTED))
                .isGreaterThan(Length.fromM(130))
                .isLessThan(Length.fromM(135));
        assertThat(boundaries.get(BoundaryKind.HIGH_RADIATION))
                .isGreaterThan(Length.fromM(18))
                .isLessThan(Length.fromM(20));
    }

    @Test
    void aHotterBoundarySitsCloserIn() {
        Map<BoundaryKind, Length> boundaries = bare().boundaries(DOWN);
        assertThat(boundaries.get(BoundaryKind.HIGH_RADIATION)).isLessThan(boundaries.get(BoundaryKind.RADIATION_AREA));
        assertThat(boundaries.get(BoundaryKind.RADIATION_AREA)).isLessThan(boundaries.get(BoundaryKind.RESTRICTED));
    }

    @Test
    void aCollimatorDoesNothingDownTheBeam() {
        assertThat(collimated().boundary(BoundaryKind.RESTRICTED, DOWN))
                .isEqualTo(bare().boundary(BoundaryKind.RESTRICTED, DOWN));
    }

    @Test
    void aCollimatorPullsTheSideBoundaryRightIn() {
        RadiationField cup = collimated();
        assertThat(cup.boundary(BoundaryKind.RESTRICTED, SIDE))
                .isLessThan(cup.boundary(BoundaryKind.RESTRICTED, DOWN).dividedBy(3));
    }

    @Test
    void theRopeOffIsTwoDistancesWhenACollimatorIsFitted() {
        RopeOff plan = RopeOff.of(collimated());
        assertThat(plan.isDirectional()).isTrue();
        assertThat(plan.downBeam()).isGreaterThan(plan.offBeam());
        assertThat(plan.describe()).contains("down the beam");
    }

    @Test
    void theRopeOffIsOneDistanceWithoutOne() {
        RopeOff plan = RopeOff.of(bare());
        assertThat(plan.isDirectional()).isFalse();
        assertThat(plan.describe()).startsWith("restricted area at ");
    }

    @Test
    void aBarrierPullsTheBoundaryInTheSameWayInEveryDirection() {
        RadiationField shielded = behind(new Barrier("lead", mm(10)));
        assertThat(shielded.boundary(BoundaryKind.RESTRICTED, DOWN))
                .isLessThan(bare().boundary(BoundaryKind.RESTRICTED, DOWN));
        assertThat(shielded.boundary(BoundaryKind.RESTRICTED, DOWN))
                .isEqualTo(shielded.boundary(BoundaryKind.RESTRICTED, SIDE));
    }

    @Test
    void barriersStack() {
        RadiationField one = behind(new Barrier("lead", mm(10)));
        RadiationField two = behind(new Barrier("lead", mm(10)), new Barrier("lead", mm(10)));
        assertThat(two.attenuation(DOWN)).isLessThan(one.attenuation(DOWN));
        assertThat(two.boundary(BoundaryKind.RESTRICTED, DOWN)).isLessThan(one.boundary(BoundaryKind.RESTRICTED, DOWN));
    }

    @Test
    void aCollimatorAndABarrierBothApply() {
        RadiationField both = new RadiationField(
                IR192, curie(74), Optional.of(collimator("w60")), List.of(new Barrier("lead", mm(10))));
        assertThat(both.attenuation(SIDE)).isLessThan(both.attenuation(DOWN));
        assertThat(both.attenuation(DOWN)).isLessThan(BigDecimal.ONE);
    }

    @Test
    void nothingInTheWayAttenuatesNothing() {
        assertThat(bare().attenuation(DOWN)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(bare().attenuation(SIDE)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void aHotterSourcePushesEveryBoundaryOut() {
        Map<BoundaryKind, Length> small = bare(20).boundaries(DOWN);
        Map<BoundaryKind, Length> big = bare(120).boundaries(DOWN);
        for (BoundaryKind kind : BoundaryKind.values()) {
            assertThat(big.get(kind)).isGreaterThan(small.get(kind));
        }
    }

    @Test
    void cobaltPushesThemFurtherStillCurieForCurie() {
        RadiationField iridium = new RadiationField(IR192, curie(40));
        RadiationField cobalt = new RadiationField(CO60, curie(40));
        assertThat(cobalt.boundary(BoundaryKind.RESTRICTED, DOWN))
                .isGreaterThan(iridium.boundary(BoundaryKind.RESTRICTED, DOWN));
    }

    @Test
    void standingInsideABoundaryIsReportedAsSuch() {
        RadiationField plan = bare();
        Length boundary = plan.boundary(BoundaryKind.RESTRICTED, DOWN);
        assertThat(plan.isInside(BoundaryKind.RESTRICTED, new Length(boundary.um() / 2), DOWN))
                .isTrue();
        assertThat(plan.isInside(BoundaryKind.RESTRICTED, new Length(boundary.um() * 2), DOWN))
                .isFalse();
    }

    @Test
    void theBoundaryIsRoundedAwayFromTheSource() {
        RadiationField plan = bare();
        Length boundary = plan.boundary(BoundaryKind.RESTRICTED, DOWN);
        assertThat(plan.rateAt(boundary, DOWN)).isLessThanOrEqualTo(BoundaryKind.RESTRICTED.rate());
    }

    @Test
    void aRateNeedsADistanceAndABoundaryNeedsARate() {
        assertThatThrownBy(() -> bare().rateAt(Length.ZERO, DOWN)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> bare().distanceTo(DoseRate.ZERO, DOWN)).isInstanceOf(ValidationError.class);
    }

    @Test
    void anEmptyPigHasNoBoundaryAndNothingToRopeOff() {
        RadiationField empty = new RadiationField(IR192, Activity.ZERO);
        assertThat(empty.boundary(BoundaryKind.RESTRICTED, DOWN)).isEqualTo(Length.ZERO);
        assertThatThrownBy(() -> RopeOff.of(empty)).isInstanceOf(TechniqueError.class);
    }

    @Test
    void thePostingTextIsWhatGoesOnTheSign() {
        assertThat(BoundaryKind.HIGH_RADIATION.signReads()).isEqualTo("danger high radiation area");
        assertThat(BoundaryKind.RESTRICTED.rate()).isEqualTo(mrPerHour(2));
        assertThat(BoundaryKind.RADIATION_AREA).hasToString("radiation area");
    }

    @Test
    void aBoundaryCanBeProjectedFromAMeterReading() {
        RadiationField plan = bare();
        DoseRate reading = plan.rateAt(Length.fromM(10), DOWN);
        Length projected = plan.distanceFromReading(reading, Length.fromM(10), mrPerHour(2));
        Length fromTable = plan.boundary(BoundaryKind.RESTRICTED, DOWN);
        assertThat(Math.abs(projected.um() - fromTable.um())).isLessThan(100_000);
    }

    @Test
    void aReadingCarriesWhateverScatterTheSiteProduced() {
        RadiationField plan = bare();
        DoseRate hot =
                mrPerHour(Decimals.multiply(plan.rateAt(Length.fromM(10), DOWN).mrPerHour(), BigDecimal.TWO));
        Length projected = plan.distanceFromReading(hot, Length.fromM(10), mrPerHour(2));
        assertThat(projected).isGreaterThan(plan.boundary(BoundaryKind.RESTRICTED, DOWN));
    }

    @Test
    void projectingAReadingRoundsAwayFromTheSource() {
        RadiationField plan = bare();
        Length projected = plan.distanceFromReading(mrPerHour(200), Length.fromM(1), mrPerHour(2));
        assertThat(projected).isEqualTo(Length.fromM(10));
    }

    @Test
    void projectingNeedsARealDistanceAndARealTarget() {
        RadiationField plan = bare();
        assertThatThrownBy(() -> plan.distanceFromReading(mrPerHour(200), Length.ZERO, mrPerHour(2)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> plan.distanceFromReading(mrPerHour(200), Length.fromM(1), DoseRate.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aReadingOfNothingProjectsToNoBoundary() {
        RadiationField plan = bare();
        assertThat(plan.distanceFromReading(DoseRate.ZERO, Length.fromM(1), mrPerHour(2)))
                .isEqualTo(Length.ZERO);
    }
}
