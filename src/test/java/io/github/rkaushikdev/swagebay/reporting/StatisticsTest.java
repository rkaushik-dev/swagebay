package io.github.rkaushikdev.swagebay.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.reporting.Statistics.Tally;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StatisticsTest {

    private static final Map<String, Tally> COUNTS = Map.of("w-19", new Tally(4, 100), "w-04", new Tally(1, 3));

    private static Period period(int month, int repaired, int shot) {
        return new Period(2024, month, new Rate(repaired, shot));
    }

    /** Six months of four repairs in a hundred. */
    private static List<Period> steadyHistory() {
        return IntStream.rangeClosed(1, 6)
                .mapToObj(month -> period(month, 4, 100))
                .toList();
    }

    private static Band fourPercentBand() {
        return new Band(new BigDecimal("0.04"), new BigDecimal("0.02"), new BigDecimal("0.06"));
    }

    @Test
    void aRateCarriesTheCountItCameFrom() {
        Rate rate = new Rate(4, 100);
        assertThat(rate.value()).isEqualByComparingTo("0.04");
        assertThat(rate.describe()).isEqualTo("4.00% on 100");
    }

    @Test
    void aRateOnTooFewWeldsIsNotReportedAsARate() {
        Rate small = new Rate(1, 19);
        assertThat(small.isReportable()).isFalse();
        assertThat(small.describe()).isEqualTo("1 of 19, too few to rate");
        assertThat(new Rate(1, Statistics.MINIMUM_DENOMINATOR).isReportable()).isTrue();
    }

    @Test
    void theSamePercentageReadsDifferentlyAtTwoDenominators() {
        Rate big = new Rate(40, 1000);
        Rate small = new Rate(1, 25);
        assertThat(big.value()).isEqualByComparingTo(small.value());
        assertThat(big.describe()).contains("on 1000");
        assertThat(small.describe()).contains("on 25");
    }

    @Test
    void moreRepairsThanWeldsIsRefused() {
        assertThatThrownBy(() -> new Rate(5, 4)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Rate(-1, 4)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aShopWithNoWeldsHasARateOfNothingRatherThanACrash() {
        assertThat(new Rate(0, 0).value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new Rate(0, 0).isReportable()).isFalse();
    }

    @Test
    void poolingIsNotTheAverageOfTheRates() {
        Rate pooled = Statistics.combine(List.of(new Rate(1, 100), new Rate(1, 4)));
        assertThat(pooled.repaired()).isEqualTo(2);
        assertThat(pooled.shot()).isEqualTo(104);
        BigDecimal average = Decimals.divide(Decimals.add(new BigDecimal("0.01"), new BigDecimal("0.25")), 2);
        assertThat(pooled.value()).isLessThan(average);
    }

    @Test
    void aMonthOutsideTheCalendarIsRefused() {
        assertThatThrownBy(() -> new Period(2024, 13, new Rate(1, 100))).isInstanceOf(ValidationError.class);
        assertThat(period(3, 1, 100).label()).isEqualTo("2024-03");
    }

    @Test
    void theBandComesFromTheHistoryAndNotFromATarget() {
        Band band = Statistics.bandFrom(steadyHistory());
        assertThat(band.centre()).isEqualByComparingTo("0.04");
        assertThat(band.low()).isLessThan(band.centre());
        assertThat(band.centre()).isLessThan(band.high());
    }

    @Test
    void aWiderSpreadMakesAWiderBand() {
        Band tight = Statistics.bandFrom(steadyHistory(), new BigDecimal("1.2"));
        Band loose = Statistics.bandFrom(steadyHistory(), BigDecimal.valueOf(2));
        assertThat(loose.high()).isGreaterThan(tight.high());
        assertThat(loose.low()).isLessThan(tight.low());
    }

    @Test
    void aSpreadOfOneOrLessIsRefused() {
        List<Period> history = steadyHistory();
        assertThatThrownBy(() -> Statistics.bandFrom(history, BigDecimal.ONE)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aBandCannotBeSetFromMonthsThatAreTooSmallToRate() {
        List<Period> small = IntStream.rangeClosed(1, 6)
                .mapToObj(month -> period(month, 0, 5))
                .toList();
        assertThatThrownBy(() -> Statistics.bandFrom(small)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theMonthsOutsideTheBandAreTheOnesWorthReading() {
        List<Period> history = steadyHistory();
        Band band = Statistics.bandFrom(history);
        List<Period> withABadMonth = new ArrayList<>(history);
        withABadMonth.add(period(7, 12, 100));
        assertThat(Statistics.excursions(withABadMonth, band))
                .extracting(Period::label)
                .containsExactly("2024-07");
    }

    @Test
    void aMonthTooSmallToRateIsNeverAnExcursion() {
        assertThat(Statistics.excursions(List.of(period(7, 1, 5)), fourPercentBand()))
                .isEmpty();
    }

    @Test
    void aQuietMonthBelowTheBandIsAnExcursionToo() {
        assertThat(Statistics.excursions(List.of(period(7, 0, 100)), fourPercentBand()))
                .hasSize(1);
    }

    @Test
    void threeMonthsEachWorseThanTheLastIsARise() {
        assertThat(Statistics.rising(List.of(period(1, 2, 100), period(2, 4, 100), period(3, 6, 100))))
                .isTrue();
    }

    @Test
    void aMonthThatImprovesBreaksTheRun() {
        assertThat(Statistics.rising(List.of(period(1, 2, 100), period(2, 4, 100), period(3, 3, 100))))
                .isFalse();
    }

    @Test
    void aShortHistoryCannotBeCalledARise() {
        assertThat(Statistics.rising(List.of(period(1, 2, 100), period(2, 4, 100))))
                .isFalse();
        assertThat(Statistics.rising(List.of(period(1, 2, 100), period(2, 4, 100)), 2))
                .isTrue();
    }

    @Test
    void aRunOfOneIsRefused() {
        List<Period> history = List.of(period(1, 2, 100));
        assertThatThrownBy(() -> Statistics.rising(history, 1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void monthsTooSmallToRateAreLeftOutOfTheRun() {
        List<Period> history = List.of(period(1, 2, 100), period(2, 1, 3), period(3, 4, 100), period(4, 6, 100));
        assertThat(Statistics.rising(history)).isTrue();
    }

    @Test
    void perWelderReportsTheRateAndTheCountWithoutRanking() {
        Map<String, Rate> rates = Statistics.byWelder(COUNTS);
        assertThat(rates.keySet()).containsExactly("w-04", "w-19");
        assertThat(rates.get("w-04").isReportable()).isFalse();
        assertThat(rates.get("w-19").describe()).isEqualTo("4.00% on 100");
    }

    @Test
    void onlyTheWeldersWithEnoughWeldsAreReportable() {
        assertThat(Statistics.reportableWelders(COUNTS)).containsExactly("w-19");
    }

    @Test
    void thePercentageIsRoundedTheWayAReportRounds() {
        assertThat(Statistics.asPercentage(new Rate(1, 3))).isEqualByComparingTo("33.33");
        assertThat(Statistics.asPercentage(new Rate(1, 8), 1)).isEqualByComparingTo("12.5");
    }

    @Test
    void aBandPrintsTheWayTheMonthlyNoteReads() {
        assertThat(fourPercentBand().describe()).isEqualTo("4.00% band 2.00% to 6.00%");
    }
}
