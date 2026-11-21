package io.github.rkaushikdev.swagebay.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MomentsTest {

    private static final OffsetDateTime AWARE = OffsetDateTime.of(2025, 4, 14, 23, 30, 0, 0, ZoneOffset.UTC);
    private static final LocalDateTime NAIVE = LocalDateTime.of(2025, 4, 14, 23, 30);
    private static final ZoneOffset NEWFOUNDLAND = ZoneOffset.ofHoursMinutes(-3, -30);

    @Test
    void anInstantKnowsWhereItHappenedAndWallClockDoesNot() {
        assertThat(Moments.isAware(AWARE)).isTrue();
        assertThat(Moments.isAware(NAIVE)).isFalse();
    }

    @Test
    void anInstantIsRequiredWhereOneIsRequired() {
        assertThat(Moments.requireAware(AWARE)).isSameAs(AWARE);
        assertThatThrownBy(() -> Moments.requireAware(NAIVE, "assay"))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.message())
                        .contains("assay"));
    }

    @Test
    void wallClockIsRequiredWhereThatIsRequired() {
        assertThat(Moments.requireNaive(NAIVE)).isSameAs(NAIVE);
        assertThatThrownBy(() -> Moments.requireNaive(AWARE, "permit window")).isInstanceOf(ValidationError.class);
    }

    @Test
    void comparingAcrossTheTwoKindsIsRefused() {
        Moments.sameKind(AWARE, AWARE);
        Moments.sameKind(NAIVE, NAIVE);
        assertThatThrownBy(() -> Moments.sameKind(AWARE, NAIVE))
                .isInstanceOfSatisfying(
                        ValidationError.class,
                        caught -> assertThat(caught.context().get("left_aware")).isEqualTo(true));
    }

    @Test
    void anInstantMovesIntoUniversalTimeWithoutChanging() {
        OffsetDateTime local = AWARE.withOffsetSameInstant(NEWFOUNDLAND);
        assertThat(Moments.toUtc(local)).isEqualTo(AWARE);
        assertThat(Moments.toUtc(local).getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void wallClockTimeCannotBeMovedIntoUniversalTime() {
        assertThatThrownBy(() -> Moments.toUtc(NAIVE)).isInstanceOf(ValidationError.class);
    }

    @Test
    void anInstantCanBeReadAtAStatedOffset() {
        OffsetDateTime read = Moments.atOffset(AWARE, -4);
        assertThat(read.getHour()).isEqualTo(19);
        assertThat(read.isEqual(AWARE)).isTrue();
    }

    @Test
    void anOffsetNoPlaceUsesIsRefused() {
        assertThatThrownBy(() -> Moments.atOffset(AWARE, 20)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Moments.atOffset(AWARE, -20)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aSpanRunsForwardsBetweenTwoOfAKind() {
        assertThat(Moments.span(NAIVE, NAIVE.plusHours(2))).isEqualTo(Duration.ofHours(2));
        assertThatThrownBy(() -> Moments.span(NAIVE.plusHours(2), NAIVE)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Moments.span(NAIVE, AWARE)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theLocalDayAndTheUniversalDayCanDiffer() {
        OffsetDateTime justPastMidnight = OffsetDateTime.of(2025, 4, 15, 0, 10, 0, 0, NEWFOUNDLAND);
        assertThat(Moments.onDay(justPastMidnight)).isEqualTo(LocalDate.of(2025, 4, 15));
        assertThat(Moments.utcDay(justPastMidnight)).isEqualTo(LocalDate.of(2025, 4, 15));
        OffsetDateTime earlier = OffsetDateTime.of(2025, 4, 15, 0, 10, 0, 0, ZoneOffset.ofHours(13));
        assertThat(Moments.utcDay(earlier)).isEqualTo(LocalDate.of(2025, 4, 14));
    }

    @Test
    void aDayRunsFromMidnightToTheLastMomentOfIt() {
        LocalDate day = LocalDate.of(2025, 4, 14);
        assertThat(Moments.startOf(day).getHour()).isZero();
        assertThat(Moments.endOf(day).getHour()).isEqualTo(23);
        assertThat(Moments.startOf(day)).isBefore(Moments.endOf(day));
    }

    @Test
    void aWallClockMomentFallsOnADayOrItDoesNot() {
        LocalDate day = LocalDate.of(2025, 4, 14);
        assertThat(Moments.within(NAIVE, day)).isTrue();
        assertThat(Moments.within(NAIVE.plusDays(1), day)).isFalse();
        assertThat(Moments.within(Moments.startOf(day), day)).isTrue();
        assertThat(Moments.within(Moments.endOf(day), day)).isTrue();
    }

    @Test
    void anInstantCannotBeAskedWhichWallClockDayItIsOn() {
        assertThatThrownBy(() -> Moments.within(AWARE, LocalDate.of(2025, 4, 14)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void everyModuleThatTakesAnInstantSaysSoTheSameWay() {
        for (String what : List.of("assay", "survey", "reading")) {
            assertThatThrownBy(() -> Moments.requireAware(NAIVE, what))
                    .isInstanceOfSatisfying(ValidationError.class, caught -> {
                        assertThat(caught.message()).contains(what);
                        assertThat(caught.context().get("given")).isEqualTo(Times.iso(NAIVE));
                    });
        }
    }

    @Test
    void everyModuleThatTakesWallClockSaysSoTheSameWay() {
        for (String what : List.of("site window", "permit", "shift")) {
            assertThatThrownBy(() -> Moments.requireNaive(AWARE, what))
                    .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.message())
                            .contains(what));
        }
    }

    @Test
    void anOffsetAtTheEdgeOfTheRangeIsAllowed() {
        assertThat(Moments.atOffset(AWARE, 14).getOffset()).isEqualTo(ZoneOffset.ofHours(14));
        assertThat(Moments.atOffset(AWARE, -14).getOffset()).isEqualTo(ZoneOffset.ofHours(-14));
    }

    @Test
    void aSpanOfNoTimeIsAllowed() {
        assertThat(Moments.span(NAIVE, NAIVE)).isEqualTo(Duration.ZERO);
        assertThat(Moments.span(AWARE, AWARE)).isEqualTo(Duration.ZERO);
    }

    @Test
    void aHalfHourZoneSurvivesTheRoundTrip() {
        OffsetDateTime local = AWARE.withOffsetSameInstant(NEWFOUNDLAND);
        assertThat(local.getOffset().getTotalSeconds()).isEqualTo(-(3 * 3600 + 30 * 60));
        assertThat(Moments.toUtc(local)).isEqualTo(AWARE);
    }

    @Test
    void theUniversalDayOfAnInstantDoesNotMoveWithTheZoneItIsReadIn() {
        OffsetDateTime local = AWARE.withOffsetSameInstant(NEWFOUNDLAND);
        assertThat(Moments.utcDay(local)).isEqualTo(Moments.utcDay(AWARE));
    }

    @Test
    void theEndOfADayIsInsideItAndTheNextMidnightIsNot() {
        LocalDate day = LocalDate.of(2025, 4, 14);
        assertThat(Moments.within(Moments.endOf(day), day)).isTrue();
        assertThat(Moments.within(Moments.startOf(LocalDate.of(2025, 4, 15)), day))
                .isFalse();
    }

    @Test
    void midnightMatchesANaiveMoment() {
        Temporal at = Moments.midnightLike(LocalDate.of(2025, 8, 19), LocalDateTime.of(2024, 1, 1, 0, 0));
        assertThat(at).isEqualTo(LocalDateTime.of(2025, 8, 19, 0, 0));
        assertThat(Moments.isAware(at)).isFalse();
    }

    @Test
    void midnightMatchesAnAwareMoment() {
        Temporal at = Moments.midnightLike(
                LocalDate.of(2025, 8, 19), OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(at.get(ChronoField.OFFSET_SECONDS)).isZero();
        assertThat(at.get(ChronoField.HOUR_OF_DAY)).isZero();
    }

    @Test
    void midnightCarriesTheOffsetItWasGivenNotUtc() {
        ZoneOffset atlantic = ZoneOffset.ofHours(-4);
        Temporal at =
                Moments.midnightLike(LocalDate.of(2025, 8, 19), OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, atlantic));
        assertThat(at.get(ChronoField.OFFSET_SECONDS)).isEqualTo(-4 * 3600);
    }

    @Test
    void aMidnightMatchedToAMomentCanAlwaysBeComparedWithIt() {
        List<Temporal> others =
                List.of(LocalDateTime.of(2024, 1, 1, 0, 0), OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        for (Temporal other : others) {
            Temporal midnight = Moments.midnightLike(LocalDate.of(2025, 8, 19), other);
            assertThat(Moments.span(other, midnight).isPositive()).isTrue();
        }
    }
}
