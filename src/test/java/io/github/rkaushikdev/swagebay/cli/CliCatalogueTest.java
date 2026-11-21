package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliCatalogueTest {

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
    }

    @Test
    void theIsotopeTableListsEveryIsotope() {
        String output = shop.run("catalogue", "isotopes").out();
        assertThat(output).contains("Ir-192", "Se-75", "Co-60", "Yb-169", "Cs-137");
    }

    @Test
    void theIsotopeTableCarriesTheNumbersOffTheCard() {
        String output = shop.run("catalogue", "isotopes").out();
        assertThat(output).contains("73.83", "0.48");
    }

    @Test
    void askingWhatCoversAWallNarrowsTheList() {
        String output = shop.run("catalogue", "isotopes", "--covering", "20 mm").out();
        assertThat(output).contains("Se-75", "Ir-192").doesNotContain("Co-60");
    }

    @Test
    void theCoveringListComesBackSoftestFirst() {
        List<String> lines =
                shop.run("catalogue", "isotopes", "--covering", "26 mm").lines();
        assertThat(lines.get(2)).startsWith("Se-75");
        assertThat(lines.get(3)).startsWith("Ir-192");
    }

    @Test
    void aWallNothingCoversComesBackEmpty() {
        assertThat(shop.run("catalogue", "isotopes", "--covering", "500 mm").out())
                .doesNotContain("Ir-192");
    }

    @Test
    void aThicknessThatWillNotParseIsRefused() {
        Shell.Result result = shop.run("catalogue", "isotopes", "--covering", "twenty mm");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void theFilmTableListsTheStockAndTheSpeeds() {
        String output = shop.run("catalogue", "films").out();
        assertThat(output).contains("Structurix D7", "Industrex AA400", "100");
    }

    @Test
    void theMaterialTableTakesABeamQuality() {
        String iridium = shop.run("catalogue", "materials").out();
        String soft = shop.run("catalogue", "materials", "--quality", "KV100").out();
        assertThat(iridium).contains("aluminium");
        assertThat(iridium).isNotEqualTo(soft);
    }

    @Test
    void aHoleInTheEquivalenceTableIsPrintedAsSuch() {
        assertThat(shop.run("catalogue", "materials", "--quality", "IR192").out())
                .contains("not published");
    }

    @Test
    void theIndicatorListCarriesBothFamilies() {
        String output = shop.run("catalogue", "indicators").out();
        assertThat(output).contains("hole identities", "W1", "3.200 mm");
    }

    @Test
    void theChartListNamesWhatEachOneWasDrawnFor() {
        String output = shop.run("catalogue", "charts").out();
        assertThat(output).contains("ir192-steel-d7-2.0", "co60-steel-d7-2.0", "24 in", "36 in");
    }

    @Test
    void theChartListShowsTheRangeEachOneCovers() {
        String output = shop.run("catalogue", "charts").out();
        assertThat(output).contains("6.4 mm", "63.5 mm");
    }
}
