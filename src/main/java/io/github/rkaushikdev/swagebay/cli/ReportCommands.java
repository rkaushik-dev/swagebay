package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.reporting.Chain;
import io.github.rkaushikdev.swagebay.reporting.Exports;
import io.github.rkaushikdev.swagebay.reporting.ReportPackage;
import io.github.rkaushikdev.swagebay.reporting.Traceability;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.TypeConversionException;

/**
 * Report commands.
 *
 * <p>The package command prints the faults first and the summary second, which is the opposite of
 * the order they are assembled in and the right order to read them. Somebody running this wants to
 * know whether the job can be sent, and the answer to that is the fault list.
 *
 * <p>The export command writes to a directory rather than to standard output, because there is a
 * file per sheet and piping several files down one pipe is not a thing. It prints the paths it wrote
 * so a script can pick them up.
 */
@Command(
        name = "report",
        description = "Assemble and export the report package.",
        subcommands = {
            ReportCommands.PackageCommand.class,
            ReportCommands.WeldsCommand.class,
            ReportCommands.TraceabilityCommand.class,
            ReportCommands.ExportCommand.class,
            ReportCommands.SheetCommand.class
        })
final class ReportCommands extends Group {

    /** The sheets the package is published as. */
    static final class Sheets extends Choice {

        @Override
        List<String> choices() {
            return Exports.sheets();
        }
    }

    /**
     * A directory to write into, which need not be there yet; a file already at that path is a usage
     * error naming it.
     */
    static final class Into implements ITypeConverter<Path> {

        @Override
        public Path convert(String text) {
            Path path = Path.of(text);
            if (Files.exists(path) && !Files.isDirectory(path)) {
                throw new TypeConversionException("Directory '" + text + "' is a file.");
            }
            return path;
        }
    }

    /** Print the package, faults first. */
    @Command(name = "package", description = "Print the package, faults first.")
    static final class PackageCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            ReportPackage assembled = fromWork(work -> ReportPackage.assemble(work, identity));
            out().println(Render.heading("report package " + identity));
            if (!assembled.faults().isEmpty()) {
                out().println(Render.bullets(assembled.faults()));
                out().println();
            }
            out().println(Render.pairs(List.of(
                    Map.entry("client", assembled.client()),
                    Map.entry("site", assembled.site()),
                    Map.entry("code", assembled.code().value()),
                    Map.entry("procedure", assembled.procedureNumber()),
                    Map.entry("welds", String.valueOf(assembled.welds().size())),
                    Map.entry("welds shot", String.valueOf(assembled.weldsShot())),
                    Map.entry("welds repaired", String.valueOf(assembled.weldsRepaired())),
                    Map.entry("repair rate", assembled.repairRate().describe()),
                    Map.entry(
                            "radiographs",
                            String.valueOf(assembled.radiographs().size())),
                    Map.entry("complete", Render.yesNo(assembled.isComplete())))));
            return Main.EXIT_OK;
        }
    }

    /** Print the weld summary as a table. */
    @Command(name = "welds", description = "Print the weld summary as a table.")
    static final class WeldsCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            ReportPackage assembled = fromWork(work -> ReportPackage.assemble(work, JobNumber.parse(number)));
            List<List<String>> rows = assembled.welds().stream()
                    .map(line -> List.of(
                            line.weld(),
                            line.wall().formatMm(),
                            line.welder(),
                            String.valueOf(line.radiographs()),
                            line.disposition(),
                            String.join(" ", line.clauses())))
                    .toList();
            out().println(Render.table(
                    List.of("weld", "wall", "welder", "films", "disposition", "clauses"), rows, List.of(1, 3)));
            return Main.EXIT_OK;
        }
    }

    /** Print only the chains that do not hold. */
    @Command(name = "traceability", description = "Print only the chains that do not hold.")
    static final class TraceabilityCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            List<Chain> broken = fromWork(work -> Traceability.brokenChains(work, identity.year(), identity.serial()));
            if (broken.isEmpty()) {
                out().println("every chain holds");
                return Main.EXIT_OK;
            }
            broken.forEach(chain -> out().println(chain.describe()));
            return Main.EXIT_OK;
        }
    }

    /** Write the CSV sheets into a directory and print what was written. */
    @Command(name = "export", description = "Write the csv sheets into a directory and print what was written.")
    static final class ExportCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Option(
                names = "--into",
                paramLabel = "DIRECTORY",
                defaultValue = ".",
                showDefaultValue = Visibility.ALWAYS,
                converter = Into.class)
        private Path into;

        @Override
        public Integer call() {
            ReportPackage assembled = fromWork(work -> ReportPackage.assemble(work, JobNumber.parse(number)));
            Map<String, String> files = Exports.toFiles(assembled);
            try {
                Files.createDirectories(into);
                for (Map.Entry<String, String> file : files.entrySet()) {
                    Path written = withoutDots(into.resolve(file.getKey()));
                    Files.writeString(written, file.getValue(), StandardCharsets.UTF_8);
                    out().println(written);
                }
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
            return Main.EXIT_OK;
        }

        /**
         * The path with every {@code .} element left out, so a file written into the working
         * directory prints as its bare name. A {@code ..} is kept as it was given.
         */
        private static Path withoutDots(Path path) {
            Path kept = path.getRoot();
            for (Path element : path) {
                if (!element.toString().equals(".")) {
                    kept = kept == null ? element : kept.resolve(element);
                }
            }
            return kept == null ? path : kept;
        }
    }

    /** Print one CSV sheet to standard output. */
    @Command(name = "sheet", description = "Print one csv sheet to standard output.")
    static final class SheetCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(
                index = "1",
                paramLabel = "WHICH",
                converter = Sheets.class,
                completionCandidates = Sheets.class,
                description = "One of: ${COMPLETION-CANDIDATES}.")
        private String which;

        @Override
        public Integer call() {
            String text =
                    fromWork(work -> Exports.toText(ReportPackage.assemble(work, JobNumber.parse(number)), which));
            out().print(text);
            return Main.EXIT_OK;
        }
    }
}
