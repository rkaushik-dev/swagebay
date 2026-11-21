package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.calibration.Certificate;
import io.github.rkaushikdev.swagebay.calibration.Instrument;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.persistence.models.LotRow;
import io.github.rkaushikdev.swagebay.safety.Incident;
import io.github.rkaushikdev.swagebay.safety.Occurrence;
import io.github.rkaushikdev.swagebay.services.RegisterService;
import io.github.rkaushikdev.swagebay.stock.Issue;
import io.github.rkaushikdev.swagebay.stock.Lot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Register commands, which are all variations on what is outstanding today.
 *
 * <p>Each of these is the query somebody runs on a Monday morning. The listings print nothing when
 * there is nothing, which is deliberate: a command that prints "nothing overdue" gets its output
 * piped somewhere and the empty case becomes a line that has to be filtered out. Silence composes.
 */
@Command(
        name = "register",
        description = "Incidents, calibrations, stock and the archive.",
        subcommands = {
            RegisterCommands.IncidentCommand.class,
            RegisterCommands.NotifyCommand.class,
            RegisterCommands.OutstandingCommand.class,
            RegisterCommands.CalibrateCommand.class,
            RegisterCommands.ExpiredCommand.class,
            RegisterCommands.ReceiveCommand.class,
            RegisterCommands.IssueCommand.class,
            RegisterCommands.StockCommand.class
        })
final class RegisterCommands extends Group {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    /** Record an abnormal occurrence and print when it is due. */
    @Command(name = "incident", description = "Record an abnormal occurrence and print when it is due.")
    static final class IncidentCommand extends Leaf {

        @Parameters(paramLabel = "REFERENCE")
        private String reference;

        @Option(
                names = "--kind",
                paramLabel = "TEXT",
                required = true,
                description = "the occurrence, for example OVEREXPOSURE")
        private String kind;

        @Option(names = "--found", paramLabel = Dates.MOMENT_LABEL, required = true, converter = Dates.Timestamp.class)
        private LocalDateTime found;

        @Option(names = "--site", paramLabel = "TEXT", required = true)
        private String site;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String reportedBy;

        @Option(names = "--account", paramLabel = "TEXT", required = true)
        private String account;

        @Override
        public Integer call() {
            Occurrence occurrence = Occurrence.valueOf(kind.toUpperCase(Locale.ROOT));
            Incident entry = new Incident(reference, occurrence, found, site, reportedBy, account);
            inWork(work -> RegisterService.recordIncident(work, entry));
            out().println(entry.describe());
            return Main.EXIT_OK;
        }
    }

    /** Record that an incident has been reported. */
    @Command(name = "notify", description = "Record that an incident has been reported.")
    static final class NotifyCommand extends Leaf {

        @Parameters(paramLabel = "REFERENCE")
        private String reference;

        @Option(names = "--at", paramLabel = Dates.MOMENT_LABEL, required = true, converter = Dates.Timestamp.class)
        private LocalDateTime at;

        @Override
        public Integer call() {
            inWork(work -> RegisterService.notifyIncident(work, reference, at));
            out().println(reference + " reported");
            return Main.EXIT_OK;
        }
    }

