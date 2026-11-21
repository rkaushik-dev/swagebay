package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Processing.CONTRAST_TOLERANCE;
import static io.github.rkaushikdev.swagebay.imaging.Processing.FOG_TOLERANCE;
import static io.github.rkaushikdev.swagebay.imaging.Processing.SPEED_TOLERANCE;
import static io.github.rkaushikdev.swagebay.imaging.Processing.daysSinceBaseline;
import static io.github.rkaushikdev.swagebay.imaging.Processing.drifting;
import static io.github.rkaushikdev.swagebay.imaging.Processing.excursions;
import static io.github.rkaushikdev.swagebay.imaging.Processing.inControl;
import static io.github.rkaushikdev.swagebay.imaging.Processing.trend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProcessingTest {

    private static final LocalDate SET_UP = LocalDate.of(2024, 9, 1);

    private static Baseline baseline() {
        return new Baseline(SET_UP, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("0.18"));
    }

    private static Strip strip(int day, String speed, String contrast, String fog) {
        return new Strip(
                SET_UP.plusDays(day), new BigDecimal(speed), new BigDecimal(contrast), new BigDecimal(fog), "dr");
    }

    private static Strip strip(int day, String speed) {
        return strip(day, speed, "3.00", "0.18");
    }

    private static Strip strip() {
        return strip(1, "2.00");
    }

    private static Strip withSpeed(String speed) {
        return strip(1, speed);
    }

    private static Strip withContrast(String contrast) {
        return strip(1, "2.00", contrast, "0.18");
    }

    private static Strip withFog(String fog) {
        return strip(1, "2.00", "3.00", fog);
    }

    @Test
    void aStripOnTheBaselineIsInControl() {
        assertThat(inControl(strip(), baseline())).isTrue();
        assertThat(excursions(strip(), baseline())).isEmpty();
    }

    @Test
    void aSmallDriftInsideTheBandIsStillInControl() {
        assertThat(inControl(withSpeed("2.10"), baseline())).isTrue();
        assertThat(inControl(withContrast("3.15"), baseline())).isTrue();
        assertThat(inControl(withFog("0.22"), baseline())).isTrue();
    }

    @Test
    void speedOutsideItsBandIsAnExcursion() {
        List<Excursion> found = excursions(withSpeed("2.20"), baseline());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).reading()).isEqualTo("speed");
        assertThat(found.get(0).tolerance()).isEqualByComparingTo(SPEED_TOLERANCE);
    }

    @Test
    void fogHasTheTightestBandOfTheThree() {
        assertThat(FOG_TOLERANCE).isLessThan(SPEED_TOLERANCE);
        assertThat(SPEED_TOLERANCE).isLessThan(CONTRAST_TOLERANCE);
        assertThat(excursions(withFog("0.25"), baseline())).isNotEmpty();
        assertThat(excursions(withContrast("3.15"), baseline())).isEmpty();
    }

    @Test
    void anExcursionSaysWhichWayItWent() {
        Excursion low = excursions(withSpeed("1.80"), baseline()).get(0);
        Excursion high = excursions(withSpeed("2.20"), baseline()).get(0);
        assertThat(low.direction()).isEqualTo("low");
        assertThat(high.direction()).isEqualTo("high");
        assertThat(low.describe()).contains("low of the baseline");
    }

    @Test
    void everyReadingOutOfBandIsReported() {
        List<Excursion> found = excursions(strip(1, "2.30", "3.40", "0.30"), baseline());
        assertThat(found).extracting(Excursion::reading).containsExactlyInAnyOrder("speed", "contrast", "fog");
    }

    @Test
    void aStripIsComparedToTheSetupAndNotToYesterday() {
        assertThat(inControl(strip(30, "1.87"), baseline())).isTrue();
        assertThat(inControl(strip(31, "1.80"), baseline())).isFalse();
    }

    @Test
    void theTrendReadsOldestFirst() {
        List<Strip> strips = List.of(strip(3, "1.98"), strip(1, "2.02"), strip(2, "2.00"));
        assertThat(trend(strips, baseline()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.02"), new BigDecimal("0.00"), new BigDecimal("-0.02"));
    }

    @Test
    void aRunAllOnOneSideIsADriftEvenInsideTheBand() {
        List<Strip> strips =
                IntStream.rangeClosed(1, 5).mapToObj(d -> strip(d, "1.98")).toList();
        assertThat(strips).allMatch(s -> inControl(s, baseline()));
        assertThat(drifting(strips, baseline())).isTrue();
    }

    @Test
    void aRunThatCrossesTheBaselineIsNotADrift() {
        List<String> speeds = List.of("1.98", "2.02", "1.98", "2.02", "1.98");
        List<Strip> strips = new ArrayList<>();
        for (int d = 1; d <= speeds.size(); d++) {
            strips.add(strip(d, speeds.get(d - 1)));
        }
        assertThat(drifting(strips, baseline())).isFalse();
    }

    @Test
    void aShortRunCannotBeCalledADrift() {
        List<Strip> strips =
                IntStream.rangeClosed(1, 3).mapToObj(d -> strip(d, "1.98")).toList();
        assertThat(drifting(strips, baseline())).isFalse();
        assertThat(drifting(strips, baseline(), 3)).isTrue();
    }

    @Test
    void aRunOfOneIsRefused() {
        assertThatThrownBy(() -> drifting(List.of(strip()), baseline(), 1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aStripNeedsASignatureAndBelievableReadings() {
        assertThatThrownBy(() ->
                        new Strip(SET_UP, BigDecimal.valueOf(2), BigDecimal.valueOf(3), new BigDecimal("0.18"), "  "))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(
                        () -> new Strip(SET_UP, BigDecimal.ZERO, BigDecimal.valueOf(3), new BigDecimal("0.18"), "dr"))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("reading", "speed"));
    }

    @Test
    void theAgeOfTheBaselineIsReported() {
        assertThat(daysSinceBaseline(strip(30, "2.00"), baseline())).isEqualTo(30);
    }
}
