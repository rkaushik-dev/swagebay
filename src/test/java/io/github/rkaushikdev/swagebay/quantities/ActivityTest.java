package io.github.rkaushikdev.swagebay.quantities;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.gbq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ActivityTest {

    @Test
    void aCurieIsExactlyThirtySevenGigabecquerels() {
        assertThat(curie(1).bq()).isEqualTo(Activity.BQ_PER_CURIE);
        assertThat(curie(1).bq()).isEqualTo(37_000_000_000L);
        assertThat(curie(1).gbq()).isEqualByComparingTo("37");
    }

    @Test
    void aWorkingProjectorLoadReadsTheSameBothWays() {
        Activity source = curie(90);
        assertThat(source.curie()).isEqualByComparingTo("90");
        assertThat(source.tbq()).isEqualByComparingTo("3.33");
        assertThat(Activity.fromTbq("3.33")).isEqualTo(source);
    }

    @Test
    void smallAmountsSurviveTheRoundTrip() {
        assertThat(Activity.fromMillicurie(10).bq()).isEqualTo(370_000_000L);
        assertThat(Activity.fromMillicurie(10).curie()).isEqualByComparingTo("0.01");
        assertThat(gbq(1).bq()).isEqualTo(1_000_000_000L);
    }

    @Test
    void activitiesSortByStrength() {
        List<Activity> loads = List.of(curie(90), curie("13.5"), curie(120), curie(41));
        assertThat(loads.stream()
                        .sorted()
                        .map(a -> a.curie().stripTrailingZeros().toPlainString()))
                .containsExactly("13.5", "41", "90", "120");
    }

    @Test
    void aNegativeActivityIsRefusedAtConstruction() {
        assertThatThrownBy(() -> new Activity(-1)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> curie(1).times(-1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void subtractionWillNotRunASourceBelowZero() {
        assertThat(curie(90).minus(curie(40))).isEqualTo(curie(50));
        assertThatThrownBy(() -> curie(40).minus(curie(90))).isInstanceOf(ValidationError.class);
    }

    @Test
    void theRatioOfTwoActivitiesIsTheDecayFraction() {
        assertThat(curie(45).ratio(curie(90))).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> curie(1).ratio(Activity.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void spentIsMeasuredAgainstAFloorTheOfficeSets() {
        Activity floor = curie(20);
        assertThat(curie(15).isSpent(floor)).isTrue();
        assertThat(curie(20).isSpent(floor)).isFalse();
        assertThat(curie(90).isSpent(floor)).isFalse();
    }

    @Test
    void parseReadsBothTheLicenceAndTheCrew() {
        assertThat(Activity.parse("90 Ci").bq()).isEqualTo(90 * Activity.BQ_PER_CURIE);
        assertThat(Activity.parse("3.33 TBq").bq()).isEqualTo(3_330_000_000_000L);
        assertThat(Activity.parse("  100 mCi ").curie()).isEqualByComparingTo("0.1");
        assertThat(Activity.parse("500 uCi").curie()).isEqualByComparingTo("0.0005");
        assertThat(Activity.parse("740 GBq").gbq()).isEqualByComparingTo("740");
        assertThat(Activity.parse("1 bq").bq()).isEqualTo(1);
    }

    @Test
    void parseRefusesAUnitItDoesNotKnow() {
        for (String bad : List.of("", "90", "Ci", "90 sieverts", "90 mm", "ninety Ci")) {
            assertThatThrownBy(() -> Activity.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    /** An activity holds a whole number of becquerels; a fraction or a truth value does not compile. */
    @Test
    void becquerelsMustBeWhole() {
        assertThat(Activity.class.getRecordComponents()).hasSize(1);
        assertThat(Activity.class.getRecordComponents()[0].getType()).isEqualTo(long.class);
    }

    /** Adding or subtracting takes another activity and nothing else, so a bare number does not compile. */
    @Test
    void activityWillNotCombineWithABareNumber() {
        List<Method> combining = Stream.of(Activity.class.getMethods())
                .filter(m -> m.getName().equals("plus") || m.getName().equals("minus"))
                .toList();
        assertThat(combining).hasSize(2);
        assertThat(combining).allSatisfy(m -> assertThat(Arrays.asList(m.getParameterTypes()))
                .containsExactly(Activity.class));
    }

    @Test
    void presentationIsWhatGoesOnThePaperwork() {
        assertThat(curie(90)).hasToString("90.0 Ci");
        assertThat(curie("13.47").formatCurie(2)).isEqualTo("13.47 Ci");
        assertThat(curie(90).formatTbq()).isEqualTo("3.33 TBq");
        assertThat(new Activity(37).repr()).isEqualTo("Activity(37)");
        assertThat(new BigDecimal("90").compareTo(curie(90).curie())).isZero();
    }
}
