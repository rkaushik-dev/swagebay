package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.scheduling.SiteSchedule;
import io.github.rkaushikdev.swagebay.scheduling.Window;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ShiftsTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 1, 27);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 1, 28);

    private static SiteSchedule schedule(LocalDate... days) {
        List<LocalDate> nights = days.length == 0 ? List.of(MONDAY) : Arrays.asList(days);
        return new SiteSchedule(nights.stream().map(Window::night).toList());
    }

    private static List<Piece> work(long... lengths) {
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < lengths.length; i++) {
            pieces.add(new Piece("GW-" + (i + 100), minutes(lengths[i])));
        }
        return pieces;
    }

    private static long[] repeated(int count, long length) {
        return LongStream.generate(() -> length).limit(count).toArray();
    }

    @Test
    void aNightThatFitsEverythingLeavesNothingOver() {
        ShiftPlan plan = Shifts.planShift(schedule(), work(30, 30, 30));
        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.slots()).hasSize(3);
        assertThat(plan.windowsUsed()).isEqualTo(1);
    }

    @Test
    void theJointsArePlacedInTheOrderTheyWereGiven() {
        ShiftPlan plan = Shifts.planShift(schedule(), work(30, 30, 30));
        assertThat(plan.slots()).extracting(Slot::weld).containsExactly("GW-100", "GW-101", "GW-102");
    }

    @Test
    void theFirstJointStartsAfterTheSetupAndNotAtTheWindow() {
        ShiftPlan plan = Shifts.planShift(schedule(), work(30));
        Slot first = plan.slots().get(0);
        assertThat(first.startsAt()).isEqualTo(LocalDateTime.of(2025, 1, 27, 22, 40));
        assertThat(first.windowStartsAt()).isEqualTo(LocalDateTime.of(2025, 1, 27, 22, 0));
    }

    @Test
    void setupIsChargedOnceAWindowAndNotOnceAJoint() {
        ShiftPlan one = Shifts.planShift(schedule(), work(400));
        ShiftPlan many = Shifts.planShift(schedule(), work(repeated(8, 50)));
        assertThat(one.isComplete()).isTrue();
        assertThat(many.isComplete()).isTrue();
    }

    @Test
    void workThatDoesNotFitRollsOverAndIsNamed() {
        ShiftPlan plan = Shifts.planShift(schedule(), work(repeated(5, 120)));
        assertThat(plan.isComplete()).isFalse();
        assertThat(plan.leftOver()).isNotEmpty();
        assertThat(plan.slots())
                .extracting(Slot::weld)
                .doesNotContain(plan.leftOver().get(0));
    }

    @Test
    void theLeftOverWorkGoesIntoTheNextNight() {
        ShiftPlan plan = Shifts.planShift(schedule(MONDAY, TUESDAY), work(repeated(5, 120)));
        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.windowsUsed()).isEqualTo(2);
    }

    @Test
    void aJointLongerThanAnyWindowNeverFits() {
        ShiftPlan plan = Shifts.planShift(schedule(), work(600));
        assertThat(plan.slots()).isEmpty();
        assertThat(plan.leftOver()).containsExactly("GW-100");
    }

    @Test
    void aWindowTooShortForTheSetupIsSkippedEntirely() {
        Window tight = new Window(LocalDateTime.of(2025, 1, 27, 22, 0), LocalDateTime.of(2025, 1, 27, 22, 30));
        ShiftPlan plan = Shifts.planShift(new SiteSchedule(List.of(tight)), work(10));
        assertThat(plan.windowsUsed()).isZero();
        assertThat(plan.leftOver()).containsExactly("GW-100");
    }

    @Test
    void theJointsDoNotOverlapEachOther() {
        List<Slot> slots = Shifts.planShift(schedule(), work(30, 30, 30)).slots();
        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).startsAt())
                    .isAfterOrEqualTo(slots.get(i - 1).endsAt());
        }
    }

    @Test
    void everyJointStaysInsideItsWindow() {
        List<Window> windows = schedule().windows();
        ShiftPlan plan = Shifts.planShift(schedule(), work(60, 60, 60));
        for (Slot slot : plan.slots()) {
            Window window = windows.stream()
                    .filter(candidate -> candidate.startsAt().equals(slot.windowStartsAt()))
                    .findFirst()
                    .orElseThrow();
            assertThat(slot.endsAt()).isBeforeOrEqualTo(window.endsAt());
        }
    }

    @Test
    void aPieceOfWorkNeedsAWeldAndSomeTime() {
        assertThatThrownBy(() -> new Piece("  ", minutes(10))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Piece("GW-100", Duration.ZERO)).isInstanceOf(ValidationError.class);
    }

    /** A duration below zero is refused when it is made, so a negative setup never reaches the plan. */
    @Test
    void aNegativeSetupIsRefused() {
        SiteSchedule schedule = schedule();
        List<Piece> work = work(30);
        assertThatThrownBy(() -> Shifts.planShift(schedule, work, new Duration(-1)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void noWorkAtAllProducesAnEmptyPlan() {
        ShiftPlan plan = Shifts.planShift(schedule(), List.of());
        assertThat(plan.isComplete()).isTrue();
        assertThat(plan.slots()).isEmpty();
        assertThat(plan.windowsUsed()).isZero();
    }

    @Test
    void theUtilisationCountsTheSetupAsWellAsTheShots() {
        List<Window> windows = schedule().windows();
        ShiftPlan plan = Shifts.planShift(schedule(), work(30, 30));
        assertThat(Shifts.windowUtilisation(plan, windows.get(0))).isEqualTo(minutes(100));
        assertThat(Shifts.DEFAULT_SETUP).isEqualTo(minutes(40));
    }

    @Test
    void anUnusedWindowReportsNoUtilisation() {
        List<Window> windows = schedule(MONDAY, TUESDAY).windows();
        ShiftPlan plan = Shifts.planShift(schedule(MONDAY, TUESDAY), work(30));
        assertThat(Shifts.windowUtilisation(plan, windows.get(1))).isEqualTo(Duration.ZERO);
    }

    @Test
    void theSpareTimeIsWhatIsLeftOfTheWindow() {
        List<Window> windows = schedule().windows();
        ShiftPlan plan = Shifts.planShift(schedule(), work(30, 30));
        assertThat(Shifts.spareIn(plan, windows.get(0))).isEqualTo(minutes(350));
    }

    @Test
    void aFullWindowHasNoSpareTime() {
        List<Window> windows = schedule().windows();
        ShiftPlan plan = Shifts.planShift(schedule(), work(repeated(4, 100)));
        assertThat(Shifts.spareIn(plan, windows.get(0)).ms()).isNotNegative();
    }

    @Test
    void thePlanPrintsWhatItDidAndWhatItCouldNot() {
        assertThat(Shifts.planShift(schedule(), work(30, 30)).describe()).isEqualTo("2 joints across 1 windows");
        ShiftPlan rolled = Shifts.planShift(schedule(), work(repeated(5, 120)));
        assertThat(rolled.describe()).contains("left over");
    }
}
