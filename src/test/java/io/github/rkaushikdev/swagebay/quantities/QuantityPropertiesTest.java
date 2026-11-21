package io.github.rkaushikdev.swagebay.quantities;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.testing.Samples;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The laws of the quantity types, checked over generated values.
 *
 * <p>The example based tests pin the values the trade quotes; these pin the laws the rest of the
 * package assumes without saying so: that a parse round trips, that ordering agrees with the
 * stored integer, that scaling by one changes nothing. The rounding law came from a length that
 * read 9.52 mm on the sheet and 9.53 mm in the database, and the parse law from a procedure line
 * with a capital MM in it.
 */
class QuantityPropertiesTest {

    static Stream<Arguments> micrometres() {
        return Samples.longs(-10_000_000, 10_000_000);
    }

    static Stream<Arguments> micrometrePairs() {
        return Samples.longPairs(-10_000_000, 10_000_000);
    }

    static Stream<Arguments> micrometreTriples() {
        return Samples.longTriples(-10_000_000, 10_000_000);
    }

    static Stream<Arguments> positiveMicrometres() {
        return Samples.longs(1, 10_000_000);
    }

    static Stream<Arguments> positiveMicrometrePairs() {
        return Samples.longPairs(1, 10_000_000);
    }

    static Stream<Arguments> becquerels() {
        return Samples.longs(0, 1_000_000_000_000_000L);
    }

    static Stream<Arguments> becquerelPairs() {
        return Samples.longPairs(0, 1_000_000_000_000_000L);
    }

    static Stream<Arguments> milliseconds() {
        return Samples.longs(0, 10_000_000_000L);
    }

    static Stream<Arguments> nanosieverts() {
        return Samples.longs(0, 1_000_000_000_000L);
    }

    static Stream<Arguments> ratesAndSpans() {
        return Samples.longPairs(1, 1_000_000_000_000L, 1, 10_000_000);
    }

    static Stream<Arguments> ratesAndBudgets() {
        return Samples.longPairs(1, 1_000_000_000, 1, 1_000_000_000);
    }

    @ParameterizedTest
    @MethodSource("micrometres")
    void aLengthKeepsTheIntegerItWasGiven(long um) {
        assertThat(new Length(um).um()).isEqualTo(um);
    }

    @ParameterizedTest
    @MethodSource("micrometrePairs")
    void lengthsOrderTheWayTheirMicrometresDo(long left, long right) {
        assertThat(new Length(left).compareTo(new Length(right)) < 0).isEqualTo(left < right);
    }

    @ParameterizedTest
    @MethodSource("micrometrePairs")
    void additionIsCommutative(long left, long right) {
        assertThat(new Length(left).plus(new Length(right))).isEqualTo(new Length(right).plus(new Length(left)));
    }

    @ParameterizedTest
    @MethodSource("micrometreTriples")
    void additionIsAssociative(long a, long b, long c) {
        Length first = new Length(a).plus(new Length(b)).plus(new Length(c));
        Length second = new Length(a).plus(new Length(b).plus(new Length(c)));
        assertThat(first).isEqualTo(second);
    }

    @ParameterizedTest
    @MethodSource("micrometrePairs")
    void subtractionUndoesAddition(long left, long right) {
        assertThat(new Length(left).plus(new Length(right)).minus(new Length(right)))
                .isEqualTo(new Length(left));
    }

    @ParameterizedTest
    @MethodSource("micrometres")
    void scalingByOneChangesNothing(long um) {
        assertThat(new Length(um).times(1)).isEqualTo(new Length(um));
        assertThat(new Length(um).times(BigDecimal.ONE)).isEqualTo(new Length(um));
    }

    @ParameterizedTest
    @MethodSource("micrometres")
    void negatingTwiceReturnsTheLengthItStartedAt(long um) {
        Length once = new Length(um).negate();
        assertThat(once.negate()).isEqualTo(new Length(um));
    }

    @ParameterizedTest
    @MethodSource("micrometres")
    void theAbsoluteValueIsNeverNegative(long um) {
        assertThat(new Length(um).abs().um()).isNotNegative();
    }

