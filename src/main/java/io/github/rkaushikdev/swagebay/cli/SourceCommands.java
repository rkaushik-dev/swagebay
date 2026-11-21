package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.services.Booking;
import io.github.rkaushikdev.swagebay.services.Fitness;
import io.github.rkaushikdev.swagebay.services.HistoryEntry;
import io.github.rkaushikdev.swagebay.services.SourceService;
import io.github.rkaushikdev.swagebay.services.WipeResult;
import io.github.rkaushikdev.swagebay.services.WipeService;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Source commands, which is what the store issues off in the morning.
 *
 * <p>The fitness command defaults to now, which contradicts what the API does on purpose. A
 * coordinator using the API is planning. A storeman at the counter with a crew waiting is asking
 * about right now, and making them type a timestamp to find out whether the pig can go in the truck
 * would mean nobody runs it.
 *
 * <p>The history command prints forwards and does not paginate. A source history is tens of lines,
 * not thousands, and a regulator reading one wants the whole thing on one page.
 */
@Command(
        name = "source",
        description = "Register sources, book them out and answer for them.",
        subcommands = {
            SourceCommands.RegisterCommand.class,
            SourceCommands.AddDeviceCommand.class,
            SourceCommands.ListCommand.class,
            SourceCommands.FitnessCommand.class,
            SourceCommands.SendOutCommand.class,
            SourceCommands.BringBackCommand.class,
            SourceCommands.WipeCommand.class,
            SourceCommands.OverdueCommand.class,
            SourceCommands.HistoryCommand.class,
            SourceCommands.RetireCommand.class
        })
final class SourceCommands extends Group {

    /** Put a source on the inventory. */
    @Command(name = "register", description = "Put a source on the inventory.")
    static final class RegisterCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--isotope", paramLabel = "TEXT", required = true)
        private String isotopeKey;

        @Option(names = "--curies", paramLabel = "TEXT", required = true)
        private String curies;

        @Option(
                names = "--assayed",
                paramLabel = Dates.MOMENT_LABEL,
                required = true,
                converter = Dates.Timestamp.class)
        private LocalDateTime assayed;

        @Option(
                names = "--manufacturer",
                paramLabel = "TEXT",
                defaultValue = "QSA",
                showDefaultValue = Visibility.ALWAYS)
        private String manufacturer;

        @Option(names = "--model", paramLabel = "TEXT", defaultValue = "A424-9", showDefaultValue = Visibility.ALWAYS)
        private String model;

        @Option(
                names = "--pellet-diameter",
                paramLabel = "TEXT",
                defaultValue = "3 mm",
                showDefaultValue = Visibility.ALWAYS)
        private String pelletDiameter;

        @Option(
                names = "--pellet-length",
                paramLabel = "TEXT",
                defaultValue = "3 mm",
                showDefaultValue = Visibility.ALWAYS)
        private String pelletLength;

        @Option(
                names = "--floor",
                paramLabel = "TEXT",
                defaultValue = "15",
                showDefaultValue = Visibility.ALWAYS,
                description = "working floor in curies")
        private String floor;

