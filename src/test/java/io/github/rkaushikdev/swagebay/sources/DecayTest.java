package io.github.rkaushikdev.swagebay.sources;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.sources.Decay.activityOn;
import static io.github.rkaushikdev.swagebay.sources.Decay.dateReaching;
import static io.github.rkaushikdev.swagebay.sources.Decay.decay;
import static io.github.rkaushikdev.swagebay.sources.Decay.decayFraction;
import static io.github.rkaushikdev.swagebay.sources.Decay.elapsedForFraction;
import static io.github.rkaushikdev.swagebay.sources.Decay.halfLivesElapsed;
import static io.github.rkaushikdev.swagebay.sources.Decay.timeToReach;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.CO60;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.IR192;
import static io.github.rkaushikdev.swagebay.sources.Isotopes.SE75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DecayTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 3, 1, 12, 0);

    @Test
    void oneHalfLifeLeavesExactlyHalf() {
        BigDecimal left = decayFraction(IR192, IR192.halfLife());
        assertThat(left.subtract(new BigDecimal("0.5")).abs()).isLessThan(new BigDecimal("1e-20"));
        assertThat(decay(curie(90), IR192, IR192.halfLife()).curie()).isEqualByComparingTo("45");
    }

    @Test
    void twoHalfLivesLeaveAQuarter() {
        Duration two = new Duration(IR192.halfLife().ms() * 2);
        assertThat(decay(curie(80), IR192, two).curie()).isEqualByComparingTo("20");
    }

    @Test
    void nothingDecaysInNoTime() {
        assertThat(decayFraction(IR192, Duration.ZERO)).isEqualByComparingTo("1");
        assertThat(decay(curie(90), IR192, Duration.ZERO)).isEqualTo(curie(90));
    }

    @Test
    void iridiumLosesAThirdOfItselfInFiveWeeks() {
        Activity left = decay(curie(90), IR192, Duration.fromHours(35 * 24));
        assertThat(left.ratio(curie(90))).isStrictlyBetween(new BigDecimal("0.71"), new BigDecimal("0.73"));
    }

    @Test
    void cobaltHardlyMovesOverTheSameFiveWeeks() {
        Activity left = decay(curie(90), CO60, Duration.fromHours(35 * 24));
        assertThat(left.ratio(curie(90))).isGreaterThan(new BigDecimal("0.98"));
    }

    @Test
    void aCertificateDecaysToTheMorningOfTheShot() {
        LocalDateTime shot = ASSAY.plusDays(74);
        Activity onTheDay = activityOn(curie(100), ASSAY, IR192, shot);
        assertThat(onTheDay.curie()).isStrictlyBetween(new BigDecimal("49"), new BigDecimal("50"));
    }

    @Test
    void theAssayInstantReturnsTheCertificateValue() {
        assertThat(activityOn(curie(100), ASSAY, IR192, ASSAY)).isEqualTo(curie(100));
    }

    @Test
    void aSourceCannotBeDecayedBackwards() {
        assertThatThrownBy(() -> activityOn(curie(100), ASSAY, IR192, ASSAY.minusDays(1)))
                .isInstanceOf(ValidationError.class);
    }

    /**
     * An instant on the certificate and a wall clock shot date cannot meet in the decay: both moments
     * are wall clock time, so handing it an instant does not compile.
     */
    @Test
    void anAwareCertificateAndANaiveShotDateAreRefused() {
        List<Method> overloads = Stream.of(Decay.class.getMethods())
                .filter(m -> m.getName().equals("activityOn"))
                .toList();
        assertThat(overloads).hasSize(1);
        assertThat(Arrays.asList(overloads.get(0).getParameterTypes()))
                .containsExactly(Activity.class, LocalDateTime.class, Isotope.class, LocalDateTime.class);
    }

    @Test
    void theTimeToFallToAFractionInvertsTheDecay() {
        Duration span = elapsedForFraction(IR192, new BigDecimal("0.5"));
        assertThat(Math.abs(span.ms() - IR192.halfLife().ms())).isLessThan(1000);
        Duration quarter = elapsedForFraction(IR192, new BigDecimal("0.25"));
        assertThat(Math.abs(quarter.ms() - IR192.halfLife().ms() * 2)).isLessThan(2000);
    }

    @Test
    void aFractionOutsideTheUnitIntervalIsRefused() {
        for (String bad : List.of("0", "-1", "1.5")) {
            assertThatThrownBy(() -> elapsedForFraction(IR192, new BigDecimal(bad)))
                    .isInstanceOf(ValidationError.class);
        }
        assertThat(elapsedForFraction(IR192, BigDecimal.ONE)).isEqualTo(Duration.ZERO);
    }

    @Test
    void theTimeFromOneActivityDownToAnother() {
        Duration span = timeToReach(SE75, curie(80), curie(20));
        assertThat(Math.abs(span.ms() - SE75.halfLife().ms() * 2)).isLessThan(2000);
    }

    @Test
    void aSourceDoesNotGrowAndNeverReachesZero() {
        assertThatThrownBy(() -> timeToReach(IR192, curie(20), curie(80))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> timeToReach(IR192, curie(20), Activity.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> timeToReach(IR192, Activity.ZERO, curie(1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theDateASourceFallsBelowTheWorkingFloor() {
        LocalDateTime retire = dateReaching(IR192, curie(100), curie(25), ASSAY);
        assertThat(retire.toLocalDate())
                .isEqualTo(ASSAY.plusSeconds(Math.round(147.66 * 86_400)).toLocalDate());
        assertThat(retire.toLocalDate()).hasToString("2024-07-27");
    }

    @Test
    void halfLivesElapsedIsWhatTheHistorySheetQuotes() {
        Duration two = new Duration(IR192.halfLife().ms() * 2);
        assertThat(halfLivesElapsed(IR192, two)).isEqualByComparingTo("2");
        assertThat(halfLivesElapsed(IR192, Duration.ZERO)).isEqualByComparingTo("0");
    }
}
