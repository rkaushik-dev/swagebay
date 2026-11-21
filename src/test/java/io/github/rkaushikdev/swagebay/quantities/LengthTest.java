package io.github.rkaushikdev.swagebay.quantities;

import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.thou;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LengthTest {

    @Test
    void anInchIsExactlyTwentyFiveThousandFourHundredMicrometres() {
        assertThat(inch(1).um()).isEqualTo(25400);
        assertThat(inch("0.375").um()).isEqualTo(9525);
        assertThat(thou(1).um()).isEqualTo(25);
    }

    @Test
    void theRoundTripThroughBothSystemsIsLossless() {
        Length wall = inch("0.375");
        assertThat(wall.inch()).isEqualByComparingTo("0.375");
        assertThat(wall.mm()).isEqualByComparingTo("9.525");
        assertThat(Length.fromMm(wall.mm())).isEqualTo(wall);
    }

    @Test
    void millimetreValuesStayWhole() {
        assertThat(mm("9.53").um()).isEqualTo(9530);
        assertThat(mm(100).m()).isEqualByComparingTo("0.1");
        assertThat(Length.fromCm(61).um()).isEqualTo(610000);
        assertThat(Length.fromM("0.61").um()).isEqualTo(610000);
    }

    @Test
    void lengthsSortTheWayTheWireGaugeCardDoes() {
        List<Length> wires = List.of(mm("0.25"), mm("0.16"), mm("0.40"), mm("0.20"), mm("0.32"));
        assertThat(wires.stream().sorted().map(Length::mm).map(BigDecimal::stripTrailingZeros))
                .containsExactly(
                        new BigDecimal("0.16"),
                        new BigDecimal("0.2"),
                        new BigDecimal("0.25"),
                        new BigDecimal("0.32"),
                        new BigDecimal("0.4"));
    }

    @Test
    void additionAndSubtractionStayExact() {
        assertThat(inch("0.375").plus(inch("0.125"))).isEqualTo(inch("0.5"));
        assertThat(mm(10).minus(mm("9.53"))).isEqualTo(new Length(470));
        assertThat(mm(3).negate()).isEqualTo(mm(-3));
        assertThat(mm(-3).abs()).isEqualTo(mm(3));
    }

    @Test
    void scalingRoundsHalfAwayFromZero() {
        assertThat(new Length(101).times(new BigDecimal("0.5")).um()).isEqualTo(51);
        assertThat(new Length(-101).times(new BigDecimal("0.5")).um()).isEqualTo(-51);
        assertThat(mm(1).times(2).um()).isEqualTo(2000);
        assertThat(mm(1).times(2L).um()).isEqualTo(2000);
        assertThat(mm(1).dividedBy(3).um()).isEqualTo(333);
    }

    @Test
    void dividingByZeroIsRefused() {
        assertThatThrownBy(() -> mm(1).dividedBy(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aRatioIsDimensionlessAndExact() {
        assertThat(inch(24).ratio(inch(12))).isEqualByComparingTo("2");
        assertThatThrownBy(() -> mm(1).ratio(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void parseReadsTheUnitsAProcedureIsWrittenIn() {
        assertThat(Length.parse("9.53 mm").um()).isEqualTo(9530);
        assertThat(Length.parse("0.375 in").um()).isEqualTo(9525);
        assertThat(Length.parse("24\"").um()).isEqualTo(609600);
        assertThat(Length.parse("  610MM ").um()).isEqualTo(610000);
        assertThat(Length.parse("40 thou").um()).isEqualTo(1016);
        assertThat(Length.parse("40 mil").um()).isEqualTo(1016);
        assertThat(Length.parse("250 um").um()).isEqualTo(250);
        assertThat(Length.parse("1.5 m").um()).isEqualTo(1500000);
    }

    @Test
    void parseRefusesAnythingItCannotRead() {
        for (String bad : List.of("", "mm", "9.53", "9.53 kg", "nine mm", "9,53 mm")) {
            assertThatThrownBy(() -> Length.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    /** A length holds a whole number of micrometres; a fraction or a truth value does not compile. */
    @Test
    void micrometresMustBeWhole() {
        assertThat(Length.class.getRecordComponents()).hasSize(1);
        assertThat(Length.class.getRecordComponents()[0].getType()).isEqualTo(long.class);
    }

    @Test
    void presentationIsReadableOnATechniqueSheet() {
        assertThat(inch("0.375")).hasToString("9.53 mm");
        assertThat(inch("0.375").formatMm(3)).isEqualTo("9.525 mm");
        assertThat(mm("9.525").formatInch()).isEqualTo("0.375 in");
        assertThat(mm(1).repr()).isEqualTo("Length(1000)");
    }

    /** Adding or subtracting takes another length and nothing else, so a bare number does not compile. */
    @Test
    void aLengthWillNotCombineWithABareNumber() {
        List<Method> combining = Stream.of(Length.class.getMethods())
                .filter(m -> m.getName().equals("plus") || m.getName().equals("minus"))
                .toList();
        assertThat(combining).hasSize(2);
        assertThat(combining).allSatisfy(m -> assertThat(Arrays.asList(m.getParameterTypes()))
                .containsExactly(Length.class));
    }

    @Test
    void zeroIsSharedAndFalsyInTheUsefulWay() {
        assertThat(Length.ZERO.um()).isZero();
        assertThat(new Length(0)).isEqualTo(Length.ZERO);
        assertThat(mm(1).minus(mm(1))).isEqualTo(Length.ZERO);
    }
}