    /** List the incidents nobody has reported yet. */
    @Command(name = "outstanding", description = "List the incidents nobody has reported yet.")
    static final class OutstandingCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = fromWork(work -> work.incidents().unreported().stream()
                    .map(RegisterCommands::incidentRow)
                    .toList());
            if (!rows.isEmpty()) {
                out().println(Render.table(List.of("reference", "occurrence", "found", "site"), rows));
            }
            return Main.EXIT_OK;
        }
    }

    private static List<String> incidentRow(IncidentRow row) {
        return List.of(row.getReference(), row.getOccurrence(), MINUTE.format(row.getDiscoveredAt()), row.getSite());
    }

    /** Record a calibration certificate. */
    @Command(name = "calibrate", description = "Record a calibration certificate.")
    static final class CalibrateCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(
                names = "--kind",
                paramLabel = "TEXT",
                defaultValue = "SURVEY_METER",
                showDefaultValue = Visibility.ALWAYS)
        private String kind;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Option(names = "--due", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime due;

        @Option(names = "--laboratory", paramLabel = "TEXT", defaultValue = "NRC", showDefaultValue = Visibility.ALWAYS)
        private String laboratory;

        @Option(names = "--certificate", paramLabel = "TEXT", description = "the laboratory reference")
        private String certificate = "";

        @Override
        public Integer call() {
            LocalDate dueOn = due.toLocalDate();
            inWork(work -> RegisterService.recordCalibration(
                    work,
                    serial,
                    Instrument.valueOf(kind.toUpperCase(Locale.ROOT)),
                    new Certificate(
                            on.toLocalDate(), dueOn, laboratory, certificate.isEmpty() ? serial : certificate)));
            out().println(serial + " calibrated until " + dueOn);
            return Main.EXIT_OK;
        }
    }

    /** List the instruments out of calibration. */
    @Command(name = "expired", description = "List the instruments out of calibration.")
    static final class ExpiredCommand extends Leaf {

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Override
        public Integer call() {
            List<String> serials = fromWork(work -> work.calibrations().expiredOn(on.toLocalDate()));
            if (!serials.isEmpty()) {
                out().println(Render.table(
                        List.of("serial"), serials.stream().map(List::of).toList()));
            }
            return Main.EXIT_OK;
        }
    }

    /** Put a lot of film on the shelf. */
    @Command(name = "receive", description = "Put a lot of film on the shelf.")
    static final class ReceiveCommand extends Leaf {

        @Parameters(paramLabel = "LOT_NUMBER")
        private String lotNumber;

        @Option(names = "--film", paramLabel = "TEXT", defaultValue = "d7", showDefaultValue = Visibility.ALWAYS)
        private String film;

        @Option(names = "--size", paramLabel = "TEXT", defaultValue = "4x10", showDefaultValue = Visibility.ALWAYS)
        private String size;

        @Option(names = "--sheets", paramLabel = "INTEGER", required = true)
        private int sheets;

        @Option(names = "--received", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime received;

        @Option(names = "--expires", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime expires;

        @Override
        public Integer call() {
            inWork(work -> RegisterService.receiveLot(
                    work, new Lot(lotNumber, film, size, sheets, received.toLocalDate(), expires.toLocalDate())));
            out().println("received " + lotNumber + ", " + sheets + " sheets");
            return Main.EXIT_OK;
        }
    }

    /** Take film off the shelf and say which lots it came from. */
    @Command(name = "issue", description = "Take film off the shelf and say which lots it came from.")
    static final class IssueCommand extends Leaf {

        /** Where the sheets came from, and how many are left behind. */
        private record Issued(List<Issue> taken, int left) {}

        @Option(names = "--film", paramLabel = "TEXT", defaultValue = "d7", showDefaultValue = Visibility.ALWAYS)
        private String film;

        @Option(names = "--size", paramLabel = "TEXT", defaultValue = "4x10", showDefaultValue = Visibility.ALWAYS)
        private String size;

        @Option(names = "--sheets", paramLabel = "INTEGER", required = true)
        private int sheets;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Override
        public Integer call() {
            LocalDate day = on.toLocalDate();
            Issued issued = fromWork(work -> {
                List<Issue> taken = RegisterService.issueFilm(work, day, film, size, sheets);
                return new Issued(taken, RegisterService.sheetsOnHand(work, day, film, size));
            });
            for (Issue issue : issued.taken()) {
                out().println(issue.lotNumber() + " " + issue.sheets());
            }
            out().println(issued.left() + " sheets left");
            return Main.EXIT_OK;
        }
    }

    /** List the usable lots, shortest dated first. */
    @Command(name = "stock", description = "List the usable lots, shortest dated first.")
    static final class StockCommand extends Leaf {

        /** The lots that can be used, and the ones past their date. */
        private record Shelf(List<List<String>> rows, List<String> expired) {}

        @Option(names = "--film", paramLabel = "TEXT", defaultValue = "d7", showDefaultValue = Visibility.ALWAYS)
        private String film;

        @Option(names = "--size", paramLabel = "TEXT", defaultValue = "4x10", showDefaultValue = Visibility.ALWAYS)
        private String size;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Override
        public Integer call() {
            LocalDate day = on.toLocalDate();
            Shelf shelf = fromWork(work -> new Shelf(
                    work.stock().usableOn(day, film, size).stream()
                            .map(StockCommand::lotRow)
                            .toList(),
                    work.stock().expiredOn(day)));
            if (!shelf.rows().isEmpty()) {
                out().println(Render.table(List.of("lot", "sheets", "expires"), shelf.rows(), List.of(1)));
            }
            for (String lotNumber : shelf.expired()) {
                out().println("expired: " + lotNumber);
            }
            return Main.EXIT_OK;
        }

        private static List<String> lotRow(LotRow row) {
            return List.of(
                    row.getLotNumber(),
                    String.valueOf(row.getSheets()),
                    row.getExpiresOn().toString());
        }
    }
}
