package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.exposure.Catalogue;
import io.github.rkaushikdev.swagebay.exposure.ExposureChart;
import io.github.rkaushikdev.swagebay.imaging.AstmWire;
import io.github.rkaushikdev.swagebay.imaging.FilmSystem;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.Indicators;
import io.github.rkaushikdev.swagebay.imaging.WireIndicator;
import io.github.rkaushikdev.swagebay.materials.Material;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.DeviceModel;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;

/**
 * Catalogue commands, which answer the questions a shooter asks a wall chart.
 *
 * <p>Every one of these is a lookup somebody currently does off a laminated card in the truck, and
 * the cards disagree with each other because they were printed in different decades. Printing them
 * from the same tables the planner reads means the card and the plan cannot drift apart.
 *
 * <p>The isotope command is the one that gets used. It answers what will shoot this wall, which is
 * the first question of every job, and it answers it softest first because the softest isotope that
 * covers a wall gives the best contrast.
 */
@Command(
        name = "catalogue",
        description = "The tables the planner reads, printed.",
        subcommands = {
            CatalogueCommands.IsotopesCommand.class,
            CatalogueCommands.FilmsCommand.class,
            CatalogueCommands.MaterialsCommand.class,
            CatalogueCommands.IndicatorsCommand.class,
            CatalogueCommands.ChartsCommand.class,
            CatalogueCommands.DevicesCommand.class
        })
final class CatalogueCommands extends Group {

    /** List the isotopes, or the ones that cover a wall, softest first. */
    @Command(name = "isotopes", description = "List the isotopes, or the ones that cover a wall, softest first.")
    static final class IsotopesCommand extends Leaf {

        @Option(names = "--covering", paramLabel = "TEXT", description = "steel thickness, for example 20 mm")
        private String covering;

        @Override
        public Integer call() {
            List<Isotope> found = covering != null
                    ? Isotopes.isotopesCovering(Length.parse(covering))
                    : Isotopes.knownIsotopes().stream().map(Isotopes::isotope).toList();
            List<List<String>> rows = found.stream()
                    .map(source -> List.of(
                            source.symbol(),
                            source.halfLifeDays().toString(),
                            source.gammaConstant().toString(),
                            source.steelMin().formatMm(0),
                            source.steelMax().formatMm(0)))
                    .toList();
            out().println(Render.table(
                    List.of("isotope", "half life d", "R/h at 1 m", "from", "to"), rows, List.of(1, 2, 3, 4)));
            return Main.EXIT_OK;
        }
    }

    /** List the film systems and their speeds. */
    @Command(name = "films", description = "List the film systems and their speeds.")
    static final class FilmsCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            for (String key : Films.knownFilms()) {
                FilmSystem film = Films.film(key);
                rows.add(List.of(
                        key,
                        film.name(),
                        film.filmClass().toString(),
                        Render.plain(film.speedIndex()),
                        Render.plain(film.granularity())));
            }
            out().println(Render.table(List.of("key", "film", "class", "speed", "grain"), rows, List.of(3, 4)));
            return Main.EXIT_OK;
        }
    }

    /** List the materials and their equivalence factor at one beam quality. */
    @Command(name = "materials", description = "List the materials and their equivalence factor at one beam quality.")
    static final class MaterialsCommand extends Leaf {

        @Option(names = "--quality", paramLabel = "TEXT", defaultValue = "IR192", showDefaultValue = Visibility.ALWAYS)
        private String quality;

        @Override
        public Integer call() {
            Quality wanted = Quality.valueOf(quality.toUpperCase(Locale.ROOT));
            List<List<String>> rows = new ArrayList<>();
            for (String key : Materials.knownMaterials()) {
                Material entry = Materials.material(key);
                String factor = Optional.ofNullable(entry.factors().get(wanted))
                        .map(Render::plain)
                        .orElse("not published");
                rows.add(List.of(key, entry.name(), String.valueOf(entry.densityKgM3()), factor));
            }
            out().println(Render.table(List.of("key", "material", "kg/m3", "factor at " + wanted), rows, List.of(2)));
            return Main.EXIT_OK;
        }
    }

    /** List the hole plaque identities and the wire diameters. */
    @Command(name = "indicators", description = "List the hole plaque identities and the wire diameters.")
    static final class IndicatorsCommand extends Leaf {

        @Option(
                names = "--astm",
                description = "list the ASTM E747 wires and their sets instead of the ISO 19232 series")
        private boolean astm;

        @Override
        public Integer call() {
            String holes =
                    Indicators.holeIdentities().stream().map(String::valueOf).collect(Collectors.joining(", "));
            out().println("hole identities  " + holes);
            out().println();
            if (astm) {
                List<List<String>> rows = IntStream.rangeClosed(AstmWire.FIRST, AstmWire.LAST)
                        .mapToObj(AstmWire::new)
                        .map(wire -> List.of(
                                String.valueOf(wire.number()),
                                wire.diameter().formatInch(4),
                                String.join("/", wire.sets())))
                        .toList();
                out().println(Render.table(List.of("wire", "diameter", "sets"), rows, List.of(0, 1)));
                return Main.EXIT_OK;
            }
            List<List<String>> rows = Indicators.wireNumbers().stream()
                    .map(WireIndicator::new)
                    .map(wire -> List.of(wire.isoLabel(), wire.diameter().formatMm(3)))
                    .toList();
            out().println(Render.table(List.of("wire", "diameter"), rows, List.of(1)));
            return Main.EXIT_OK;
        }
    }

    /** List the exposure devices, one line for every isotope on each certificate. */
    @Command(name = "devices", description = "List the exposure devices and what each is certified to hold.")
    static final class DevicesCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            for (String key : Devices.knownDevices()) {
                DeviceModel model = Devices.deviceModel(key);
                for (DeviceModel.Certification line : model.certifications()) {
                    rows.add(List.of(
                            key,
                            model.name(),
                            Isotopes.isotope(line.isotopeKey()).symbol(),
                            line.capacity().formatCurie(),
                            Render.plain(model.massKg()),
                            model.maxGuideTube().formatM(0),
                            model.isoCategory()));
                }
            }
            out().println(Render.table(
                    List.of("key", "device", "isotope", "capacity", "kg", "guide tube", "category"),
                    rows,
                    List.of(3, 4, 5)));
            return Main.EXIT_OK;
        }
    }

    /** List the exposure charts the shop has established. */
    @Command(name = "charts", description = "List the exposure charts the shop has established.")
    static final class ChartsCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            for (ExposureChart chart : Catalogue.allCharts()) {
                rows.add(List.of(
                        chart.key(),
                        chart.isotopeKey(),
                        chart.materialKey(),
                        chart.filmKey(),
                        Render.plain(chart.density()),
                        chart.referenceSfd().formatInch(0),
                        chart.thinnest().formatMm(1),
                        chart.thickest().formatMm(1)));
            }
            out().println(Render.table(
                    List.of("chart", "isotope", "material", "film", "density", "at", "from", "to"),
                    rows,
                    List.of(4, 5, 6, 7)));
            return Main.EXIT_OK;
        }
    }
}
