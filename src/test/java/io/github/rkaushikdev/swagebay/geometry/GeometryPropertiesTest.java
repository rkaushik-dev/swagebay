package io.github.rkaushikdev.swagebay.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.evaluation.Aggregates;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.evaluation.Window;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.testing.MoreSamples;
import io.github.rkaushikdev.swagebay.testing.Samples;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Properties of the geometry and the aggregate window, checked over generated values.
 *
 * <p>The window rule is the one worth generating cases for. It is a maximisation over a continuum
 * of window positions reduced to a finite candidate list, and a reduction like that is either
 * exactly right or subtly wrong in a way no handful of examples finds. The property that matters is
 * that no window position anybody can name beats the one the search returned.
 *
 * <p>The unsharpness properties are cheaper but they caught a real defect: the minimum distance
 * helper rounded with truncating decimal division and came back one micrometre short, so a
 * distance computed to pass the check failed it.
 */
class GeometryPropertiesTest {

    private static final long LENGTH_MIN = 1000;
    private static final long LENGTH_MAX = 2_000_000;
    private static final long GAP_MIN = 0;
    private static final long GAP_MAX = 500_000;
    private static final long FOCAL_MIN = 100;
    private static final long FOCAL_MAX = 6000;
    private static final int WINDOW_EXAMPLES = 60;

    /** Source to object, object to film, focal size. */
    static Stream<Arguments> beams() {
        return MoreSamples.longTriples(LENGTH_MIN, LENGTH_MAX, GAP_MIN, GAP_MAX, FOCAL_MIN, FOCAL_MAX);
    }

    /** Focal size, object to film, material thickness. */
    static Stream<Arguments> limits() {
        return MoreSamples.longTriples(FOCAL_MIN, FOCAL_MAX, GAP_MIN, GAP_MAX, LENGTH_MIN, LENGTH_MAX);
    }

    /** Tenths of a degree over one turn. */
    static Stream<Arguments> bearings() {
        return Samples.longs(0, 3599);
    }

    /** Tenths of a degree for the start and whole degrees for the span. */
    static Stream<Arguments> startsAndSpans() {
        return Samples.longPairs(0, 3599, 1, 359);
    }

    static Stream<Arguments> filmCounts() {
        return Samples.longs(1, 12);
    }

    /** Welds with up to six slag lines on them. */
    static Stream<Arguments> welds() {
        return MoreSamples.runsOnALine(400_000, 2_000_000, 6, 1000, 120_000, WINDOW_EXAMPLES);
    }

    static Stream<Arguments> clippings() {
        return MoreSamples.longTriples(0, 900_000, 1000, 120_000, 0, 900_000);
    }

    static Stream<Arguments> swallowed() {
        return Samples.longPairs(0, 900_000, 1000, 120_000);
    }

    private static BigDecimal bearing(long tenths) {
        return Decimals.divide(BigDecimal.valueOf(tenths), 10);
    }

    private static Indication slag(long startUm, long spanUm) {
        return new Indication(Kind.ESI, new Length(startUm), new Length(spanUm), new Length(1000));
    }

    private static List<Indication> slags(List<MoreSamples.Run> runs) {
        return runs.stream().map(run -> slag(run.start(), run.span())).toList();
    }

    @ParameterizedTest
    @MethodSource("beams")
    void theThreeDistancesAlwaysAddUp(long sod, long ofd, long spot) {
        BeamGeometry beam = new BeamGeometry(new Length(sod), new Length(ofd), new Length(spot));
        assertThat(beam.sourceToFilm()).isEqualTo(new Length(sod).plus(new Length(ofd)));
    }

    @ParameterizedTest
    @MethodSource("beams")
    void buildingFromTheTapeMeasurementIsTheInverse(long sod, long ofd, long spot) {
        BeamGeometry beam = new BeamGeometry(new Length(sod), new Length(ofd), new Length(spot));
        BeamGeometry rebuilt = BeamGeometry.fromSfd(beam.sourceToFilm(), new Length(ofd), new Length(spot));
        assertThat(rebuilt).isEqualTo(beam);
    }

    @ParameterizedTest
    @MethodSource("beams")
    void theMagnificationIsNeverBelowOne(long sod, long ofd, long spot) {
        BeamGeometry beam = new BeamGeometry(new Length(sod), new Length(ofd), new Length(spot));
        assertThat(beam.magnification()).isGreaterThanOrEqualTo(BigDecimal.ONE);
    }

