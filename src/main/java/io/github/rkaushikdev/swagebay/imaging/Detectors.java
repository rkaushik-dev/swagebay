package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The detector panels the shop owns, and the limits a panel is held to.
 *
 * <p>A panel can be sharp in one corner and soft in another, can remember the last exposure for
 * several frames, can saturate without saying so, and has a population of pixels that never
 * worked. Lag matters on a panel shooting a series of welds quickly; burn in scraps a panel
 * whatever it is used for.
 */
public final class Detectors {

    /** The most of the previous frame a panel may still show. */
    public static final BigDecimal MAX_ACCEPTABLE_LAG = new BigDecimal("0.05");

    /** The most permanent ghost a panel may carry before it is scrap. */
    public static final BigDecimal MAX_ACCEPTABLE_BURN_IN = new BigDecimal("0.01");

    private static final Map<String, DetectorArray> PANELS = new LinkedHashMap<>();

    static {
        add(new DetectorArray(
                "dxr250",
                "DXR250C",
                "Waygate",
                Length.fromMm("0.200"),
                Length.fromMm("0.250"),
                250,
                new BigDecimal("0.72"),
                new BigDecimal("0.02"),
                new BigDecimal("0.002"),
                1024,
                1024));
        add(new DetectorArray(
                "xrd1611",
                "XRD 1611",
                "Perkin",
                Length.fromMm("0.100"),
                Length.fromMm("0.160"),
                200,
                new BigDecimal("0.64"),
                new BigDecimal("0.03"),
                new BigDecimal("0.004"),
                4096,
                4096));
        add(new DetectorArray(
                "flex7",
                "Flex 7",
                "Waygate",
                Length.fromMm("0.127"),
                Length.fromMm("0.200"),
                180,
                new BigDecimal("0.58"),
                new BigDecimal("0.08"),
                new BigDecimal("0.003"),
                2048,
                1536));
    }

    private Detectors() {}

    /**
     * Look a panel up by its key.
     *
     * @throws ConfigurationError for a panel the shop does not own
     */
    public static DetectorArray detector(String key) {
        DetectorArray found = PANELS.get(key);
        if (found == null) {
            throw new ConfigurationError("no such detector array", Context.of("key", key));
        }
        return found;
    }

    /** Every panel key, sorted. */
    public static List<String> knownDetectors() {
        return PANELS.keySet().stream().sorted().toList();
    }

    /** Every panel that clears the specification and its own limits, coarsest first. */
    public static List<DetectorArray> detectorsMeeting(Length requiredResolution, int requiredSnr) {
        return PANELS.values().stream()
                .filter(d -> d.shortfalls(requiredResolution, requiredSnr).isEmpty())
                .sorted(Comparator.comparing(DetectorArray::basicSpatialResolution)
                        .reversed())
                .toList();
    }

    private static void add(DetectorArray panel) {
        PANELS.put(panel.key(), panel);
    }
}
