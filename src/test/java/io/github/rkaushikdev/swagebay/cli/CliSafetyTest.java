package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliSafetyTest {

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
    }

    @Test
    void theBoundaryTableListsEveryPosting() {
        String output = shop.run("safety", "boundary", "--curies", "74").out();
        assertThat(Text.words(output.lines().toList().getFirst()).getFirst()).isEqualTo("posting");
        assertThat(output).contains("restricted area", "high radiation area");
    }

    @Test
    void anOpenBeamRopesOffTheSameDistanceAllRound() {
        String output = shop.run("safety", "boundary", "--curies", "74").out();
        assertThat(output).contains("restricted area at").doesNotContain("down the beam");
    }

    @Test
    void aCollimatorMakesTheRopeDirectional() {
        String output = shop.run("safety", "boundary", "--curies", "74", "--collimator", "w60")
                .out();
        assertThat(output).contains("down the beam", "to the sides");
    }

    @Test
    void aBarrierPullsTheBoundaryIn() {
        String bare = shop.run("safety", "boundary", "--curies", "74").out();
        String shielded = shop.run("safety", "boundary", "--curies", "74", "--barrier", "lead", "--thickness", "30 mm")
                .out();
        assertThat(bare).isNotEqualTo(shielded);
    }

    @Test
    void aBarrierWithNoThicknessIsAUsageError() {
        Shell.Result result = shop.run("safety", "boundary", "--curies", "74", "--barrier", "lead");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.err()).contains("a barrier needs a thickness");
    }

    @Test
    void aHotterSourcePushesTheRopeOut() {
        String small = shop.run("safety", "boundary", "--curies", "20").out();
        String big = shop.run("safety", "boundary", "--curies", "120").out();
        assertThat(small).isNotEqualTo(big);
    }

    @Test
    void anIsotopeNothingKnowsAboutIsRefused() {
        Shell.Result result = shop.run("safety", "boundary", "--curies", "74", "--isotope", "ra226");
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFIGURATION);
        assertThat(result.failure()).isEqualTo(ConfigurationError.CODE);
    }

    @Test
    void theShieldingTableGivesBothLayers() {
        String output = shop.run("safety", "shielding").out();
        assertThat(output).contains("half value layer", "tenth value layer", "4.80 mm");
    }

    @Test
    void theShieldingTableTakesAnotherMaterial() {
        String lead = shop.run("safety", "shielding", "--material", "lead").out();
        String concrete =
                shop.run("safety", "shielding", "--material", "concrete").out();
        assertThat(lead).isNotEqualTo(concrete);
        assertThat(concrete).contains("44.50 mm");
    }

    @Test
    void aMaterialWithNoPublishedLayerIsRefused() {
        Shell.Result result = shop.run("safety", "shielding", "--material", "gypsum");
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFIGURATION);
        assertThat(result.failure()).isEqualTo(ConfigurationError.CODE);
    }

    @Test
    void cobaltNeedsMoreLeadThanIridium() {
        String iridium = shop.run("safety", "shielding", "--isotope", "ir192").out();
        String cobalt = shop.run("safety", "shielding", "--isotope", "co60").out();
        assertThat(cobalt).contains("12.50 mm");
        assertThat(iridium).doesNotContain("12.50 mm");
    }

    @Test
    void theCollimatorListNamesEveryCup() {
        String output = shop.run("safety", "collimators").out();
        assertThat(output).contains("w60", "du45", "tungsten");
    }

    @Test
    void theCollimatorListShowsWhatEachOneTransmits() {
        String output = shop.run("safety", "collimators").out();
        assertThat(output).contains("0.06", "0.03");
    }
}
