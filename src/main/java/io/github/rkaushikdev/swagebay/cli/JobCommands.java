package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Job commands.
 *
 * <p>The listing commands print a table and the write commands print one line naming what they did.
 * That split is deliberate. Anything a person reads goes in a table. Anything a script reads is one
 * line with the identifier on it, so piping the output through {@code cut} works and nobody has to
 * parse a table.
 *
 * <p>Dates default to today and can be overridden. Every command that writes takes the date it
 * happened rather than assuming the moment it is typed, because the paperwork is entered the morning
 * after the night shift and dating it today is how a job ends up with welds shot before they were
 * welded.
 */
@Command(
        name = "job",
        description = "Open, list and close jobs.",
        subcommands = {
            JobCommands.OpenCommand.class,
            JobCommands.ListCommand.class,
            JobCommands.ShowCommand.class,
            JobCommands.CloseCommand.class
        })
final class JobCommands extends Group {

    /** Open a job and print its number. */
    @Command(name = "open", description = "Open a job and print its number.")
    static final class OpenCommand extends Leaf {

        @Option(names = "--client", paramLabel = "TEXT", required = true)
        private String client;

        @Option(names = "--site", paramLabel = "TEXT", required = true)
        private String site;

        @Option(names = "--code", paramLabel = "TEXT", defaultValue = "API 1104", showDefaultValue = Visibility.ALWAYS)
        private String code;

        @Option(names = "--procedure", paramLabel = "TEXT", required = true)
        private String procedure;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            LocalDate opened = Gates.asDay(on);
            JobNumber number = fromWork(work ->
                    JobService.openJob(work, new NewJob(client, site, Naming.codeNamed(code), procedure, opened)));
            out().println("opened " + number);
            return Main.EXIT_OK;
        }
    }

    /** List the jobs open on a day. */
    @Command(name = "list", description = "List the jobs open on a day.")
    static final class ListCommand extends Leaf {

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            List<List<String>> rows = fromWork(work -> work.jobs().openOn(day).stream()
                    .map(row -> List.of(
                            new JobNumber(row.getYear(), row.getSerial()).toString(),
                            row.getClient(),
                            row.getSite(),
                            row.getAcceptanceCode(),
                            String.valueOf(row.getWelds().size())))
                    .toList());
            out().println(Render.table(List.of("job", "client", "site", "code", "welds"), rows, List.of(4)));
            return Main.EXIT_OK;
        }
    }

    /** Print one job and what is outstanding on it. */
    @Command(name = "show", description = "Print one job and what is outstanding on it.")
    static final class ShowCommand extends Leaf {

        /** The job's label block, and what stands between it and being closed. */
        private record Shown(String block, List<String> pending) {}

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            Shown shown = fromWork(work -> {
                JobRow row = work.jobs().get(identity);
                List<String> pending = JobService.outstanding(work, identity);
                String block = Render.pairs(List.of(
                        Map.entry("job", identity.toString()),
                        Map.entry("client", row.getClient()),
                        Map.entry("site", row.getSite()),
                        Map.entry("code", row.getAcceptanceCode()),
                        Map.entry("procedure", row.getProcedureNumber()),
                        Map.entry("opened", row.getOpenedOn().toString()),
                        Map.entry(
                                "closed",
                                row.getClosedOn().map(LocalDate::toString).orElse("no")),
                        Map.entry("welds", String.valueOf(row.getWelds().size())),
                        Map.entry("clear", Render.yesNo(pending.isEmpty()))));
                return new Shown(block, pending);
            });
            out().println(shown.block());
            for (String item : shown.pending()) {
                out().println("  outstanding: " + item);
            }
            return Main.EXIT_OK;
        }
    }

    /** Close a job, refusing while anything is outstanding. */
    @Command(name = "close", description = "Close a job, refusing while anything is outstanding.")
    static final class CloseCommand extends Leaf {

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            JobNumber identity = JobNumber.parse(number);
            LocalDate day = Gates.asDay(on);
            inWork(work -> JobService.closeJob(work, identity, day));
            out().println("closed " + identity);
            return Main.EXIT_OK;
        }
    }
}
