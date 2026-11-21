package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.services.PlanRequest;
import io.github.rkaushikdev.swagebay.services.PlanningService;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The technique commands, which are what gets run before every shot.
 *
 * <p>The plan output is a sheet, printed as a label and value block, and then the warnings
 * underneath it with a blank line between. The blank line matters: a crew reading this off a phone
 * screen at the side of a road scrolls until the numbers stop, and warnings that run straight on
 * from the numbers get read as more numbers.
 *
 * <p>The compare command prints one row per film with the exposure and the total source out, which
 * are the two numbers a shooter actually chooses on. It does not print the geometry, because the
 * geometry does not change between films and a column of identical values teaches nobody anything.
 */
@Command(
        name = "technique",
        description = "Plan a shot and compare films.",
        subcommands = {TechniqueCommands.PlanCommand.class, TechniqueCommands.CompareCommand.class})
final class TechniqueCommands extends Group {

    /** The density a printed sheet is planned to. */
    private static final BigDecimal SHEET_DENSITY = new BigDecimal("2.0");

    /** Print a technique sheet. */
    @Command(name = "plan", description = "Print a technique sheet.")
    static final class PlanCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "WELD")
        private String weld;

        @Option(names = "--source", paramLabel = "TEXT", required = true)
        private String serial;

        @Option(names = "--film", paramLabel = "TEXT", defaultValue = "d7", showDefaultValue = Visibility.ALWAYS)
        private String film;

        @Option(names = "--at", paramLabel = Dates.MOMENT_LABEL, required = true, converter = Dates.Timestamp.class)
        private LocalDateTime at;

        @Option(names = "--shot", paramLabel = "TEXT", description = "leave out to let the planner pick")
        private Optional<String> shot;

        @Option(names = "--film-side", description = "put the indicator on the film side")
        private boolean filmSide;

        @Option(names = "--window", paramLabel = "INTEGER", description = "clear time in minutes")
        private Optional<Integer> window;

        @Override
        public Integer call() {
            PlanRequest request = PlanRequest.builder(WeldId.parse(weld), serial, film, at)
                    .shot(Naming.optionalShotNamed(shot))
                    .indicatorSide(filmSide ? IndicatorSide.FILM : IndicatorSide.SOURCE)
                    .window(window.map(minutes -> Duration.fromMinutes(minutes)))
                    .density(SHEET_DENSITY)
                    .build();
            TechniqueSheet sheet = fromWork(work -> PlanningService.plan(work, JobNumber.parse(number), request));
            out().println(Render.heading("technique sheet " + weld));
            out().println(Render.pairs(List.of(
                    Map.entry("joint", sheet.joint().describe()),
                    Map.entry("shot", sheet.shot().toString()),
                    Map.entry("penetrated", sheet.penetrated().formatMm()),
                    Map.entry("source to film", sheet.sourceToFilm().formatMm(0)),
                    Map.entry(
                            "unsharpness",
                            sheet.geometry().geometricUnsharpness().formatMm(3)),
                    Map.entry("indicator", sheet.indicator().describe()),
                    Map.entry("exposures", String.valueOf(sheet.exposures())),
                    Map.entry("each", sheet.exposure().timer()),
                    Map.entry("source out", sheet.totalSourceOut().asTimer()),
                    Map.entry("activity", sheet.exposure().activity().formatCurie()),
                    Map.entry("clean", Render.yesNo(sheet.isClean())))));
            if (!sheet.warnings().isEmpty()) {
                out().println();
                out().println(Render.bullets(sheet.warnings()));
            }
            return Main.EXIT_OK;
        }
    }

    /** Print the same shot on several films, side by side. */
    @Command(name = "compare", description = "Print the same shot on several films, side by side.")
    static final class CompareCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "WELD")
        private String weld;

        @Option(names = "--source", paramLabel = "TEXT", required = true)
        private String serial;

        @Option(names = "--films", paramLabel = "TEXT", required = true, description = "comma separated film keys")
        private String films;

        @Option(names = "--at", paramLabel = Dates.MOMENT_LABEL, required = true, converter = Dates.Timestamp.class)
        private LocalDateTime at;

        @Option(names = "--shot", paramLabel = "TEXT")
        private Optional<String> shot;

        @Override
        public Integer call() {
            List<String> keys = Arrays.stream(films.split(Pattern.quote(","), -1))
                    .map(Text::strip)
                    .filter(key -> !key.isEmpty())
                    .toList();
            if (keys.isEmpty()) {
                throw new ValidationError("name at least one film to compare", Context.of("films", films));
            }
            PlanRequest request = PlanRequest.builder(WeldId.parse(weld), serial, keys.getFirst(), at)
                    .shot(Naming.optionalShotNamed(shot))
                    .build();
            Map<String, TechniqueSheet> sheets =
                    fromWork(work -> PlanningService.compareFilms(work, JobNumber.parse(number), request, keys));
            List<List<String>> rows = keys.stream()
                    .map(key -> {
                        TechniqueSheet sheet = sheets.get(key);
                        return List.of(
                                key,
                                sheet.exposure().timer(),
                                String.valueOf(sheet.exposures()),
                                sheet.totalSourceOut().asTimer(),
                                Render.yesNo(sheet.isClean()));
                    })
                    .toList();
            out().println(Render.table(List.of("film", "each", "n", "source out", "clean"), rows, List.of(1, 2, 3)));
            return Main.EXIT_OK;
        }
    }
}
