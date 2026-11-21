package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.geometry.WeldMap.evenArcs;
import static io.github.rkaushikdev.swagebay.geometry.WeldMap.wrap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeldmapTest {

    private static final String DEGREE = Character.toString(176);

    private static Arc arc(String label, long start, long span) {
        return new Arc(label, BigDecimal.valueOf(start), BigDecimal.valueOf(span));
    }

    private static Arc arc(long start, long span) {
        return arc("1", start, span);
    }

    private static BigDecimal deg(long degrees) {
        return BigDecimal.valueOf(degrees);
    }

    @Test
    void bearingsFoldIntoOneTurn() {
        assertThat(wrap(deg(370))).isEqualByComparingTo("10");
        assertThat(wrap(deg(-10))).isEqualByComparingTo("350");
        assertThat(wrap(deg(360))).isEqualByComparingTo("0");
    }

    @Test
    void anArcKnowsWhereItEnds() {
        assertThat(arc(0, 120).endsAt()).isEqualByComparingTo("120");
        assertThat(arc(300, 120).endsAt()).isEqualByComparingTo("60");
    }

    @Test
    void anArcThatCrossesTwelveSaysSo() {
        assertThat(arc(330, 60).wraps()).isTrue();
        assertThat(arc(0, 120).wraps()).isFalse();
    }

    @Test
    void aWrappingArcIsOneArcAndNotTwo() {
        Arc crossing = arc(330, 60);
        assertThat(crossing.contains(deg(340))).isTrue();
        assertThat(crossing.contains(deg(0))).isTrue();
        assertThat(crossing.contains(deg(20))).isTrue();
        assertThat(crossing.contains(deg(100))).isFalse();
    }

    @Test
    void anArcIsClosedAtTheStartAndOpenAtTheEnd() {
        Arc span = arc(0, 120);
        assertThat(span.contains(deg(0))).isTrue();
        assertThat(span.contains(new BigDecimal("119.9"))).isTrue();
        assertThat(span.contains(deg(120))).isFalse();
    }

    @Test
    void anArcWithNoSpanOrNoLabelIsRefused() {
        assertThatThrownBy(() -> arc(0, 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> arc("  ", 0, 120)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> arc(0, 400)).isInstanceOf(ValidationError.class);
    }

    @Test
    void threeEvenFilmsCloseTheCircle() {
        WeldMap weld = evenArcs(3);
        assertThat(weld.isComplete()).isTrue();
        assertThat(weld.gaps()).isEmpty();
    }

    @Test
    void filmsThatDoNotReachLeaveAGapThatIsFound() {
        WeldMap weld = new WeldMap(List.of(arc("1", 0, 100), arc("2", 120, 100), arc("3", 240, 100)));
        List<Gap> gaps = weld.gaps();
        assertThat(gaps).hasSize(3);
        assertThat(gaps.get(0).startsAt()).isEqualByComparingTo("100");
    }

    @Test
    void aGapThatCrossesTwelveIsReportedAsOneGap() {
        WeldMap weld = new WeldMap(List.of(arc("1", 20, 300)));
        List<Gap> gaps = weld.gaps();
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).startsAt()).isEqualByComparingTo("320");
        assertThat(gaps.get(0).span()).isEqualByComparingTo("60");
        assertThat(gaps.get(0).describe()).isEqualTo("320.0" + DEGREE + " to 20.0" + DEGREE);
    }

    @Test
    void aSingleFilmRoundTheWholeWeldLeavesNothing() {
        WeldMap weld = new WeldMap(List.of(arc("1", 0, 360)));
        assertThat(weld.isComplete()).isTrue();
    }

    @Test
    void theMapSaysWhichFilmsCoverABearing() {
        WeldMap weld = evenArcs(3, deg(20));
        assertThat(weld.covers(deg(60))).containsExactly("1");
        assertThat(weld.covers(deg(125))).hasSize(2);
    }

    @Test
    void aBearingNothingReachesComesBackEmpty() {
        WeldMap weld = new WeldMap(List.of(arc("1", 0, 100)));
        assertThat(weld.covers(deg(200))).isEmpty();
    }

    @Test
    void theOverlapBetweenTwoFilmsIsMeasuredByWalking() {
        Arc left = arc("1", 0, 130);
        Arc right = arc("2", 120, 130);
        assertThat(left.overlapWith(right)).isEqualByComparingTo("10");
    }

    @Test
    void anOverlapThatCrossesTwelveIsMeasuredToo() {
        Arc left = arc("1", 300, 120);
        Arc right = arc("2", 350, 60);
        assertThat(left.overlapWith(right)).isPositive();
    }

    @Test
    void twoFilmsThatDoNotTouchOverlapByNothing() {
        assertThat(arc("1", 0, 100).overlapWith(arc("2", 200, 100))).isEqualByComparingTo("0");
    }

    @Test
    void theStretchesOnlyOneFilmReachesAreReported() {
        WeldMap weld = evenArcs(3, deg(20));
        List<Gap> thin = weld.singleCovered();
        assertThat(thin).isNotEmpty();
        assertThat(thin).allSatisfy(gap -> assertThat(gap.span()).isPositive());
    }

    @Test
    void aWeldWithNoOverlapAnywhereIsEntirelySingleCovered() {
        WeldMap weld = evenArcs(4, deg(0));
        assertThat(weld.isComplete()).isTrue();
        assertThat(weld.singleCovered()).hasSize(1);
        assertThat(weld.singleCovered().get(0).span()).isEqualByComparingTo("360");
    }

    @Test
    void aLayoutNeedsAtLeastOneFilm() {
        assertThatThrownBy(() -> evenArcs(0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> evenArcs(3, deg(-1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aWalkStepThatIsNotSmallIsRefused() {
        assertThatThrownBy(() -> evenArcs(3).gaps(deg(0))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> evenArcs(3).gaps(deg(30))).isInstanceOf(ValidationError.class);
    }

    @Test
    void anArcPrintsTheWayTheSketchIsLabelled() {
        assertThat(arc("2", 120, 130).describe()).isEqualTo("2 120" + DEGREE + " for 130" + DEGREE);
    }
}
