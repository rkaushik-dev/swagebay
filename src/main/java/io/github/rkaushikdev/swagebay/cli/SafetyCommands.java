package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.safety.Barrier;
import io.github.rkaushikdev.swagebay.safety.BoundaryKind;
import io.github.rkaushikdev.swagebay.safety.RadiationField;
import io.github.rkaushikdev.swagebay.safety.RopeOff;
import io.github.rkaushikdev.swagebay.safety.Shielding;
import io.github.rkaushikdev.swagebay.sources.Collimator;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;

/**
 * Safety commands, which are the ones run standing next to the truck.
 *
 * <p>The boundary command takes the load and what is fitted as arguments rather than reading the
 * inventory, for the same reason the API does: the inventory does not know whether the collimator
 * went on. It answers the question as asked and the crew is responsible for asking it correctly,
 * which is the honest division.
 *
 * <p>The output is two distances and a sentence, because that is what gets read out over a radio.
 * Anything longer gets shortened by the person reading it and the part they drop is the part that
 * mattered.
 */
@Command(
        name = "safety",
        description = "Boundaries, shielding and the rope.",
        subcommands = {
            SafetyCommands.BoundaryCommand.class,
            SafetyCommands.ShieldingCommand.class,
            SafetyCommands.CollimatorsCommand.class
        })
final class SafetyCommands extends Group {

    private static final BigDecimal DOWN = BigDecimal.ZERO;
    private static final BigDecimal SIDE = BigDecimal.valueOf(180);

    /** Print where the rope goes, down the beam and to the sides. */
    @Command(name = "boundary", description = "Print where the rope goes, down the beam and to the sides.")
    static final class BoundaryCommand extends Leaf {

        @Option(names = "--isotope", paramLabel = "TEXT", defaultValue = "ir192", showDefaultValue = Visibility.ALWAYS)
        private String isotopeKey;

        @Option(names = "--curies", paramLabel = "TEXT", required = true)
        private String curies;

        @Option(names = "--collimator", paramLabel = "TEXT", description = "leave out for an open beam")
        private String cup;

        @Option(names = "--barrier", paramLabel = "TEXT", description = "for example lead")
        private String barrier;

        @Option(names = "--thickness", paramLabel = "TEXT", description = "for example 30 mm")
        private String thickness;

        @Override
        public Integer call() {
            List<Barrier> barriers = List.of();
            if (barrier != null) {
                if (thickness == null) {
                    throw misused("a barrier needs a thickness");
                }
                barriers = List.of(new Barrier(barrier, Length.parse(thickness)));
            }
            Isotope isotope = Isotopes.isotope(isotopeKey);
            Activity activity = Activity.fromCurie(Decimals.of(curies));
            Optional<Collimator> collimator = Optional.ofNullable(cup).map(Devices::collimator);
            RadiationField field = new RadiationField(isotope, activity, collimator, barriers);
            List<List<String>> rows = new ArrayList<>();
            for (BoundaryKind kind : BoundaryKind.values()) {
                rows.add(List.of(
                        kind.value(),
                        kind.rate().formatMrPerHour(),
                        field.boundary(kind, DOWN).formatM(),
                        field.boundary(kind, SIDE).formatM()));
            }
            out().println(Render.table(List.of("posting", "rate", "down beam", "off beam"), rows, List.of(1, 2, 3)));
            out().println();
            out().println(RopeOff.of(field).describe());
            return Main.EXIT_OK;
        }
    }

    /** Print the half and tenth value layers for one isotope and material. */
    @Command(name = "shielding", description = "Print the half and tenth value layers for one isotope and material.")
    static final class ShieldingCommand extends Leaf {

        @Option(names = "--isotope", paramLabel = "TEXT", defaultValue = "ir192", showDefaultValue = Visibility.ALWAYS)
        private String isotopeKey;

        @Option(names = "--material", paramLabel = "TEXT", defaultValue = "lead", showDefaultValue = Visibility.ALWAYS)
        private String material;

        @Override
        public Integer call() {
            Isotope source = Isotopes.isotope(isotopeKey);
            Length half = Shielding.halfValueLayer(source, material);
            Length tenth = Shielding.tenthValueLayer(source, material);
            List<List<String>> rows = List.of(
                    List.of("half value layer", half.formatMm(2)),
                    List.of("tenth value layer", tenth.formatMm(2)),
                    List.of("ten half layers", half.times(10).formatMm(1)));
            out().println(Render.table(List.of("layer", material), rows, List.of(1)));
            return Main.EXIT_OK;
        }
    }

    /** List the collimators and what each one cuts the side field to. */
    @Command(name = "collimators", description = "List the collimators and what each one cuts the side field to.")
    static final class CollimatorsCommand extends Leaf {

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            for (String key : Devices.knownCollimators()) {
                Collimator cup = Devices.collimator(key);
                rows.add(List.of(
                        key,
                        cup.material(),
                        String.valueOf(cup.apertureDegrees()),
                        Render.plain(cup.transmission()),
                        Render.plain(cup.massKg())));
            }
            out().println(Render.table(
                    List.of("cup", "material", "aperture", "transmission", "kg"), rows, List.of(2, 3, 4)));
            return Main.EXIT_OK;
        }
    }
}
