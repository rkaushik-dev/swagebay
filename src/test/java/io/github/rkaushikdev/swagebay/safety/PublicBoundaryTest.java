package io.github.rkaushikdev.swagebay.safety;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.sources.Devices.collimator;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PublicBoundaryTest {

    private static final BigDecimal DOWN = BigDecimal.ZERO;
    private static final Duration HOUR = Duration.fromHours(1);

    private static RadiationField bare() {
        return new RadiationField(IR192, curie(50));
    }

    @Test
    void aSourceOutAllHourGetsTheOrdinaryRestrictedBoundary() {
        assertThat(bare().publicBoundary(DOWN, HOUR)).isEqualTo(bare().boundary(BoundaryKind.RESTRICTED, DOWN));
    }

    @Test
    void sixMinutesInTheHourBringsTheRopeInByTheRootOfTen() {
        Length always = bare().boundary(BoundaryKind.RESTRICTED, DOWN);
        Length tenth = bare().publicBoundary(DOWN, Duration.fromMinutes(6));
        double ratio = always.um() / (double) tenth.um();
        assertThat(ratio).isBetween(Math.sqrt(10) - 0.0001, Math.sqrt(10) + 0.0001);
        assertThat(tenth).isLessThan(always);
    }

    @Test
    void atThePublicBoundaryTheBusiestHourComesToTwoMillirem() {
        Duration out = Duration.fromMinutes(15);
        Length boundary = bare().publicBoundary(DOWN, out);
        Dose inTheHour = bare().rateAt(boundary, DOWN).over(out);
        assertThat(inTheHour).isLessThanOrEqualTo(Dose.fromMrem(2));
        Dose justInside = bare().rateAt(new Length(boundary.um() - 1000), DOWN).over(out);
        assertThat(justInside).isGreaterThan(Dose.fromMrem(2));
    }

    @Test
    void aCollimatorPullsThePublicBoundaryInOffTheBeam() {
        RadiationField cup = new RadiationField(IR192, curie(50), collimator("w60"));
        RopeOff rope = RopeOff.forOccupancy(cup, Duration.fromMinutes(10));
        assertThat(rope.kind()).isSameAs(BoundaryKind.RESTRICTED);
        assertThat(rope.isDirectional()).isTrue();
        assertThat(rope.offBeam()).isLessThan(rope.downBeam());
        assertThat(rope.downBeam()).isLessThan(RopeOff.of(cup).downBeam());
    }

    @Test
    void theTimeOutHasToBePartOfOneHour() {
        assertThatThrownBy(() -> bare().publicBoundary(DOWN, Duration.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> bare().publicBoundary(DOWN, Duration.fromMinutes(61)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aFieldWithNoSourceHasNothingToRope() {
        RadiationField spent = new RadiationField(IR192, curie(0));
        assertThat(spent.publicBoundary(DOWN, HOUR)).isEqualTo(Length.ZERO);
        assertThatThrownBy(() -> RopeOff.forOccupancy(spent, HOUR)).isInstanceOf(TechniqueError.class);
    }
}
