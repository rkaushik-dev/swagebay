package io.github.rkaushikdev.swagebay.evaluation;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicationTest {

    private static Indication slag() {
        return slag(100, 20);
    }

    private static Indication slag(long at, long length) {
        return slag(at, length, 1);
    }

    private static Indication slag(long at, long length, long width) {
        return new Indication(Kind.ESI, mm(at), mm(length), mm(width));
    }

    private static Indication pore(long at, long width) {
        return new Indication(Kind.P, mm(at), Length.ZERO, mm(width));
    }

    @Test
    void anElongatedIndicationIsJudgedOnItsLength() {
        Indication found = slag(100, 30);
        assertThat(found.size()).isEqualTo(mm(30));
        assertThat(found.endsAt()).isEqualTo(mm(130));
    }

    @Test
    void aRoundedIndicationIsJudgedOnItsWidth() {
        Indication found = pore(100, 3);
        assertThat(found.size()).isEqualTo(mm(3));
        assertThat(found.length()).isEqualTo(Length.ZERO);
        assertThat(found.endsAt()).isEqualTo(found.startsAt());
    }

    @Test
    void anElongatedIndicationWithoutALengthIsRefused() {
        assertThatThrownBy(() -> new Indication(Kind.ESI, mm(100), Length.ZERO, mm(1)))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("kind", "ESI"));
    }

    @Test
    void aRoundedIndicationWithoutAWidthIsRefused() {
        assertThatThrownBy(() -> new Indication(Kind.P, mm(100), mm(3), Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void negativeSizesAndPositionsAreRefused() {
        assertThatThrownBy(() -> new Indication(Kind.ESI, new Length(-1), mm(10), mm(1)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Indication(Kind.ESI, mm(100), new Length(-1), mm(1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aDepthFractionSitsInsideTheUnitInterval() {
        Indication undercut = new Indication(Kind.U, mm(100), mm(10));
        assertThatCode(() -> undercut.withDepthFraction(new BigDecimal("0.125")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> undercut.withDepthFraction(BigDecimal.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> undercut.withDepthFraction(new BigDecimal("1.5")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void overlapAndGapAreMeasuredAlongTheWeld() {
        Indication first = slag(100, 20);
        Indication second = slag(130, 20);
        assertThat(first.overlaps(second)).isFalse();
        assertThat(first.gapTo(second)).isEqualTo(mm(10));
        assertThat(second.gapTo(first)).isEqualTo(mm(10));
    }

    @Test
    void touchingIndicationsDoNotOverlapAndHaveNoGap() {
        Indication first = slag(100, 20);
        Indication second = slag(120, 20);
        assertThat(first.overlaps(second)).isFalse();
        assertThat(first.gapTo(second)).isEqualTo(Length.ZERO);
    }

    @Test
    void overlappingIndicationsReportNoGap() {
        Indication first = slag(100, 40);
        Indication second = slag(120, 20);
        assertThat(first.overlaps(second)).isTrue();
        assertThat(first.gapTo(second)).isEqualTo(Length.ZERO);
    }

    @Test
    void aSetSortsItselfAlongTheWeld() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(slag(300, 20), slag(100, 20), slag(200, 20)));
        assertThat(found.indications())
                .extracting(indication -> indication.startsAt().mm())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(300));
        assertThat(found.size()).isEqualTo(3);
    }

    @Test
    void anIndicationOffTheEndOfTheWeldIsRefused() {
        assertThatThrownBy(() -> new IndicationSet(mm(100), List.of(slag(90, 20))))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("ends_at_mm", "110"));
    }

    @Test
    void aWeldWithNoLengthIsRefused() {
        assertThatThrownBy(() -> new IndicationSet(Length.ZERO, List.of())).isInstanceOf(ValidationError.class);
    }

    @Test
    void aSetReportsWhatKindsItHolds() {
        IndicationSet found =
                new IndicationSet(mm(1000), List.of(slag(), pore(400, 2), new Indication(Kind.C, mm(600), mm(5))));
        assertThat(found.kinds()).containsExactlyInAnyOrder(Kind.ESI, Kind.P, Kind.C);
        assertThat(found.ofKind(Kind.ESI)).hasSize(1);
        assertThat(found.ofKind(Kind.U)).isEmpty();
    }

    @Test
    void thePlanarOnesCanBePulledOutOnTheirOwn() {
        IndicationSet found =
                new IndicationSet(mm(1000), List.of(slag(), pore(400, 2), new Indication(Kind.C, mm(600), mm(5))));
        assertThat(found.planar()).extracting(Indication::kind).containsExactly(Kind.C);
    }

    @Test
    void lengthsOfOneKindAreTotalled() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(slag(100, 20), slag(300, 30), pore(500, 2)));
        assertThat(found.totalLengthOf(Kind.ESI)).isEqualTo(mm(50));
        assertThat(found.totalLengthOf(Kind.P)).isEqualTo(Length.ZERO);
    }

    @Test
    void aWindowPicksUpEverythingThatOverlapsIt() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(slag(100, 20), slag(290, 30)));
        assertThat(found.within(mm(0), mm(200))).hasSize(1);
        assertThat(found.within(mm(0), mm(300))).hasSize(2);
        assertThat(found.within(mm(400), mm(900))).isEmpty();
    }

    @Test
    void aWindowWithNoWidthIsRefused() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(slag()));
        assertThatThrownBy(() -> found.within(mm(100), mm(100))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theLongestOfAKindIsFoundOnSizeNotLength() {
        IndicationSet found = new IndicationSet(mm(1000), List.of(pore(100, 2), pore(200, 5)));
        assertThat(found.longestOf(Kind.P))
                .hasValueSatisfying(longest -> assertThat(longest.size()).isEqualTo(mm(5)));
        assertThat(found.longestOf(Kind.C)).isEmpty();
    }

    @Test
    void anIndicationPrintsTheWayAReportLineReads() {
        Indication found = new Indication(Kind.ESI, mm(100), mm(25), mm(1)).withClock(ClockPosition.parse("3:00"));
        assertThat(found.describe()).isEqualTo("ESI 25.0 mm from 100 mm at 3:00");
        assertThat(slag().describe()).isEqualTo("ESI 20.0 mm from 100 mm");
    }
}
