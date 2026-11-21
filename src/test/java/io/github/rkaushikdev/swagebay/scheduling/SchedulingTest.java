package io.github.rkaushikdev.swagebay.scheduling;

import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingTest {

    private static final LocalDate MONDAY = LocalDate.of(2024, 4, 8);

    private static LocalDateTime at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2024, month, day, hour, minute);
    }

    @Test
    void aNightWindowRunsOverMidnight() {
        Window window = Window.night(MONDAY);
        assertThat(window.startsAt()).isEqualTo(at(4, 8, 22, 0));
        assertThat(window.endsAt()).isEqualTo(at(4, 9, 5, 30));
        assertThat(window.length()).isEqualTo(minutes(450));
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new Window(at(4, 8, 22, 0), at(4, 8, 21, 0))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Window(at(4, 8, 22, 0), at(4, 8, 22, 0))).isInstanceOf(ValidationError.class);
    }

    /**
     * A window is wall clock time, so both of its ends are {@link LocalDateTime}: an instant carrying
     * a zone does not compile as either end, and the refusal is the type itself.
     */
    @Test
    void anAwareTimestampIsRefusedBecausePermitsAreWallClock() {
        assertThat(Arrays.stream(Window.class.getRecordComponents())
                        .filter(component -> component.getName().endsWith("At"))
                        .map(RecordComponent::getType))
                .containsExactly(LocalDateTime.class, LocalDateTime.class);
        assertThat(Arrays.stream(Window.class.getConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                        .filter(type -> type != WindowKind.class))
                .containsOnly(LocalDateTime.class);
    }

    @Test
    void containmentIsHalfOpenAtTheClose() {
        Window window = Window.night(MONDAY);
        assertThat(window.contains(at(4, 8, 22, 0))).isTrue();
        assertThat(window.contains(at(4, 9, 5, 29))).isTrue();
        assertThat(window.contains(at(4, 9, 5, 30))).isFalse();
        assertThat(window.contains(at(4, 8, 21, 59))).isFalse();
    }

    @Test
    void timeRemainingRunsDownAndStopsAtZero() {
        Window window = Window.night(MONDAY);
        assertThat(window.remainingAt(at(4, 9, 5, 0))).isEqualTo(minutes(30));
        assertThat(window.remainingAt(at(4, 9, 6, 0))).isEqualTo(Duration.ZERO);
        assertThat(window.remainingAt(at(4, 8, 12, 0))).isEqualTo(minutes(450));
    }

    @Test
    void aJobFitsOnlyIfTheTimeLeftCoversIt() {
        Window window = Window.night(MONDAY);
        LocalDateTime late = at(4, 9, 5, 0);
        assertThat(window.fits(minutes(30), late)).isTrue();
        assertThat(window.fits(minutes(31), late)).isFalse();
    }

    @Test
    void aScheduleKeepsItsWindowsInOrder() {
        Window later = Window.night(LocalDate.of(2024, 4, 10));
        Window earlier = Window.night(MONDAY);
        SiteSchedule schedule = new SiteSchedule(List.of(later, earlier));
        assertThat(schedule.windows()).containsExactly(earlier, later);
    }

    @Test
    void overlappingGrantsAreRefusedAtConstruction() {
        Window first = new Window(at(4, 8, 22, 0), at(4, 9, 6, 0));
        Window second = new Window(at(4, 9, 5, 0), at(4, 9, 8, 0));
        assertThat(first.overlaps(second)).isTrue();
        assertThatThrownBy(() -> new SiteSchedule(List.of(first, second))).isInstanceOf(ValidationError.class);
    }

    @Test
    void touchingWindowsDoNotOverlap() {
        Window first = new Window(at(4, 8, 22, 0), at(4, 9, 6, 0));
        Window second = new Window(at(4, 9, 6, 0), at(4, 9, 8, 0));
        assertThat(first.overlaps(second)).isFalse();
        assertThat(new SiteSchedule(List.of(first, second)).windows()).hasSize(2);
    }

    @Test
    void theScheduleAnswersWhetherTheAreaIsClearRightNow() {
        SiteSchedule schedule =
                new SiteSchedule(List.of(Window.night(MONDAY), Window.night(LocalDate.of(2024, 4, 10))));
        assertThat(schedule.isOpenAt(at(4, 9, 1, 0))).isTrue();
        assertThat(schedule.isOpenAt(at(4, 9, 12, 0))).isFalse();
        assertThat(schedule.windowAt(at(4, 9, 1, 0)).orElseThrow().kind()).isSameAs(WindowKind.NIGHT);
    }

    @Test
    void theNextWindowIsTheNextOneThatHasNotStarted() {
        SiteSchedule schedule =
                new SiteSchedule(List.of(Window.night(MONDAY), Window.night(LocalDate.of(2024, 4, 10))));
        Window following = schedule.nextWindowAfter(at(4, 9, 1, 0)).orElseThrow();
        assertThat(following.startsAt()).isEqualTo(at(4, 10, 22, 0));
        assertThat(schedule.nextWindowAfter(at(4, 20, 0, 0))).isEmpty();
    }

    @Test
    void aLongJobSkipsAWindowThatIsTooShortForIt() {
        Window shortOne = new Window(at(4, 8, 22, 0), at(4, 8, 23, 0));
        Window longOne = Window.night(LocalDate.of(2024, 4, 10));
        SiteSchedule schedule = new SiteSchedule(List.of(shortOne, longOne));
        assertThat(schedule.firstWindowFitting(minutes(120), at(4, 8, 12, 0))).hasValue(longOne);
        assertThat(schedule.firstWindowFitting(minutes(30), at(4, 8, 12, 0))).hasValue(shortOne);
    }

    @Test
    void aJobThatFitsNowhereComesBackEmpty() {
        SiteSchedule schedule = new SiteSchedule(List.of(Window.night(MONDAY)));
        assertThat(schedule.firstWindowFitting(Duration.fromHours(20), at(4, 1, 0, 0)))
                .isEmpty();
    }

    @Test
    void theOpenTimeOnADayCountsOnlyThatDaysShare() {
        SiteSchedule schedule = new SiteSchedule(List.of(Window.night(MONDAY)));
        assertThat(schedule.totalOpen(MONDAY)).isEqualTo(minutes(120));
        assertThat(schedule.totalOpen(LocalDate.of(2024, 4, 9))).isEqualTo(minutes(330));
        assertThat(schedule.totalOpen(LocalDate.of(2024, 4, 11))).isEqualTo(Duration.ZERO);
    }

    @Test
    void shutdownsCanBeExtendedAndNightsCannot() {
        assertThat(WindowKind.SHUTDOWN.isExtendable()).isTrue();
        assertThat(WindowKind.WEEKEND.isExtendable()).isTrue();
        assertThat(WindowKind.NIGHT.isExtendable()).isFalse();
        assertThat(WindowKind.PERMIT.isExtendable()).isFalse();
    }

    @Test
    void aWindowPrintsTheWayAPermitReads() {
        Window window = new Window(at(4, 8, 22, 0), at(4, 9, 5, 30), WindowKind.PERMIT);
        assertThat(window).hasToString("permit 2024-04-08 22:00 to 05:30");
        assertThat(Window.night(MONDAY, LocalTime.of(20, 0), Window.NIGHT_CLOSES)
                        .startsAt()
                        .getHour())
                .isEqualTo(20);
    }
}
