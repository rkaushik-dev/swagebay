package io.github.rkaushikdev.swagebay.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FloatsTest {

    @ParameterizedTest
    @CsvSource({
        "1e16, 1e+16",
        "1e-5, 1e-05",
        "0.0001, 0.0001",
        "100.0, 100.0",
        "1234567890123456.0, 1234567890123456.0",
        "-2.5, -2.5",
        "0.3333333333333333, 0.3333333333333333",
        "1.25e-7, 1.25e-07",
        "0.0, 0.0",
        "-0.0, -0.0"
    })
    void aDoubleIsWrittenAsItsShortestText(double value, String expected) {
        assertThat(Floats.repr(value)).isEqualTo(expected);
    }

    @Test
    void theNonFiniteValuesHaveNames() {
        assertThat(Floats.repr(Double.POSITIVE_INFINITY)).isEqualTo("inf");
        assertThat(Floats.repr(Double.NEGATIVE_INFINITY)).isEqualTo("-inf");
        assertThat(Floats.repr(Double.NaN)).isEqualTo("nan");
        assertThatThrownBy(() -> Floats.decimal(Double.NaN)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void roundingIsJudgedOnTheExactBinaryValue() {
        assertThat(Floats.fixed(2.675, 2)).isEqualTo("2.67");
        assertThat(Floats.fixed(-0.001, 2)).isEqualTo("-0.00");
        assertThat(Floats.fixed(Double.NaN, 2)).isEqualTo("nan");
        assertThat(Floats.round(2.5)).isEqualTo(2);
        assertThat(Floats.round(3.5)).isEqualTo(4);
        assertThat(Floats.round(2.675, 2)).isEqualTo(2.67);
        assertThat(Floats.round(Double.POSITIVE_INFINITY, 2)).isInfinite();
        assertThat(Floats.decimal(0.1)).hasToString("0.1");
    }
}
