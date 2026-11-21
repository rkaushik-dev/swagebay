package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.safety.Defect;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import io.github.rkaushikdev.swagebay.services.Clearance;
import io.github.rkaushikdev.swagebay.services.DispatchService;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Device commands, which are the ones run in the yard at seven in the morning.
 *
 * <p>The inspect command takes defects as {@code item:account} pairs so a whole check goes in as one
 * line. That matters more than it looks: a form that takes four commands to fill in gets filled in
 * after the truck leaves, and a pre use check recorded after the truck leaves is not a pre use check.
 *
 * <p>The clear command is the gate, run against a real device, source and day. It prints every
 * reason rather than the first, and it exits non zero when the load cannot go, so it can be the last
 * line of a dispatch script.
 */
@Command(
        name = "device",
        description = "Inspect devices and clear them to leave.",
        subcommands = {
            DeviceCommands.InspectCommand.class,
            DeviceCommands.HistoryCommand.class,
            DeviceCommands.ClearCommand.class
        })
final class DeviceCommands extends Group {

    /**
     * One {@code item:account} pair from the command line, as a defect.
     *
     * @throws ValidationError for an item nothing inspects, or a defect that does not say what is
     *     wrong
     */
    static Defect defect(String text) {
        int colon = text.indexOf(':');
        String item = colon < 0 ? text : text.substring(0, colon);
        String account = colon < 0 ? "" : Text.strip(text.substring(colon + 1));
        InspectionItem found = Naming.itemNamed(item);
        if (account.isEmpty()) {
            throw new ValidationError("a defect says what is wrong with it", Context.of("item", found.toString()));
        }
        return new Defect(found, account);
    }

    /** Record an inspection against a device. */
    @Command(name = "inspect", description = "Record an inspection against a device.")
    static final class InspectCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--quarterly", description = "the strip down, not the yard check")
        private boolean isQuarterly;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String badge;

        @Option(names = "--defect", paramLabel = "TEXT", description = "item:what is wrong with it")
        private List<String> defects = List.of();

        @Option(names = "--note", paramLabel = "TEXT")
        private String note = "";

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            List<Defect> raised = defects.stream().map(DeviceCommands::defect).toList();
            Inspection inspection = isQuarterly
                    ? Inspection.quarterly(serial, day, badge, raised, note)
                    : Inspection.preUse(serial, day, badge, raised, note);
            inWork(work -> work.maintenance().record(inspection));
            out().println(inspection.describe());
            if (!inspection.blockingDefects().isEmpty()) {
                out().println(Render.heading("device is out of service"));
                out().println(Render.bullets(described(inspection.blockingDefects())));
            }
            if (!inspection.notedDefects().isEmpty()) {
                out().println(Render.heading("noted"));
                out().println(Render.bullets(described(inspection.notedDefects())));
            }
            return Main.EXIT_OK;
        }

        private static List<String> described(List<Defect> defects) {
            return defects.stream().map(Defect::describe).toList();
        }
    }

    /** Every inspection on one device, oldest first. */
    @Command(name = "history", description = "Every inspection on one device, oldest first.")
    static final class HistoryCommand extends Leaf {

        /** What the register holds on one device, and when its next quarterly falls due. */
        private record Held(List<Inspection> found, Optional<LocalDate> due) {}

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Override
        public Integer call() {
            Held held = fromWork(work -> new Held(
                    work.maintenance().forDevice(serial),
                    work.maintenance().registerFor(List.of(serial)).quarterlyDueBy(serial)));
            if (held.found().isEmpty()) {
                out().println("nothing on record for " + serial);
                return Main.EXIT_OK;
            }
            List<List<String>> rows = held.found().stream()
                    .map(inspection -> List.of(
                            inspection.performedOn().toString(),
                            inspection.isQuarterly() ? "quarterly" : "pre use",
                            inspection.performedBy(),
                            inspection.passed() ? "passed" : "failed",
                            defectsOf(inspection)))
                    .toList();
            out().println(Render.table(List.of("when", "kind", "by", "verdict", "defects"), rows));
            held.due().ifPresent(due -> out().println("next quarterly due by " + due));
            return Main.EXIT_OK;
        }

        private static String defectsOf(Inspection inspection) {
            String listed = inspection.defects().stream().map(Defect::describe).collect(Collectors.joining("; "));
            return listed.isEmpty() ? "-" : listed;
        }
    }

    /** Ask the gate whether this load can leave the shop today. */
    @Command(name = "clear", description = "Ask the gate whether this load can leave the shop today.")
    static final class ClearCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--source", paramLabel = "TEXT", required = true)
        private String sourceSerial;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--crew", paramLabel = "TEXT")
        private String leadBadge = "";

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            String lead = leadBadge.isEmpty() ? "yard" : leadBadge;
            Clearance found =
                    fromWork(work -> DispatchService.clearance(work, serial, sourceSerial, day, List.of(), lead));
            return Gates.report(
                    out(),
                    serial + " with " + sourceSerial + " is not clear for " + day,
                    List.of(
                            Map.entry("source", found.sourceReasons()),
                            Map.entry("device", found.deviceReasons()),
                            Map.entry("crew", found.crewReasons())),
                    found.describe());
        }
    }
}
