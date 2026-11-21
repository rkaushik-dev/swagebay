package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shop's viewers, and the viewing conditions that decide whether a good radiograph can be read
 * at all.
 *
 * <p>A film with a density of 3.5 is a good film. It is also almost opaque, and getting enough light
 * through it takes a viewer most shops do not own. So the density a technique aims for and the
 * viewer the film will be read on are the same decision.
 *
 * <p>The ambient light in the room is the other half and it is the half nobody measures. A bright
 * room washes out the contrast the density was bought to produce, so the room has to be dark enough
 * that the film front reflects less than a hundredth of what comes through it.
 */
public final class Viewers {

    /** Candela a square metre through the film, below which nobody reads anything. */
    public static final BigDecimal MINIMUM_TRANSMITTED = BigDecimal.valueOf(30);

    /** How much of the transmitted light the room may add by reflection. */
    public static final BigDecimal MAXIMUM_AMBIENT_FRACTION = new BigDecimal("0.01");

    private static final BigDecimal THIN_FILM = new BigDecimal("2.0");

    private static final Map<String, Viewer> VIEWERS = new LinkedHashMap<>();

    static {
        add(new Viewer("hi300", "HiLight 300", 30_000, true));
        add(new Viewer("hi100", "HiLight 100", 10_000, true));
        add(new Viewer("bench", "bench box", 3_000, false));
        add(new Viewer("field", "field viewer", 1_000, false));
    }

    private Viewers() {}

    /**
     * Look a viewer up by its key.
     *
     * @throws ConfigurationError for a viewer the shop does not have
     */
    public static Viewer viewer(String key) {
        Viewer found = VIEWERS.get(key);
        if (found == null) {
            throw new ConfigurationError("no such viewer", Context.of("key", key));
        }
        return found;
    }

    /** Every viewer key, sorted. */
    public static List<String> knownViewers() {
        return VIEWERS.keySet().stream().sorted().toList();
    }

    /** Every viewer in the shop that can read a film that dense, dimmest first. */
    public static List<Viewer> viewersSupporting(BigDecimal density) {
        return VIEWERS.values().stream()
                .filter(v -> v.supports(density))
                .sorted(Comparator.comparingInt(Viewer::maximumLuminance))
                .toList();
    }

    /** How bright the room may be for a film of that density to be readable on that viewer. */
    public static BigDecimal maximumAmbient(Viewer viewer, BigDecimal density) {
        return Decimals.multiply(viewer.transmittedAt(density), MAXIMUM_AMBIENT_FRACTION);
    }

    /**
     * Everything wrong with reading that film on that viewer in that room: a film too dense for the
     * viewer, a room too bright for the film, and a thin film washed out on an unmasked viewer.
     */
    public static List<String> faults(Viewer viewer, BigDecimal density, BigDecimal ambient) {
        List<String> out = new ArrayList<>();
        if (!viewer.supports(density)) {
            out.add("density " + density + " is over the "
                    + Decimals.halfEven(viewer.supportedDensity(), 2).toPlainString() + " this viewer supports");
        }
        BigDecimal allowed = maximumAmbient(viewer, density);
        if (ambient.compareTo(allowed) > 0) {
            out.add("ambient light " + ambient + " is over the "
                    + Decimals.halfEven(allowed, 3).toPlainString() + " allowed");
        }
        if (!viewer.masked() && density.compareTo(THIN_FILM) < 0) {
            out.add("a thin film on an unmasked viewer is washed out by the surround");
        }
        return List.copyOf(out);
    }

    private static void add(Viewer viewer) {
        VIEWERS.put(viewer.key(), viewer);
    }
}
