package io.github.rkaushikdev.swagebay.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The digits here were computed independently, at 28 significant digits, correctly rounded. */
class DecimalsTest {

    @ParameterizedTest
    @CsvSource({
        "2, 0.6931471805599453094172321215",
        "10, 2.302585092994045684017991455",
        "0.5, -0.6931471805599453094172321215",
        "1, 0"
    })
    void theLogarithmIsCorrectlyRounded(String value, String expected) {
        assertThat(Decimals.ln(new BigDecimal(value))).hasToString(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "-2, 0.1353352832366126918939994950",
        "-10, 0.00004539992976248485153559151556",
        "-0.5, 0.6065306597126334236037995350",
        "0, 1"
    })
    void theExponentialIsCorrectlyRounded(String value, String expected) {
        assertThat(Decimals.exp(new BigDecimal(value))).hasToString(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2, 1.414213562373095048801688724",
        "1.00, 1.0",
        "100, 10",
        "0.25, 0.5",
        "2.25, 1.5",
        "0.0004, 0.02",
        "1E+2, 1E+1",
        "12.0, 3.464101615137754587054892683",
        "0, 0"
    })
    void anExactRootKeepsHalfTheScaleAndAnInexactOneIsRounded(String value, String expected) {
        assertThat(Decimals.sqrt(new BigDecimal(value))).hasToString(expected);
    }

    @Test
    void theBaseTenLogarithmIsExactForAPowerOfTen() {
        assertThat(Decimals.log10(BigDecimal.TEN)).hasToString("1");
        assertThat(Decimals.log10(new BigDecimal("1000"))).hasToString("3");
        assertThat(Decimals.log10(BigDecimal.TWO)).hasToString("0.3010299956639811952137388947");
    }

    @Test
    void aWholePowerIsExactAndAFractionalOneGoesThroughTheLogarithm() {
        assertThat(Decimals.power(BigDecimal.TEN, BigDecimal.TWO)).hasToString("100");
        assertThat(Decimals.power(BigDecimal.TEN, new BigDecimal("2.0"))).hasToString("100");
        assertThat(Decimals.power(BigDecimal.TWO, BigDecimal.valueOf(-2))).isEqualByComparingTo("0.25");
        assertThat(Decimals.power(BigDecimal.TEN, new BigDecimal("0.5"))).isEqualTo(Decimals.sqrt(BigDecimal.TEN));
    }

    @Test
    void aChainedExpressionMatchesTheDecayArithmetic() {
        BigDecimal periods = Decimals.divide(new BigDecimal("-74"), new BigDecimal("73.83"));
        BigDecimal fraction = Decimals.exp(Decimals.multiply(periods, Decimals.ln(BigDecimal.TWO)));
        assertThat(fraction).hasToString("0.4992026206387105172697385901");
    }

    @Test
    void anExactQuotientKeepsTheScaleOfTheDividendLessTheDivisor() {
        assertThat(Decimals.divide(9525, 1000)).hasToString("9.525");
        assertThat(Decimals.divide(10000, 1000)).hasToString("10");
        assertThat(Decimals.divide(1, 3)).hasToString("0.3333333333333333333333333333");
    }

    @Test
    void roundingFollowsTheModeItIsNamedFor() {
        assertThat(Decimals.roundHalfUp(new BigDecimal("2.5"))).isEqualTo(3);
        assertThat(Decimals.roundHalfEven(new BigDecimal("2.5"))).isEqualTo(2);
        assertThat(Decimals.truncate(new BigDecimal("-2.7"))).isEqualTo(-2);
        assertThat(Decimals.floor(new BigDecimal("-2.2"))).isEqualTo(-3);
        assertThat(Decimals.ceiling(new BigDecimal("2.2"))).isEqualTo(3);
        assertThat(Decimals.halfUp(new BigDecimal("0.125"), 2)).hasToString("0.13");
        assertThat(Decimals.halfEven(new BigDecimal("0.125"), 2)).hasToString("0.12");
    }

    @Test
    void aPlainDecimalHasNoExponentAndNoTrailingZeros() {
        assertThat(Decimals.plain(new BigDecimal("100.00"))).hasToString("100");
        assertThat(Decimals.plain(new BigDecimal("2.50"))).hasToString("2.5");
        assertThat(Decimals.normalize(new BigDecimal("100"))).hasToString("1E+2");
        assertThat(Decimals.isWhole(new BigDecimal("3.000"))).isTrue();
        assertThat(Decimals.isWhole(new BigDecimal("3.5"))).isFalse();
        assertThat(Decimals.of(" 2.50 ")).hasToString("2.50");
    }

    @Test
    void theTranscendentalsRefuseOutsideTheirDomain() {
        assertThatThrownBy(() -> Decimals.ln(BigDecimal.ZERO)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Decimals.log10(BigDecimal.ONE.negate())).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Decimals.sqrt(BigDecimal.ONE.negate())).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Decimals.power(BigDecimal.ZERO, new BigDecimal("0.5")))
                .isInstanceOf(ArithmeticException.class);
    }
}
