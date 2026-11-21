package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.services.Line;
import io.github.rkaushikdev.swagebay.services.Load;
import io.github.rkaushikdev.swagebay.services.MorningService;
import io.github.rkaushikdev.swagebay.services.Sheet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The morning command, run once before anybody gets in a truck.
 *
 * <p>It prints the sheet in the order the sheet is meant to be worked through and exits non zero
 * when anything is outstanding, so it can be the first line of the yard script. Things due this week
 * and reports running late are listed apart from what stops the day, so whoever reads the sheet can
 * tell them from it.
 */
@Command(name = "morning", description = "Everything outstanding, in the order it has to be dealt with.")
final class MorningCommand extends Leaf {

    /** What the morning reads, all in one unit of work. */
    private record Morning(Sheet sheet, List<String> waiting, List<Load> pairings) {}

    @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
    private Optional<LocalDateTime> on;

    @Option(names = "--loads", description = "also list the pairings that could go out")
    private boolean loads;

    @Override
    public Integer call() {
        LocalDate day = Gates.asDay(on);
        Morning morning = fromWork(work -> new Morning(
                MorningService.sheetFor(work, day),
                MorningService.filmsWaiting(work, day),
                loads ? MorningService.loadsReady(work, day) : List.of()));
        if (!morning.waiting().isEmpty()) {
            out().println(Render.heading("films waiting to be read"));
            out().println(Render.bullets(morning.waiting()));
        }
        if (loads) {
            out().println(Render.heading("ready to go out"));
            if (morning.pairings().isEmpty()) {
                out().println("nothing is cleared to leave the shop");
            } else {
                out().println(Render.table(
                        List.of("device", "source"),
                        morning.pairings().stream()
                                .map(load -> List.of(load.deviceSerial(), load.sourceSerial()))
                                .toList()));
            }
        }
        Sheet sheet = morning.sheet();
        return Gates.report(
                out(),
                "outstanding on " + day,
                List.of(
                        Map.entry("stops work today", described(sheet.stopsToday())),
                        Map.entry("due this week", described(sheet.stopsThisWeek())),
                        Map.entry("running late", described(sheet.runningLate()))),
                sheet.describe());
    }

    private static List<String> described(List<Line> lines) {
        return lines.stream().map(Line::describe).toList();
    }
}
