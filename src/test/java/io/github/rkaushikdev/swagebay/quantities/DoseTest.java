package io.github.rkaushikdev.swagebay.quantities;

import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DoseTest {

    @Test
    void theFieldConventionIsTenMicrosievertsToTheMilliroentgen() {
        assertThat(mrPerHour(1).usvPerHour()).isEqualByComparingTo("10");
        assertThat(mrPerHour("0.2").usvPerHour()).isEqualByComparingTo("2");
        assertThat(usvPerHour(20).mrPerHour()).isEqualByComparingTo("2");
    }

    @Test
    void aDoseReadsTheSameInBothSystems() {
        Dose taken = usv(500);
        assertThat(taken.msv()).isEqualByComparingTo("0.5");
        assertThat(taken.mrem()).isEqualByComparingTo("50");
        assertThat(Dose.fromMrem(50)).isEqualTo(taken);
        assertThat(Dose.fromMsv("0.5")).isEqualTo(taken);
    }

    @Test
    void dosesAndRatesBothSort() {
        assertThat(Stream.of(usv(30), usv(5), usv(12)).sorted()).containsExactly(usv(5), usv(12), usv(30));
        assertThat(Stream.of(usvPerHour(30), usvPerHour(5)).sorted()).containsExactly(usvPerHour(5), usvPerHour(30));
    }

    @Test
    void neitherQuantityCanBeNegative() {
        assertThatThrownBy(() -> new Dose(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new DoseRate(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> usv(1).minus(usv(2))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> usv(1).times(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> usvPerHour(1).times(-1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aRateOverASpanIsADose() {
        assertThat(usvPerHour(20).over(Duration.fromHours(1))).isEqualTo(usv(20));
        assertThat(usvPerHour(20).over(minutes(30))).isEqualTo(usv(10));
        assertThat(usvPerHour(20).over(minutes(6))).isEqualTo(usv(2));
        assertThat(usvPerHour(0).over(minutes(30))).isEqualTo(Dose.ZERO);
    }

    @Test
    void stayTimeTruncatesSoTheBudgetIsNeverOverspent() {
        assertThat(usvPerHour(20).stayTime(usv(10))).isEqualTo(minutes(30));
        assertThat(usvPerHour(3).stayTime(usv(1))).isEqualTo(seconds(1200));
        Duration generous = usvPerHour(7).stayTime(usv(1));
        assertThat(generous.ms()).isEqualTo(514285);
        assertThatThrownBy(() -> DoseRate.ZERO.stayTime(usv(10))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aDoseTakenAsAFractionOfTheLimitIsWhatTheOfficeTracks() {
        Dose quarterly = Dose.fromMsv(5);
        assertThat(usv(500).fractionOf(quarterly)).isEqualByComparingTo("0.1");
        assertThatThrownBy(() -> usv(1).fractionOf(Dose.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void parseReadsADoseWrittenAnyOfTheUsualWays() {
        assertThat(Dose.parse("500 uSv")).isEqualTo(usv(500));
        assertThat(Dose.parse("0.5 mSv")).isEqualTo(usv(500));
        assertThat(Dose.parse("50 mrem")).isEqualTo(usv(500));
        assertThat(Dose.parse("1 Sv").msv()).isEqualByComparingTo("1000");
        assertThat(Dose.parse("5 rem").msv()).isEqualByComparingTo("50");
        assertThat(Dose.parse("250 nsv").nsv()).isEqualTo(250);
    }

    @Test
    void parseReadsARateOffASurveySheet() {
        assertThat(DoseRate.parse("2 mR/h")).isEqualTo(usvPerHour(20));
        assertThat(DoseRate.parse("2 mR/hr")).isEqualTo(usvPerHour(20));
        assertThat(DoseRate.parse("20 uSv/h")).isEqualTo(usvPerHour(20));
        assertThat(DoseRate.parse("1 R / h").msvPerHour()).isEqualByComparingTo("10");
        assertThat(DoseRate.parse("0.5 mSv/h").usvPerHour()).isEqualByComparingTo("500");
    }

    @Test
    void parseRefusesADoseWhereARateBelongs() {
        assertThatThrownBy(() -> DoseRate.parse("500 uSv")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Dose.parse("20 uSv/h")).isInstanceOf(ValidationError.class);
        for (String bad : List.of("", "hot", "2 mR", "2 mR/day")) {
            assertThatThrownBy(() -> DoseRate.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    /** A dose adds only to a dose and a rate only to a rate, so mixing them does not compile. */
    @Test
    void theTwoTypesDoNotMix() {
        assertThat(Stream.of(Dose.class.getMethods()).filter(m -> m.getName().equals("plus")))
                .allSatisfy(
                        m -> assertThat(Arrays.asList(m.getParameterTypes())).containsExactly(Dose.class));
        assertThat(Stream.of(DoseRate.class.getMethods())
                        .filter(m -> m.getName().equals("plus")))
                .allSatisfy(
                        m -> assertThat(Arrays.asList(m.getParameterTypes())).containsExactly(DoseRate.class));
    }

    @Test
    void presentationMatchesTheSurveySheet() {
        assertThat(usv(500)).hasToString("500.0 uSv");
        assertThat(usv(500).formatMrem()).isEqualTo("50.0 mrem");
        assertThat(usvPerHour(20)).hasToString("20.0 uSv/h");
        assertThat(usvPerHour(20).formatMrPerHour()).isEqualTo("2.00 mR/h");
        assertThat(new Dose(1).repr()).isEqualTo("Dose(1)");
        assertThat(new DoseRate(1).repr()).isEqualTo("DoseRate(1)");
    }
}
