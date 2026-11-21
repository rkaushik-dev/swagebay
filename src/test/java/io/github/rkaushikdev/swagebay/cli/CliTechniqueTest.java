package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTechniqueTest {

    private static final String ASSAY = "2024-11-01T00:00:00";
    private static final String SHOT = "2024-11-21T23:30:00";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void aJobWithAWeldAndASource() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
        shop.run("source", "add-device", "880D-221", "880d");
        shop.run("source", "register", "IR-5512", "--isotope", "ir192", "--curies", "90", "--assayed", ASSAY);
        shop.run("source", "wipe", "IR-5512", "--at", ASSAY, "--by", "rp");
        shop.run("job", "open", "--client", "c", "--site", "s", "--procedure", "RT-001", "--on", "2024-11-01");
        shop.run(
                "weld",
                "add",
                "24-0001",
                "GW-104",
                "--wall",
                "10.97 mm",
                "--diameter",
                "168.3 mm",
                "--cap",
                "2 mm",
                "--length",
                "529 mm",
                "--welder",
                "w-19",
                "--on",
                "2024-11-01");
    }

    private Shell.Result plan(String... extra) {
        List<String> args =
                new ArrayList<>(List.of("technique", "plan", "24-0001", "GW-104", "--source", "IR-5512", "--at", SHOT));
        args.addAll(List.of(extra));
        return shop.run(args.toArray(String[]::new));
    }

    private Shell.Result compare(String films, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "technique", "compare", "24-0001", "GW-104", "--source", "IR-5512", "--films", films, "--at", SHOT));
        args.addAll(List.of(extra));
        return shop.run(args.toArray(String[]::new));
    }

    @Test
    void aSheetPrintsEveryNumberTheCrewNeeds() {
        Shell.Result result = plan("--shot", "double wall single image");
        assertThat(result.code()).isZero();
        assertThat(result.out())
                .contains(
                        "technique sheet GW-104",
                        "penetrated      25.94 mm",
                        "exposures       8",
                        "clean           yes");
    }

    @Test
    void thePlannerPicksTheShotWhenNoneIsNamed() {
        String output = plan().out();
        assertThat(output).contains("shot            panoramic", "exposures       1");
    }

    @Test
    void theShotCanBeNamedByItsEnumNameToo() {
        assertThat(plan("--shot", "DWSI").out()).contains("double wall single image");
    }

    @Test
    void aShotTypeNothingKnowsAboutIsRefused() {
        Shell.Result result = plan("--shot", "sideways");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void warningsPrintUnderABlankLine() {
        List<String> lines =
                plan("--shot", "double wall single image", "--window", "1").lines();
        int blank = lines.indexOf("");
        assertThat(blank).isNotNegative();
        assertThat(lines.subList(blank, lines.size())).anyMatch(line -> line.contains("source out"));
    }

    @Test
    void theFilmSideFlagAsksForTheMarker() {
        assertThat(plan("--shot", "double wall single image", "--film-side").out())
                .contains("F marker");
    }

    @Test
    void aSlowerFilmGivesALongerSheet() {
        String fast = plan("--shot", "double wall single image", "--film", "d7").out();
        String slow = plan("--shot", "double wall single image", "--film", "d4").out();
        assertThat(fast).isNotEqualTo(slow);
    }

    @Test
    void aWeldThatIsNotThereIsNotFound() {
        Shell.Result result = shop.run("technique", "plan", "24-0001", "GW-999", "--source", "IR-5512", "--at", SHOT);
        assertThat(result.code()).isEqualTo(Main.EXIT_NOT_FOUND);
        assertThat(result.failure()).isEqualTo(NotFoundError.CODE);
    }

    @Test
    void filmsAreComparedInATable() {
        List<String> lines =
                compare("d7,d5,d4", "--shot", "double wall single image").lines();
        assertThat(Text.words(lines.get(0))).containsExactly("film", "each", "n", "source", "out", "clean");
        assertThat(lines.get(2)).startsWith("d7");
        assertThat(lines.get(4)).startsWith("d4");
    }

    @Test
    void comparingNoFilmsIsRefused() {
        Shell.Result result = compare(" , ");
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void theComparisonOnlyPrintsWhatChangesBetweenFilms() {
        String output = compare("d7,d4", "--shot", "double wall single image").out();
        assertThat(output).doesNotContain("unsharpness", "penetrated");
    }
}
