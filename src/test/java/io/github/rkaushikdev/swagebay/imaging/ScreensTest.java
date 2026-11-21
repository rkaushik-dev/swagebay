package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Screens.qualitiesWithScreens;
import static io.github.rkaushikdev.swagebay.imaging.Screens.recommendedFilter;
import static io.github.rkaushikdev.swagebay.imaging.Screens.screensFor;
import static io.github.rkaushikdev.swagebay.imaging.Screens.wrongScreens;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreensTest {

    @Test
    void aHarderQualityWantsThickerLead() {
        Screens soft = screensFor(Quality.KV100);
        Screens hard = screensFor(Quality.CO60);
        assertThat(hard.front()).isGreaterThan(soft.front());
        assertThat(hard.back()).isGreaterThan(soft.back());
    }

    @Test
    void aHarderQualityAlsoGetsMoreOutOfTheScreens() {
        assertThat(screensFor(Quality.CO60).intensification())
                .isGreaterThan(screensFor(Quality.KV100).intensification());
    }

    @Test
    void theScreensShortenTheShotRatherThanLengtheningIt() {
        for (Quality quality : qualitiesWithScreens()) {
            assertThat(screensFor(quality).timeFactor()).isLessThanOrEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    void theIridiumPairIsTheOneTheShopUsesMost() {
        Screens pair = screensFor(Quality.IR192);
        assertThat(pair.front()).isEqualTo(mm("0.13"));
        assertThat(pair.back()).isEqualTo(mm("0.15"));
        assertThat(pair.timeFactor()).isEqualByComparingTo(Decimals.divide(BigDecimal.ONE, new BigDecimal("2.5")));
    }

    @Test
    void aQualityWithNoPairSpecifiedIsRefused() {
        assertThatThrownBy(() -> screensFor(Quality.MV2))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.context())
                        .containsEntry("quality", "2 MV"));
    }

    @Test
    void aScreenThatMakesAShotLongerIsRefused() {
        assertThatThrownBy(() -> new Screens(Quality.IR192, mm("0.1"), mm("0.1"), new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Screens(Quality.IR192, new Length(-1), mm("0.1"), BigDecimal.valueOf(2)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theRightPairOnTheRightShotDrawsNoComment() {
        assertThat(wrongScreens(screensFor(Quality.IR192), Quality.IR192)).isEmpty();
    }

    @Test
    void aThinPairOnAHardShotWillFog() {
        String note = wrongScreens(screensFor(Quality.KV100), Quality.CO60).orElseThrow();
        assertThat(note).contains("thin for Co-60");
        assertThat(note).contains("fog");
    }

    @Test
    void aHeavyPairOnASoftShotLosesSpeed() {
        String note = wrongScreens(screensFor(Quality.CO60), Quality.KV100).orElseThrow();
        assertThat(note).contains("heavy for 100 kV");
        assertThat(note).contains("lose speed");
    }

    @Test
    void thinSteelNeedsNoFilter() {
        assertThat(recommendedFilter(mm(10))).isEmpty();
        assertThat(recommendedFilter(mm(24))).isEmpty();
    }

    @Test
    void mediumSteelTakesHalfAMillimetreOfCopper() {
        Filter fitted = recommendedFilter(mm(30)).orElseThrow();
        assertThat(fitted.material()).isEqualTo("copper");
        assertThat(fitted.thickness()).isEqualTo(mm("0.5"));
    }

    @Test
    void heavySteelTakesTwiceThatAndCostsTwiceTheTime() {
        Filter light = recommendedFilter(mm(30)).orElseThrow();
        Filter heavy = recommendedFilter(mm(60)).orElseThrow();
        assertThat(heavy.thickness()).isGreaterThan(light.thickness());
        assertThat(heavy.timeMultiplier()).isGreaterThan(light.timeMultiplier());
    }

    @Test
    void aFilterAlwaysMakesTheShotLonger() {
        for (int thickness : List.of(30, 60, 120)) {
            Filter fitted = recommendedFilter(mm(thickness)).orElseThrow();
            assertThat(fitted.timeMultiplier()).isGreaterThanOrEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    void aThicknessOfNothingIsRefused() {
        assertThatThrownBy(() -> recommendedFilter(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aFilterThatShortensAShotIsRefused() {
        assertThatThrownBy(() -> new Filter("copper", mm(1), new BigDecimal("0.5")))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Filter("copper", Length.ZERO, BigDecimal.valueOf(2)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void noLetterAtAllMeansNoBackscatter() {
        BackscatterTest test = new BackscatterTest(false, false);
        assertThat(test.failed()).isFalse();
        assertThat(test.verdict()).isEqualTo("no backscatter");
    }

    @Test
    void aLighterLetterIsTheFailure() {
        BackscatterTest test = new BackscatterTest(true, true);
        assertThat(test.failed()).isTrue();
        assertThat(test.verdict()).contains("shoot it again");
    }

    @Test
    void aDarkerLetterIsNotBackscatter() {
        BackscatterTest test = new BackscatterTest(true, false);
        assertThat(test.failed()).isFalse();
        assertThat(test.verdict()).contains("not backscatter");
    }

    @Test
    void aFilterAndAPairBothPrintForTheSheet() {
        assertThat(recommendedFilter(mm(30)).orElseThrow().describe()).isEqualTo("0.50 mm copper, x1.4");
        assertThat(screensFor(Quality.IR192).describe()).isEqualTo("0.13 mm front, 0.15 mm back, x2.5 faster");
    }
}