    @ParameterizedTest
    @MethodSource("positiveMicrometres")
    void aLengthRoundTripsThroughMillimetres(long um) {
        Length value = new Length(um);
        assertThat(Length.fromMm(value.mm())).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("positiveMicrometres")
    void aLengthRoundTripsThroughItsOwnMillimetreText(long um) {
        Length value = new Length(um);
        assertThat(Length.parse(value.mm() + " mm")).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("positiveMicrometres")
    void aLengthRoundTripsThroughInches(long um) {
        Length value = new Length(um);
        assertThat(Length.fromInch(value.inch())).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("positiveMicrometres")
    void theUnitInAParsedLengthIsCaseInsensitive(long um) {
        Length value = new Length(um);
        assertThat(Length.parse(value.mm() + " MM")).isEqualTo(value);
        assertThat(Length.parse(value.mm() + " mm")).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("positiveMicrometrePairs")
    void aRatioIsTheInverseOfTheOtherWayRound(long left, long right) {
        BigDecimal forward = new Length(left).ratio(new Length(right));
        BigDecimal back = new Length(right).ratio(new Length(left));
        BigDecimal product = forward.multiply(back, new MathContext(28));
        assertThat(product.subtract(BigDecimal.ONE).abs()).isLessThan(new BigDecimal("1e-20"));
    }

    @ParameterizedTest
    @MethodSource("becquerels")
    void anActivityRoundTripsThroughCuries(long bq) {
        Activity value = new Activity(bq);
        assertThat(Activity.fromCurie(value.curie())).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("becquerelPairs")
    void anActivityNeverGoesNegativeThroughSubtraction(long left, long right) {
        long high = Math.max(left, right);
        long low = Math.min(left, right);
        assertThat(new Activity(high).minus(new Activity(low)).bq()).isNotNegative();
    }

    @ParameterizedTest
    @MethodSource("becquerelPairs")
    void activitiesOrderTheWayTheirBecquerelsDo(long left, long right) {
        assertThat(new Activity(left).compareTo(new Activity(right)) < 0).isEqualTo(left < right);
    }

    @ParameterizedTest
    @MethodSource("milliseconds")
    void aDurationRoundTripsThroughSeconds(long ms) {
        Duration value = new Duration(ms);
        assertThat(Duration.fromSeconds(value.seconds())).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("milliseconds")
    void roundingUpToASecondNeverShortensADuration(long ms) {
        Duration value = new Duration(ms);
        assertThat(value.roundUpToSecond()).isGreaterThanOrEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("milliseconds")
    void roundingUpLandsOnAWholeSecond(long ms) {
        assertThat(new Duration(ms).roundUpToSecond().ms() % 1000).isZero();
    }

    @ParameterizedTest
    @MethodSource("milliseconds")
    void aDurationThatIsAlreadyWholeIsLeftAlone(long ms) {
        Duration whole = new Duration(ms - (ms % 1000));
        assertThat(whole.roundUpToSecond()).isEqualTo(whole);
    }

    @ParameterizedTest
    @MethodSource("nanosieverts")
    void aDoseRoundTripsThroughMicrosieverts(long nsv) {
        Dose value = new Dose(nsv);
        assertThat(Dose.fromUsv(value.usv())).isEqualTo(value);
    }

    /** Checked for every span, including the ones short enough to round to no dose at all. */
    @ParameterizedTest
    @MethodSource("ratesAndSpans")
    void aRateOverASpanAndBackAgreesWithItself(long nsvPerHour, long ms) {
        DoseRate rate = new DoseRate(nsvPerHour);
        Dose taken = rate.over(new Duration(ms));
        assertThat((double) taken.nsv()).isLessThanOrEqualTo(nsvPerHour * (ms / 3_600_000.0) + 1);
    }

    @ParameterizedTest
    @MethodSource("ratesAndBudgets")
    void aStayTimeNeverOverspendsTheBudget(long nsvPerHour, long budgetNsv) {
        DoseRate rate = new DoseRate(nsvPerHour);
        Dose budget = new Dose(budgetNsv);
        Duration stay = rate.stayTime(budget);
        assertThat(rate.over(stay)).isLessThanOrEqualTo(budget);
    }
}
