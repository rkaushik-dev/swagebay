package io.github.rkaushikdev.swagebay.quantities;

import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DurationTest {

    @Test
    void theThreeConstructorsAgree() {
        assertThat(seconds(90)).isEqualTo(minutes("1.5"));
        assertThat(Duration.fromHours(1)).isEqualTo(minutes(60));
        assertThat(seconds("0.25").ms()).isEqualTo(250);
    }

    @Test
    void exposuresSortByLength() {
        List<Duration> shots = List.of(seconds(45), minutes(2), seconds(90), minutes("0.5"));
        assertThat(shots.stream()
                        .sorted()
                        .map(s -> s.seconds().stripTrailingZeros().toPlainString()))
                .containsExactly("30", "45", "90", "120");
    }

    @Test
    void arithmeticStaysInTheType() {
        assertThat(seconds(30).plus(seconds(45))).isEqualTo(seconds(75));
        assertThat(minutes(2).minus(seconds(30))).isEqualTo(seconds(90));
        assertThat(seconds(30).times(4)).isEqualTo(minutes(2));
        assertThat(seconds(30).times(4L)).isEqualTo(minutes(2));
        assertThat(minutes(2).dividedBy(4)).isEqualTo(seconds(30));
    }

    @Test
    void aDurationCannotBeDrivenNegative() {
        assertThatThrownBy(() -> new Duration(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> seconds(30).minus(minutes(2))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> seconds(30).times(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> seconds(30).dividedBy(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theRatioOfTwoDurationsIsTheExposureFactor() {
        assertThat(minutes(2).ratio(seconds(30))).isEqualByComparingTo("4");
        assertThatThrownBy(() -> seconds(1).ratio(Duration.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void clockNotationIsReadTheWayItIsWritten() {
        assertThat(Duration.parse("1:40")).isEqualTo(seconds(100));
        assertThat(Duration.parse("0:30")).isEqualTo(seconds(30));
        assertThat(Duration.parse("1:02:30")).isEqualTo(seconds(3750));
        assertThat(Duration.parse("2:07.5")).isEqualTo(seconds("127.5"));
    }

    @Test
    void compoundNotationIsReadToo() {
        assertThat(Duration.parse("90s")).isEqualTo(seconds(90));
        assertThat(Duration.parse("1m 40s")).isEqualTo(seconds(100));
        assertThat(Duration.parse("2h")).isEqualTo(minutes(120));
        assertThat(Duration.parse("1h 30m")).isEqualTo(minutes(90));
        assertThat(Duration.parse(" 45 S ")).isEqualTo(seconds(45));
    }

    @Test
    void parseRefusesNonsenseAndOutOfRangeClocks() {
        for (String bad : List.of("", "later", "1:70", "1:60:00", "90", "1:2:3:4")) {
            assertThatThrownBy(() -> Duration.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void roundingUpToAWholeSecondNeverShortensAnExposure() {
        assertThat(seconds("30.001").roundUpToSecond()).isEqualTo(seconds(31));
        assertThat(seconds(30).roundUpToSecond()).isEqualTo(seconds(30));
        assertThat(Duration.ZERO.roundUpToSecond()).isEqualTo(Duration.ZERO);
    }

    @Test
    void theTimerStringIsWhatTheCrewDialsIn() {
        assertThat(seconds(100).asTimer()).isEqualTo("1:40");
        assertThat(seconds(9).asTimer()).isEqualTo("0:09");
        assertThat(seconds("89.2").asTimer()).isEqualTo("1:30");
        assertThat(minutes(75).asTimer()).isEqualTo("1:15:00");
        assertThat(seconds(45)).hasToString("0:45");
    }

    /** A duration holds a whole number of milliseconds; a fraction or a truth value does not compile. */
    @Test
    void millisecondsMustBeWhole() {
        assertThat(Duration.class.getRecordComponents()).hasSize(1);
        assertThat(Duration.class.getRecordComponents()[0].getType()).isEqualTo(long.class);
    }

    /** Adding or subtracting takes another duration and nothing else, so a bare number does not compile. */
    @Test
    void aDurationWillNotCombineWithABareNumber() {
        List<Method> combining = Stream.of(Duration.class.getMethods())
                .filter(m -> m.getName().equals("plus") || m.getName().equals("minus"))
                .toList();
        assertThat(combining).hasSize(2);
        assertThat(combining).allSatisfy(m -> assertThat(Arrays.asList(m.getParameterTypes()))
                .containsExactly(Duration.class));
        assertThat(new Duration(250).repr()).isEqualTo("Duration(250)");
    }
}