        @Override
        public Integer call() {
            Isotope isotope = Isotopes.isotope(isotopeKey);
            Activity assay = Activity.fromCurie(Decimals.of(curies));
            Length diameter = Length.parse(pelletDiameter);
            Length length = Length.parse(pelletLength);
            SourceCertificate certificate =
                    new SourceCertificate(serial, isotope, assay, assayed, manufacturer, model, diameter, length);
            SealedSource source = new SealedSource(certificate, Activity.fromCurie(Decimals.of(floor)));
            inWork(work -> SourceService.registerSource(work, source));
            out().println("registered " + serial);
            return Main.EXIT_OK;
        }
    }

    /** Put an exposure device on the inventory. */
    @Command(name = "add-device", description = "Put an exposure device on the inventory.")
    static final class AddDeviceCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "SERIAL")
        private String serial;

        @Parameters(index = "1", paramLabel = "MODEL_KEY")
        private String modelKey;

        @Override
        public Integer call() {
            inWork(work -> SourceService.registerDevice(work, serial, modelKey));
            out().println("registered device " + serial);
            return Main.EXIT_OK;
        }
    }

    /** List the sources in service, with where each one is. */
    @Command(name = "list", description = "List the sources in service, with where each one is.")
    static final class ListCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = fromWork(work -> {
                List<List<String>> found = new ArrayList<>();
                for (SourceRow row : work.sources().inService()) {
                    found.add(List.of(
                            row.getSerial(),
                            row.getIsotopeKey(),
                            row.getAssayActivity().formatCurie(),
                            row.getAssayAt().toLocalDate().toString(),
                            SourceService.location(work, row.getSerial())));
                }
                return found;
            });
            out().println(Render.table(List.of("serial", "isotope", "assay", "assayed", "where"), rows, List.of(2)));
            return Main.EXIT_OK;
        }
    }

    /** Say whether a source can go out, now or at a stated moment. */
    @Command(name = "fitness", description = "Say whether a source can go out, now or at a stated moment.")
    static final class FitnessCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--at", paramLabel = Dates.MOMENT_LABEL, converter = Dates.Timestamp.class)
        private Optional<LocalDateTime> at;

        @Override
        public Integer call() {
            LocalDateTime moment = at.orElseGet(LocalDateTime::now);
            Fitness report = fromWork(work -> SourceService.fitness(work, serial, moment));
            out().println(serial + " fit: " + Render.yesNo(report.fit()));
            for (String reason : report.reasons()) {
                out().println("  " + reason);
            }
            return Main.EXIT_OK;
        }
    }

    /** Book a source out to a job. */
    @Command(name = "send-out", description = "Book a source out to a job.")
    static final class SendOutCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--device", paramLabel = "TEXT", required = true)
        private String device;

        @Option(names = "--crew", paramLabel = "TEXT", required = true)
        private String crew;

        @Option(names = "--site", paramLabel = "TEXT", required = true)
        private String site;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Override
        public Integer call() {
            inWork(work -> SourceService.sendOut(work, new Booking(serial, device, crew, site, on.toLocalDate())));
            out().println(serial + " out to " + site);
            return Main.EXIT_OK;
        }
    }

    /** Book a source back in. */
    @Command(name = "bring-back", description = "Book a source back in.")
    static final class BringBackCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Option(names = "--exposures", paramLabel = "INTEGER", defaultValue = "0", showDefaultValue = Visibility.ALWAYS)
        private int exposures;

        @Override
        public Integer call() {
            inWork(work -> SourceService.bringBack(work, serial, on.toLocalDate(), exposures));
            out().println(serial + " back in the shop");
            return Main.EXIT_OK;
        }
    }

    /** Record a leak test. */
    @Command(name = "wipe", description = "Record a leak test.")
    static final class WipeCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Option(names = "--at", paramLabel = Dates.MOMENT_LABEL, required = true, converter = Dates.Timestamp.class)
        private LocalDateTime at;

        @Option(
                names = "--removed",
                paramLabel = "TEXT",
                defaultValue = "0",
                showDefaultValue = Visibility.ALWAYS,
                description = "becquerels removed")
        private String removed;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String technician;

        @Override
        public Integer call() {
            WipeResult result =
                    fromWork(work -> WipeService.recordWipe(work, serial, at, Decimals.of(removed), technician));
            out().println(result.describe());
            return Main.EXIT_OK;
        }
    }

    /** List the sources whose leak test has run out. */
    @Command(name = "overdue", description = "List the sources whose leak test has run out.")
    static final class OverdueCommand extends Leaf {

        @Option(names = "--on", paramLabel = Dates.DAY_LABEL, required = true, converter = Dates.Day.class)
        private LocalDateTime on;

        @Override
        public Integer call() {
            List<String> serials = fromWork(work -> WipeService.wipesOverdue(work, on.toLocalDate()));
            out().println(Render.table(
                    List.of("serial"), serials.stream().map(List::of).toList()));
            return Main.EXIT_OK;
        }
    }

    /** Print a source history sheet, oldest first. */
    @Command(name = "history", description = "Print a source history sheet, oldest first.")
    static final class HistoryCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Override
        public Integer call() {
            List<HistoryEntry> entries = fromWork(work -> WipeService.history(work, serial));
            List<List<String>> rows = entries.stream()
                    .map(entry -> List.of(entry.at().toLocalDate().toString(), entry.what(), entry.detail()))
                    .toList();
            out().println(Render.table(List.of("date", "what", "detail"), rows));
            return Main.EXIT_OK;
        }
    }

    /** Take a source out of service. */
    @Command(name = "retire", description = "Take a source out of service.")
    static final class RetireCommand extends Leaf {

        @Parameters(paramLabel = "SERIAL")
        private String serial;

        @Override
        public Integer call() {
            inWork(work -> SourceService.retire(work, serial));
            out().println(serial + " awaiting disposal");
            return Main.EXIT_OK;
        }
    }
}
