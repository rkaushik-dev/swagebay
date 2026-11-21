package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The phosphor plates the shop stocks for computed radiography, and the clock on scanning them.
 *
 * <p>A plate scanned twenty minutes after the shot and the same plate scanned the next morning are
 * not the same measurement, and a crew that shoots a night's work and scans it after breakfast has
 * thrown away signal it paid for in exposure time. A scan inside {@link #SCAN_WITHOUT_CORRECTION}
 * needs no correction; one past {@link #SCAN_DEADLINE} is not defensible at all.
 */
public final class Plates {

    /** Inside this the fade is small enough that nobody bothers correcting. */
    public static final Duration SCAN_WITHOUT_CORRECTION = Duration.fromMinutes(30);

    /** Past this the plate is scrapped and the shot is repeated. */
    public static final Duration SCAN_DEADLINE = Duration.fromHours(8);

    private static final Map<String, PlateSystem> PLATES = new LinkedHashMap<>();

    static {
        add(new PlateSystem("ip1_hd", "HD-IP", "Durr", "IP1", Length.fromMm("0.040"), 130, Duration.fromHours(20)));
        add(new PlateSystem("ip2_std", "ST-VI", "Durr", "IP2", Length.fromMm("0.060"), 150, Duration.fromHours(24)));
        add(new PlateSystem(
                "ip3_gp", "GP-D", "Carestream", "IP3", Length.fromMm("0.080"), 175, Duration.fromHours(26)));
        add(new PlateSystem(
                "ip5_fast", "XL Blue", "Carestream", "IP5", Length.fromMm("0.130"), 210, Duration.fromHours(30)));
    }

    private Plates() {}

    /**
     * Look a plate system up by its key.
     *
     * @throws ConfigurationError for a plate the shop does not stock
     */
    public static PlateSystem plate(String key) {
        PlateSystem found = PLATES.get(key);
        if (found == null) {
            throw new ConfigurationError("no such plate system", Context.of("key", key));
        }
        return found;
    }

    /** Every plate key, sorted. */
    public static List<String> knownPlates() {
        return PLATES.keySet().stream().sorted().toList();
    }

    /** Every plate in stock that clears both halves of the specification, coarsest first. */
    public static List<PlateSystem> platesMeeting(Length requiredResolution, int requiredSnr) {
        return PLATES.values().stream()
                .filter(p -> p.shortfalls(requiredResolution, requiredSnr).isEmpty())
                .sorted(Comparator.comparing(PlateSystem::basicSpatialResolution)
                        .reversed())
                .toList();
    }

    private static void add(PlateSystem plate) {
        PLATES.put(plate.key(), plate);
    }
}
