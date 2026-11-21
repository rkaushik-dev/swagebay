package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Non conformance commands, which the coordinator runs and not the crew.
 *
 * <p>Raise, decide, close, and two listings. The listings are separate commands rather than flags
 * because the open list is what a Monday meeting works through and the job list is what goes in
 * front of a client, and those want different columns.
 *
 * <p>Close exits non zero when it is refused, so the close out script for a job stops rather than
 * carrying on and producing a package with an open report behind it.
 */
@Command(
        name = "ncr",
        description = "Raise and work through non conformance reports.",
        subcommands = {
            NonconformanceCommands.RaiseCommand.class,
            NonconformanceCommands.DecideCommand.class,
            NonconformanceCommands.CloseCommand.class,
            NonconformanceCommands.OpenCommand.class,
            NonconformanceCommands.JobCommand.class
        })
final class NonconformanceCommands extends Group {

    /** The dispositions as they are typed: the member names in lower case. */
    static final List<String> DISPOSITIONS = Arrays.stream(Disposition.values())
            .map(disposition -> disposition.name().toLowerCase(Locale.ROOT))
            .toList();

    private static final List<String> COLUMNS = List.of("reference", "job", "item", "disposition", "days", "state");

    /** A disposition on the command line, one of {@link #DISPOSITIONS}. */
    static final class Dispositions extends Choice {

        @Override
        List<String> choices() {
            return DISPOSITIONS;
        }
    }

    private static List<List<String>> rows(List<Report> reports, LocalDate on) {
        return reports.stream()
                .map(report -> List.of(
                        report.reference(),
                        report.job(),
                        report.item(),
                        report.disposition().map(Disposition::toString).orElse("undecided"),
                        String.valueOf(report.daysOpen(on)),
                        report.isOpen()
                                ? "open"
                                : report.closedOn().map(LocalDate::toString).orElseThrow()))
                .toList();
    }

    /** Raise a report against an item on a job. */
    @Command(name = "raise", description = "Raise a report against an item on a job.")
    static final class RaiseCommand extends Leaf {

        @Parameters(paramLabel = "REFERENCE")
        private String reference;

        @Option(names = "--job", paramLabel = "TEXT", required = true)
        private String job;

        @Option(names = "--item", paramLabel = "TEXT", required = true, description = "the weld, spool or component")
        private String item;

        @Option(names = "--requirement", paramLabel = "TEXT", required = true, description = "the clause it fails")
        private String requirement;

        @Option(names = "--account", paramLabel = "TEXT", required = true, description = "what is actually wrong")
        private String account;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String badge;

        @Override
        public Integer call() {
            Report report = new Report(reference, job, item, requirement, account, Gates.asDay(on), badge);
            inWork(work -> work.nonconformances().raiseReport(report));
            out().println(report.describe());
            return Main.EXIT_OK;
        }
    }

    /** Set the disposition, with the paperwork it needs. */
    @Command(name = "decide", description = "Set the disposition, with the paperwork it needs.")
    static final class DecideCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "REFERENCE")
        private String reference;

        @Parameters(
                index = "1",
                paramLabel = "DISPOSITION",
                converter = Dispositions.class,
                completionCandidates = Dispositions.class,
                description = "One of: ${COMPLETION-CANDIDATES}.")
        private String disposition;

        @Option(names = "--concession", paramLabel = "TEXT", description = "required for repair and use as is")
        private String concession = "";

        @Option(names = "--removal", paramLabel = "TEXT", description = "required for reject")
        private String removal = "";

        @Override
        public Integer call() {
            Report report = fromWork(work -> work.nonconformances()
                    .decide(reference, Naming.dispositionNamed(disposition), concession, removal));
            out().println(report.describe());
            return Main.EXIT_OK;
        }
    }

    /** Close a report. Somebody other than the author has to do it. */
    @Command(name = "close", description = "Close a report. Somebody other than the author has to do it.")
    static final class CloseCommand extends Leaf {

        @Parameters(paramLabel = "REFERENCE")
        private String reference;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String badge;

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            Report report = fromWork(work -> work.nonconformances().close(reference, day, badge));
            out().println(report.describe());
            return Main.EXIT_OK;
        }
    }

    /** Every open report, with the overdue ones called out. */
    @Command(name = "open", description = "Every open report, with the overdue ones called out.")
    static final class OpenCommand extends Leaf {

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            List<Report> reports = fromWork(work -> work.nonconformances().openReports());
            if (reports.isEmpty()) {
                out().println("nothing open");
                return Main.EXIT_OK;
            }
            out().println(Render.table(COLUMNS, rows(reports, day), List.of(4)));
            List<String> overdue = reports.stream()
                    .filter(report -> report.isOverdue(day))
                    .map(Report::reference)
                    .toList();
            if (!overdue.isEmpty()) {
                out().println(Render.heading("overdue"));
                out().println(Render.bullets(overdue));
            }
            return Main.EXIT_OK;
        }
    }

    /** Every report on one job, and what is stopping it being signed off. */
    @Command(name = "job", description = "Every report on one job, and what is stopping it being signed off.")
    static final class JobCommand extends Leaf {

        /** The reports on the job, and what among them stops the sign off. */
        private record OnJob(List<Report> reports, List<String> blocking) {}

        @Parameters(paramLabel = "JOB")
        private String job;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            OnJob found = fromWork(work -> new OnJob(
                    work.nonconformances().forJob(job),
                    work.nonconformances().registerFor(job).blocksClosing(job)));
            if (found.reports().isEmpty()) {
                out().println("nothing raised against " + job);
                return Main.EXIT_OK;
            }
            out().println(Render.table(COLUMNS, rows(found.reports(), day), List.of(4)));
            return Gates.report(out(), "stopping the sign off", List.of(Map.entry("", found.blocking())));
        }
    }
}
