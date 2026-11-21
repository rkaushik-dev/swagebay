package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.geometry.Coverage.DEFAULT_PATH_ALLOWANCE;
import static io.github.rkaushikdev.swagebay.geometry.Coverage.incidenceAngle;
import static io.github.rkaushikdev.swagebay.geometry.Coverage.pathFactor;
import static io.github.rkaushikdev.swagebay.geometry.Coverage.planCoverage;
import static io.github.rkaushikdev.swagebay.geometry.Coverage.standoffForExposures;
import static io.github.rkaushikdev.swagebay.geometry.Coverage.usableHalfArc;
import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoverageTest {

    private static final BigDecimal LOOSE = new BigDecimal("1.6");

    private static Joint line() {
        return PipeSchedule.girthWeld("12", "40", STEEL);
    }

    private static Length radius() {
        return line().od().dividedBy(2);
    }

    @Test
    void theBeamIsSquareOnAtTheMiddleOfTheFilm() {
        assertThat(incidenceAngle(radius(), mm(600), 0)).isEqualTo(0.0);
        assertThat(pathFactor(radius(), mm(600), 0)).isEqualByComparingTo("1");
    }

    @Test
    void theAngleOpensUpRoundThePipe() {
        List<Double> angles = Stream.of(0, 10, 20, 30, 45)
                .map(phi -> incidenceAngle(radius(), mm(600), phi))
                .toList();
        assertThat(angles).isSorted();
        assertThat(angles.get(1)).isGreaterThan(12.0).isLessThan(13.0);
    }

    @Test
    void thePathGetsLongerWithTheAngle() {
        List<BigDecimal> factors = Stream.of(0, 10, 20, 30)
                .map(phi -> pathFactor(radius(), mm(600), phi))
                .toList();
        assertThat(factors).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(factors.get(2)).isGreaterThan(new BigDecimal("1.10"));
    }

    @Test
    void aSourceFurtherBackBendsTheRayLess() {
        double near = incidenceAngle(radius(), mm(200), 20);
        double far = incidenceAngle(radius(), mm(2000), 20);
        assertThat(far).isLessThan(near);
    }

    @Test
    void aGrazingRayIsRefusedRatherThanReturningAHugeFactor() {
        assertThatThrownBy(() -> pathFactor(radius(), mm(600), 120)).isInstanceOf(TechniqueError.class);
    }

    @Test
    void theGeometryNeedsAPipeAndAStandoff() {
        assertThatThrownBy(() -> incidenceAngle(Length.ZERO, mm(600), 10)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> incidenceAngle(mm(100), Length.ZERO, 10)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theUsableArcIsWhereThePathStaysInsideTheAllowance() {
        Joint joint = line();
        BigDecimal half = usableHalfArc(joint, mm(600));
        Length radius = joint.od().dividedBy(2);
        assertThat(pathFactor(radius, mm(600), half.doubleValue())).isLessThanOrEqualTo(DEFAULT_PATH_ALLOWANCE);
        assertThat(pathFactor(radius, mm(600), half.doubleValue() + 1)).isGreaterThan(DEFAULT_PATH_ALLOWANCE);
    }

    @Test
    void aLooserAllowanceBuysAWiderArc() {
        Joint joint = line();
        assertThat(usableHalfArc(joint, mm(600), LOOSE)).isGreaterThan(usableHalfArc(joint, mm(600)));
    }

    @Test
    void backingTheSourceOffBuysAWiderArcToo() {
        Joint joint = line();
        assertThat(usableHalfArc(joint, mm(600), LOOSE)).isGreaterThan(usableHalfArc(joint, mm(200), LOOSE));
    }

    @Test
    void anAllowanceOfOneOrLessIsRefused() {
        assertThatThrownBy(() -> usableHalfArc(line(), mm(600), BigDecimal.ONE)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> usableHalfArc(line(), mm(600), new BigDecimal("0.9")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theCodeMinimumIsAFloorAndGeometryUsuallyAsksForMore() {
        CoveragePlan plan = planCoverage(line(), mm(600), 3);
        assertThat(plan.exposures()).isEqualTo(10);
        assertThat(plan.exposures()).isGreaterThan(3);
    }

    @Test
    void thePlannedExposuresReallyDoOverlap() {
        CoveragePlan plan = planCoverage(line(), mm(600), 3);
        assertThat(plan.hasOverlap()).isTrue();
        assertThat(plan.usableHalfArcDegrees().multiply(BigDecimal.valueOf(2))).isGreaterThan(plan.separationDegrees());
        assertThat(plan.overlapDegrees()).isPositive();
    }

    @Test
    void aLooserAllowanceCostsFewerExposures() {
        CoveragePlan strict = planCoverage(line(), mm(600), 3);
        CoveragePlan loose = planCoverage(line(), mm(600), 3, LOOSE);
        assertThat(loose.exposures()).isLessThan(strict.exposures());
        assertThat(loose.exposures()).isEqualTo(5);
    }

    @Test
    void theCodeMinimumWinsWhenTheGeometryIsGenerous() {
        CoveragePlan plan = planCoverage(line(), mm(3000), 3, new BigDecimal("2.0"));
        assertThat(plan.exposures()).isEqualTo(4);
        assertThat(plan.separationDegrees()).isEqualByComparingTo("90");
    }

    @Test
    void aPlanNeedsAPositiveCodeMinimum() {
        assertThatThrownBy(() -> planCoverage(line(), mm(600), 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void workingBackwardsToTheStandoffAShotCountNeeds() {
        Joint joint = line();
        Length standoff = standoffForExposures(joint, 4, LOOSE);
        assertThat(usableHalfArc(joint, standoff, LOOSE)).isGreaterThanOrEqualTo(BigDecimal.valueOf(45));
        assertThat(planCoverage(joint, standoff, 4, LOOSE).exposures()).isEqualTo(4);
    }

    @Test
    void fewerExposuresNeedTheSourceFurtherBack() {
        Joint joint = line();
        assertThat(standoffForExposures(joint, 4, LOOSE)).isGreaterThan(standoffForExposures(joint, 6, LOOSE));
    }

    @Test
    void threeExposuresAreUnreachableUnderTheOfficeAllowance() {
        assertThatThrownBy(() -> standoffForExposures(line(), 3))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("exposures", 3));
    }

    @Test
    void aStandoffNeedsAPositiveExposureCount() {
        assertThatThrownBy(() -> standoffForExposures(line(), 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void thePlanPrintsTheWayTheSheetReads() {
        CoveragePlan plan = planCoverage(line(), mm(600), 3, LOOSE);
        assertThat(plan.describe()).isEqualTo("5 exposures 72.0 degrees apart, each carrying 83.5 degrees");
    }
}
