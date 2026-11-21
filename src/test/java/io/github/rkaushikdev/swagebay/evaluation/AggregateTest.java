package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.STANDARD_WINDOW;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.aggregateIn;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.clippedLength;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.countInWorstWindow;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.fractionOfWeld;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.windowApplies;
import static io.github.rkaushikdev.swagebay.evaluation.Aggregates.worstWindow;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AggregateTest {

    private static Indication slag(long at, long length) {
        return new Indication(Kind.ESI, mm(at), mm(length), mm(1));
    }

    private static List<Indication> slags(int count, long pitch, long length) {
        return IntStream.range(0, count).mapToObj(i -> slag(i * pitch, length)).toList();
    }

    @Test
    void theWindowIsTwelveInches() {
        assertThat(inch(12)).isEqualTo(STANDARD_WINDOW);
        assertThat(STANDARD_WINDOW).isEqualTo(mm("304.8"));
    }

    @Test
    void aWeldWithNothingInItHasAnEmptyWorstWindow() {
        Window found = worstWindow(List.of(), mm(1000));
        assertThat(found.aggregate()).isEqualTo(Length.ZERO);
        assertThat(found.count()).isZero();
    }

    @Test
    void theSameTotalSpreadOutAndBunchedUpReadDifferently() {
        List<Indication> spread = slags(8, 130, 12);
        List<Indication> bunched = slags(8, 35, 12);
        Length weld = mm(1100);
        assertThat(worstWindow(spread, weld).aggregate())
                .isLessThan(worstWindow(bunched, weld).aggregate());
        assertThat(worstWindow(bunched, weld).aggregate()).isEqualTo(mm(96));
    }

    @Test
    void theWindowFindsTheWorstPositionNotTheFirstOne() {
        List<Indication> indications = List.of(slag(10, 10), slag(700, 40), slag(760, 40));
        Window found = worstWindow(indications, mm(1200));
        assertThat(found.aggregate()).isEqualTo(mm(80));
        assertThat(found.count()).isEqualTo(2);
        assertThat(found.startsAt()).isGreaterThanOrEqualTo(mm(490));
    }

    @Test
    void anIndicationStraddlingTheEdgeIsClipped() {
        Indication longOne = slag(0, 400);
        assertThat(clippedLength(longOne, mm(0), mm(100))).isEqualTo(mm(100));
        assertThat(clippedLength(longOne, mm(350), mm(500))).isEqualTo(mm(50));
        assertThat(clippedLength(longOne, mm(500), mm(600))).isEqualTo(Length.ZERO);
    }

    @Test
    void aWindowNeverCountsMoreThanItsOwnLength() {
        Window found = worstWindow(List.of(slag(0, 900)), mm(1000));
        assertThat(found.aggregate()).isEqualTo(STANDARD_WINDOW);
    }

    @Test
    void theAggregateInANamedWindowCountsWhatContributed() {
        List<Indication> indications = List.of(slag(100, 20), slag(200, 30), slag(900, 10));
        WindowTotal inside = aggregateIn(indications, mm(0), mm(400));
        assertThat(inside.total()).isEqualTo(mm(50));
        assertThat(inside.count()).isEqualTo(2);
    }

    @Test
    void aWindowWithNoWidthIsRefused() {
        assertThatThrownBy(() -> aggregateIn(List.of(), mm(100), mm(100))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> worstWindow(List.of(slag(0, 10)), mm(1000), Length.ZERO))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> worstWindow(List.of(slag(0, 10)), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aShortWeldUsesItsOwnLengthAsTheWindow() {
        Length shortWeld = mm(200);
        List<Indication> indications = List.of(slag(0, 20), slag(150, 20));
        Window found = worstWindow(indications, shortWeld);
        assertThat(found.length()).isEqualTo(shortWeld);
        assertThat(found.aggregate()).isEqualTo(mm(40));
        assertThat(windowApplies(shortWeld)).isFalse();
    }

    @Test
    void aLongWeldUsesTheStandardWindow() {
        assertThat(windowApplies(mm(400))).isTrue();
        assertThat(windowApplies(STANDARD_WINDOW)).isTrue();
        assertThat(windowApplies(mm(300))).isFalse();
    }

    @Test
    void theShortWeldRuleComparesAFractionInstead() {
        assertThat(fractionOfWeld(mm(16), mm(200))).isEqualByComparingTo("0.08");
        assertThat(fractionOfWeld(mm(10), mm(200))).isLessThan(new BigDecimal("0.08"));
        assertThatThrownBy(() -> fractionOfWeld(mm(10), Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theWorstWindowNeverRunsOffTheEndOfTheWeld() {
        Window found = worstWindow(List.of(slag(950, 40)), mm(1000));
        assertThat(found.endsAt()).isLessThanOrEqualTo(mm(1000));
        assertThat(found.aggregate()).isEqualTo(mm(40));
    }

    @Test
    void someRulesCountIndicationsRatherThanMeasuringThem() {
        assertThat(countInWorstWindow(slags(8, 60, 5), mm(1000))).isEqualTo(6);
        assertThat(countInWorstWindow(List.of(), mm(1000))).isZero();
    }

    @Test
    void theCountFindsTheWorstPositionToo() {
        List<Indication> indications = List.of(slag(0, 5), slag(600, 5), slag(620, 5), slag(640, 5), slag(660, 5));
        assertThat(countInWorstWindow(indications, mm(1200))).isEqualTo(4);
    }

    @Test
    void theWindowPrintsTheWayTheReportReads() {
        Window found = worstWindow(List.of(slag(100, 20)), mm(1000));
        assertThat(found.describe()).contains("20.0 mm in the 305 mm from");
    }

    @Test
    void twoIndicationsOnTheSameStretchAreCountedOnce() {
        WindowTotal inside = aggregateIn(List.of(slag(100, 40), slag(100, 40)), mm(0), mm(400));
        assertThat(inside.total()).isEqualTo(mm(40));
        assertThat(inside.count()).isEqualTo(2);
    }

    @Test
    void partlyOverlappingIndicationsCountTheStretchTheyCover() {
        WindowTotal inside = aggregateIn(List.of(slag(100, 40), slag(120, 40)), mm(0), mm(400));
        assertThat(inside.total()).isEqualTo(mm(60));
    }

    @Test
    void theAggregateNeverExceedsTheWindowItSitsIn() {
        Window found = worstWindow(slags(4, 0, 400), mm(1000));
        assertThat(found.aggregate()).isLessThanOrEqualTo(found.length());
    }

    @Test
    void touchingIndicationsAreNotDoubleCountedAtTheJoin() {
        WindowTotal inside = aggregateIn(List.of(slag(100, 20), slag(120, 20)), mm(0), mm(400));
        assertThat(inside.total()).isEqualTo(mm(40));
    }

    @Test
    void indicationsThatDoNotTouchStillAddUp() {
        WindowTotal inside = aggregateIn(List.of(slag(100, 20), slag(200, 20)), mm(0), mm(400));
        assertThat(inside.total()).isEqualTo(mm(40));
    }
}
