package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.audit.Entry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Reading the audit trail from a terminal.
 *
 * <p>This exists because the people who need the trail are not the people with a browser open. A
 * reader disputing a call, or a supervisor answering a client on the phone, wants one line per
 * change with the old value on it, and they want it without joining two tables in their head.
 *
 * <p>Three commands, and they are deliberately not one command with a mode flag. The history of one
 * record, one person's day, and the changes made after somebody signed are three different questions
 * with three different shapes of answer, and a flag that changes the columns is a flag nobody
 * remembers.
 */
@Command(
        name = "history",
        description = "Read the audit trail.",
        subcommands = {
            HistoryCommands.RecordCommand.class,
            HistoryCommands.ActorCommand.class,
            HistoryCommands.AfterSigningCommand.class
        })
final class HistoryCommands extends Group {

    /** The kinds of record the trail is kept for. */
    static final List<String> RECORDS = List.of("radiograph", "weld", "job", "source", "survey");

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);
    private static final List<String> EVERY_COLUMN = List.of("when", "who", "what", "field", "was", "became", "why");
    private static final List<String> WITHOUT_ACTOR = List.of("when", "what", "field", "was", "became", "why");

    /** A record kind on the command line, one of {@link #RECORDS}. */
    static final class Records extends Choice {

        @Override
        List<String> choices() {
            return RECORDS;
        }
    }

    private static List<List<String>> rows(List<Entry> entries, boolean withActor) {
        List<List<String>> rows = new ArrayList<>();
        for (Entry entry : entries) {
            List<String> row = new ArrayList<>();
            row.add(MINUTE.format(entry.at()));
            if (withActor) {
                row.add(entry.actor());
            }
            row.add(entry.action().toString());
            row.add(orDash(entry.fieldName()));
            row.add(orDash(entry.was()));
            row.add(orDash(entry.became()));
            row.add(orDash(entry.reason()));
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    private static String orDash(String text) {
        return text.isEmpty() ? "-" : text;
    }

    /** Every change to one record, oldest first. */
    @Command(name = "record", description = "Every change to one record, oldest first.")
    static final class RecordCommand extends Leaf {

        @Parameters(
                index = "0",
                paramLabel = "RECORD",
                converter = Records.class,
                completionCandidates = Records.class,
                description = "One of: ${COMPLETION-CANDIDATES}.")
        private String record;

        @Parameters(index = "1", paramLabel = "IDENTITY")
        private String identity;

        @Override
        public Integer call() {
            List<Entry> entries = fromWork(work -> work.audit().forRecord(record, identity));
            if (entries.isEmpty()) {
                out().println("nothing recorded against " + record + " " + identity);
                return Main.EXIT_OK;
            }
            out().println(Render.heading(record + " " + identity));
            out().println(Render.table(EVERY_COLUMN, rows(entries, true)));
            return Main.EXIT_OK;
        }
    }

    /** What one badge changed, over one day or a few. */
    @Command(name = "actor", description = "What one badge changed, over one day or a few.")
    static final class ActorCommand extends Leaf {

        @Parameters(paramLabel = "BADGE")
        private String badge;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Option(
                names = "--days",
                paramLabel = "INTEGER RANGE",
                defaultValue = "1",
                showDefaultValue = Visibility.ALWAYS,
                converter = Counts.Positive.class)
        private int days;

        @Override
        public Integer call() {
            LocalDateTime until = on.plusDays(days);
            List<Entry> entries = fromWork(work -> work.audit().byActor(badge, on, until));
            if (entries.isEmpty()) {
                out().println(badge + " changed nothing between " + on.toLocalDate() + " and " + until.toLocalDate());
                return Main.EXIT_OK;
            }
            out().println(Render.heading(badge + " from " + on.toLocalDate() + " to " + until.toLocalDate()));
            out().println(Render.table(WITHOUT_ACTOR, rows(entries, false)));
            return Main.EXIT_OK;
        }
    }

    /** Changes made after the record was signed. Exits 4 when there are any. */
    @Command(
            name = "after-signing",
            description = "Changes made after the record was signed. Exits 4 when there are any.")
    static final class AfterSigningCommand extends Leaf {

        @Parameters(
                index = "0",
                paramLabel = "RECORD",
                converter = Records.class,
                completionCandidates = Records.class,
                description = "One of: ${COMPLETION-CANDIDATES}.")
        private String record;

        @Parameters(index = "1", paramLabel = "IDENTITY")
        private String identity;

        @Override
        public Integer call() {
            List<Entry> entries = fromWork(work -> work.audit().changedAfterSigning(record, identity));
            if (entries.isEmpty()) {
                out().println("nothing changed on " + record + " " + identity + " after it was signed");
                return Main.EXIT_OK;
            }
            out().println(Render.table(EVERY_COLUMN, rows(entries, true)));
            return Gates.report(
                    out(),
                    record + " " + identity + " changed after signing",
                    List.of(Map.entry("", entries.stream().map(Entry::describe).toList())));
        }
    }
}
