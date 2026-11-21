package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.safety.DeviceDay;
import io.github.rkaushikdev.swagebay.safety.Use;
import io.github.rkaushikdev.swagebay.services.ExchangeService;
import io.github.rkaushikdev.swagebay.sources.Exchange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Shop commands: swapping a source and logging a day's use.
 *
 * <p>Exchange goes through the service rather than the repository, so the same rules that refuse a
 * load in the domain refuse it here. There is no way to write an exchange row from this command
 * line that the domain would not accept, and that is the point of having it.
 *
 * <p>The gaps command is the one worth running weekly. It compares the trips against the
 * utilisation log and prints the days a device was out with nothing written down, and it exits non
 * zero, because a gap is the one thing on this register that gets harder to fix the longer it is
 * left.
 */
@Command(
        name = "shop",
        description = "Source exchanges and the utilisation log.",
        subcommands = {
            ShopCommands.ExchangeCommand.class,
            ShopCommands.LoadingCommand.class,
            ShopCommands.UseCommand.class,
            ShopCommands.ExposuresCommand.class,
            ShopCommands.GapsCommand.class
        })
final class ShopCommands extends Group {

    /** Swap what is in a device, with the rules running. */
    @Command(name = "exchange", description = "Swap what is in a device, with the rules running.")
    static final class ExchangeCommand extends Leaf {

        @Parameters(paramLabel = "DEVICE")
        private String device;

        @Option(names = "--out", paramLabel = "TEXT", description = "the source coming out")
        private Optional<String> removed;

        @Option(names = "--in", paramLabel = "TEXT", description = "the source going in")
        private Optional<String> installed;

        @Option(names = "--shipment", paramLabel = "TEXT", required = true)
        private String shipment;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String badge;

        @Option(names = "--note", paramLabel = "TEXT")
        private String note = "";

        @Override
        public Integer call() {
            Exchange exchange = new Exchange(device, removed, installed, Gates.asDay(on), badge, shipment, note);
            inWork(work -> ExchangeService.recordExchange(work, exchange));
            out().println(exchange.describe());
            return Main.EXIT_OK;
        }
    }

    /** Which source is in which device, replayed from the exchanges. */
    @Command(name = "loading", description = "Which source is in which device, replayed from the exchanges.")
    static final class LoadingCommand extends Leaf {

        @Override
        public Integer call() {
            Map<String, String> held =
                    fromWork(work -> new TreeMap<>(work.shop().loading().held()));
            if (held.isEmpty()) {
                out().println("no device is holding a source");
                return Main.EXIT_OK;
            }
            List<List<String>> rows = held.entrySet().stream()
                    .map(entry -> List.of(entry.getKey(), entry.getValue()))
                    .toList();
            out().println(Render.table(List.of("device", "source"), rows));
            return Main.EXIT_OK;
        }
    }

    /** Log a day's use of one device. */
    @Command(name = "use", description = "Log a day's use of one device.")
    static final class UseCommand extends Leaf {

        @Parameters(paramLabel = "DEVICE")
        private String device;

        @Option(names = "--source", paramLabel = "TEXT", required = true)
        private String sourceSerial;

        @Option(names = "--site", paramLabel = "TEXT", required = true)
        private String site;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--by", paramLabel = "TEXT", required = true, description = "one per radiographer")
        private List<String> badges;

        @Option(
                names = "--exposures",
                paramLabel = "INTEGER RANGE",
                defaultValue = "0",
                showDefaultValue = Visibility.ALWAYS,
                converter = Counts.NotNegative.class)
        private int exposures;

        @Override
        public Integer call() {
            Use use = new Use(Gates.asDay(on), device, sourceSerial, site, badges, exposures);
            inWork(work -> ExchangeService.recordUse(work, use));
            out().println(use.describe());
            return Main.EXIT_OK;
        }
    }

    /** Top up a count written before the films were counted. */
    @Command(name = "exposures", description = "Top up a count written before the films were counted.")
    static final class ExposuresCommand extends Leaf {

        @Parameters(paramLabel = "DEVICE")
        private String device;

        @Option(names = "--site", paramLabel = "TEXT", required = true)
        private String site;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> on;

        @Option(names = "--add", paramLabel = "INTEGER RANGE", required = true, converter = Counts.NotNegative.class)
        private int extra;

        @Override
        public Integer call() {
            LocalDate day = Gates.asDay(on);
            Use use = fromWork(work -> work.shop().addExposures(device, day, site, extra));
            out().println(use.describe());
            return Main.EXIT_OK;
        }
    }

    /** Days a device was out with nothing in the utilisation log. */
    @Command(name = "gaps", description = "Days a device was out with nothing in the utilisation log.")
    static final class GapsCommand extends Leaf {

        @Option(names = "--since", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime since;

        @Option(names = "--until", paramLabel = Dates.DAY_LABEL, converter = Dates.Day.class)
        private Optional<LocalDateTime> until;

        @Override
        public Integer call() {
            LocalDate start = since.toLocalDate();
            LocalDate end = Gates.asDay(until);
            if (end.isBefore(start)) {
                throw new ValidationError("that window ends before it starts", Context.of("since", start.toString()));
            }
            List<DeviceDay> missing = fromWork(work -> ExchangeService.unloggedDays(work, start, end));
            return Gates.report(
                    out(),
                    "utilisation gaps from " + start + " to " + end,
                    List.of(Map.entry(
                            "",
                            missing.stream()
                                    .map(gap -> gap.deviceSerial() + " on " + gap.day())
                                    .toList())),
                    "nothing missing between " + start + " and " + end);
        }
    }
}