    @ParameterizedTest
    @MethodSource("beams")
    void movingTheSourceBackNeverIncreasesTheBlur(long sod, long ofd, long spot) {
        BeamGeometry near = new BeamGeometry(new Length(sod), new Length(ofd), new Length(spot));
        BeamGeometry far = new BeamGeometry(new Length(sod * 2), new Length(ofd), new Length(spot));
        assertThat(far.geometricUnsharpness()).isLessThanOrEqualTo(near.geometricUnsharpness());
    }

    @ParameterizedTest
    @MethodSource("limits")
    void theMinimumDistanceReallyDoesMeetTheLimit(long spot, long ofd, long wall) {
        Length limit = BeamGeometry.unsharpnessLimit(new Length(wall));
        Length closest = BeamGeometry.minimumSourceToObject(new Length(spot), new Length(ofd), limit);
        assertThat(new BeamGeometry(closest, new Length(ofd), new Length(spot)).geometricUnsharpness())
                .isLessThanOrEqualTo(limit);
    }

    @ParameterizedTest
    @MethodSource("bearings")
    void wrappingABearingLandsInsideOneTurn(long tenths) {
        BigDecimal folded = WeldMap.wrap(bearing(tenths));
        assertThat(folded).isGreaterThanOrEqualTo(BigDecimal.ZERO).isLessThan(WeldMap.FULL_CIRCLE);
    }

    @ParameterizedTest
    @MethodSource("startsAndSpans")
    void anArcContainsItsOwnStartAndNotItsOwnEnd(long startTenths, long span) {
        Arc arc = new Arc("a", WeldMap.wrap(bearing(startTenths)), BigDecimal.valueOf(span));
        assertThat(arc.contains(arc.startsAt())).isTrue();
        assertThat(arc.contains(arc.endsAt())).isFalse();
    }

    @ParameterizedTest
    @MethodSource("filmCounts")
    void evenlySpacedArcsCloseTheCircle(long count) {
        assertThat(WeldMap.evenArcs(Math.toIntExact(count)).isComplete()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("bearings")
    void aSingleFullArcCoversEveryBearing(long tenths) {
        WeldMap weld = new WeldMap(List.of(new Arc("1", BigDecimal.ZERO, WeldMap.FULL_CIRCLE)));
        assertThat(weld.covers(bearing(tenths))).containsExactly("1");
    }

    @ParameterizedTest
    @MethodSource("welds")
    void noWindowPositionBeatsTheOneTheSearchFound(long weldUm, List<MoreSamples.Run> runs) {
        List<Indication> found = slags(runs);
        Length weld = new Length(weldUm);
        Window best = Aggregates.worstWindow(found, weld);
        Length span = Aggregates.STANDARD_WINDOW.compareTo(weld) <= 0 ? Aggregates.STANDARD_WINDOW : weld;
        long limit = weld.um() - span.um();
        for (long startUm = 0; startUm <= Math.max(limit, 0); startUm += 25_000) {
            Length total = Aggregates.aggregateIn(found, new Length(startUm), new Length(startUm + span.um()))
                    .total();
            assertThat(total).isLessThanOrEqualTo(best.aggregate());
        }
    }

    @ParameterizedTest
    @MethodSource("welds")
    void theWorstWindowNeverCountsMoreThanItsOwnLength(long weldUm, List<MoreSamples.Run> runs) {
        Window best = Aggregates.worstWindow(slags(runs), new Length(weldUm));
        assertThat(best.aggregate()).isLessThanOrEqualTo(best.length());
    }

    @ParameterizedTest
    @MethodSource("welds")
    void theWorstWindowStaysOnTheWeld(long weldUm, List<MoreSamples.Run> runs) {
        Length weld = new Length(weldUm);
        Window best = Aggregates.worstWindow(slags(runs), weld);
        assertThat(best.startsAt().um()).isNotNegative();
        assertThat(best.endsAt()).isLessThanOrEqualTo(weld);
    }

    @ParameterizedTest
    @MethodSource("clippings")
    void clippingNeverReturnsMoreThanTheIndicationItself(long start, long span, long windowStart) {
        Indication found = slag(start, span);
        Length inside = Aggregates.clippedLength(found, new Length(windowStart), new Length(windowStart + 300_000));
        assertThat(inside).isLessThanOrEqualTo(found.length());
    }

    @ParameterizedTest
    @MethodSource("swallowed")
    void aWindowThatSwallowsAnIndicationClipsToTheWholeOfIt(long start, long span) {
        Indication found = slag(start, span);
        Length inside = Aggregates.clippedLength(found, new Length(0), new Length(start + span + 1));
        assertThat(inside).isEqualTo(found.length());
    }
}
