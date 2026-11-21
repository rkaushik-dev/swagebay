package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Weld commands.
 *
 * <p>A weld is described the way the line list describes it: a wall, a diameter and a cap, each as
 * text with its unit in it. Leaving the diameter out means plate, which is the same convention the
 * joint type uses, so a caller who knows one knows the other.
 */
@Command(
        name = "weld",
        description = "Add and list welds.",
        subcommands = {WeldCommands.AddCommand.class, WeldCommands.ListCommand.class})
final class WeldCommands extends Group {

    /** Add a weld to a job. */
    @Command(name = "add", description = "Add a weld to a job.")
    static final class AddCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "WELD")
        private String weld;

        @Option(
                names = "--material",
                paramLabel = "TEXT",
                defaultValue = "carbon_steel",
                showDefaultValue = Visibility.ALWAYS)
        private String materialKey;

        @Option(
                names = "--wall",
                paramLabel = "TEXT",
                required = true,
                description = "for example 10.97 mm or 0.375 in")
        private String wall;

        @Option(names = "--diameter", paramLabel = "TEXT", description = "leave out for plate")
        private String diameter;

        @Option(names = "--cap", paramLabel = "TEXT", defaultValue = "0 mm", showDefaultValue = Visibility.ALWAYS)
        private String cap;

        @Option(names = "--length", paramLabel = "TEXT", required = true)
        private String weldLength;

        @Option(names = "--welder", paramLabel = "TEXT", required = true)
        private String welder;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            LocalDate welded = Gates.asDay(on);
            Joint joint = new Joint(
                    diameter == null ? JointForm.PLATE_BUTT : JointForm.PIPE_GIRTH,
                    Length.parse(wall),
                    Materials.material(materialKey),
                    Optional.ofNullable(diameter).map(Length::parse),
                    Length.parse(cap),
                    Optional.empty());
            inWork(work -> JobService.addWeld(
                    work, identity, new NewWeld(WeldId.parse(weld), joint, Length.parse(weldLength), welder, welded)));
            out().println("added " + weld + " to " + identity);
            return Main.EXIT_OK;
        }
    }

    /** List the welds on a job. */
    @Command(name = "list", description = "List the welds on a job.")
    static final class ListCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            List<List<String>> rows = fromWork(work -> work.jobs().get(identity).getWelds().stream()
                    .sorted(Comparator.comparingInt(WeldRow::getNumber).thenComparingInt(WeldRow::getRepairRound))
                    .map(row -> List.of(
                            new WeldId(row.getPrefix(), row.getNumber(), row.getRepairRound()).toString(),
                            row.getMaterialKey(),
                            row.getWall().formatMm(),
                            row.getOutsideDiameter().map(Length::formatMm).orElse("plate"),
                            row.getWelder(),
                            String.valueOf(row.getRadiographs().size())))
                    .toList());
            out().println(Render.table(
                    List.of("weld", "material", "wall", "od", "welder", "films"), rows, List.of(2, 3, 5)));
            return Main.EXIT_OK;
        }
    }
}
