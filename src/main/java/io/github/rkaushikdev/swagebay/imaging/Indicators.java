package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The published image quality indicators, hole type and wire type.
 *
 * <p>An indicator is a deliberately crude object placed in the beam so that the image can be asked
 * a question it can answer honestly: can you see this. It says nothing about whether the weld is
 * good. It says whether the radiograph is good enough to be allowed to have an opinion about the
 * weld.
 *
 * <p>A hole type plaque and a wire are not measures of each other. A radiograph that shows a 2T
 * hole may not show the wire the E747 equivalence table says goes with it, which is why a client
 * specification naming one family cannot be satisfied by shooting the other.
 *
 * <p>Plaque identities are thousandths of an inch, because that is what is stamped on the lead.
 */
public final class Indicators {

    /** Every plaque thickness E1025 publishes, in thousandths of an inch, thinnest first. */
    static final List<Integer> HOLE_IDENTITIES =
            List.of(5, 7, 10, 12, 15, 17, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 100, 120, 140, 160, 200, 240, 280);

    /** The ISO 19232-1 wire numbers and their diameters. */
    static final Map<Integer, Length> WIRE_DIAMETERS = wireDiameters();

    private static final Map<String, List<Integer>> WIRE_SETS = Map.of(
            "A", List.of(1, 2, 3, 4, 5, 6, 7),
            "B", List.of(6, 7, 8, 9, 10, 11, 12),
            "C", List.of(10, 11, 12, 13, 14, 15, 16),
            "D", List.of(13, 14, 15, 16, 17, 18, 19));

    private Indicators() {}

    /** Every published plaque identity, thinnest first. */
    public static List<Integer> holeIdentities() {
        return HOLE_IDENTITIES;
    }

    /** Every wire number in the ISO series, coarsest wire first. */
    public static List<Integer> wireNumbers() {
        return WIRE_DIAMETERS.keySet().stream().sorted().toList();
    }

    /**
     * The wires of one stock set, named {@code A} to {@code D} in either case.
     *
     * @throws ConfigurationError for a set nobody makes
     */
    public static List<WireIndicator> wireSet(String name) {
        List<Integer> numbers = WIRE_SETS.get(name.toUpperCase(Locale.ROOT));
        if (numbers == null) {
            throw new ConfigurationError("no such wire set", Context.of("name", name));
        }
        return numbers.stream().map(WireIndicator::new).toList();
    }

    /** Which sets a crew could take to site and still have that wire, in alphabetical order. */
    public static List<String> wireSetsContaining(int number) {
        return WIRE_SETS.entrySet().stream()
                .filter(set -> set.getValue().contains(number))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * The next plaque up, for when the essential hole will not show. That is the next published
     * identity, not the next whole number.
     *
     * @throws ConfigurationError when nothing thicker is published
     */
    public static int nextThickerIdentity(int identity) {
        for (int candidate : HOLE_IDENTITIES) {
            if (candidate > identity) {
                return candidate;
            }
        }
        throw new ConfigurationError("nothing thicker is published", Context.of("identity", identity));
    }

    /** Refuses a penetrated thickness that is not positive, which no sensitivity can be taken against. */
    static void requirePenetrated(Length penetrated) {
        if (penetrated.um() <= 0) {
            throw new ValidationError("a penetrated thickness has to be positive");
        }
    }

    private static Map<Integer, Length> wireDiameters() {
        String[] millimetres = {
            "3.20", "2.50", "2.00", "1.60", "1.25", "1.00", "0.80", "0.63", "0.50", "0.40", "0.32", "0.25", "0.20",
            "0.16", "0.125", "0.10", "0.08", "0.063", "0.05"
        };
        Map<Integer, Length> diameters = new LinkedHashMap<>();
        for (int i = 0; i < millimetres.length; i++) {
            diameters.put(i + 1, Length.fromMm(millimetres[i]));
        }
        return Collections.unmodifiableMap(diameters);
    }
}
