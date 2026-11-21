package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The x-ray sets the shop owns, and the arithmetic of the quantity knob.
 *
 * <p>Double the current or double the time and the exposure doubles, which makes tube arithmetic
 * much friendlier than isotope arithmetic, where the source is also decaying.
 */
public final class Tubes {

    private static final Map<String, Tube> TUBES = new LinkedHashMap<>();

    static {
        add("eresco42", "ERESCO 42 MF4", "Waygate", 10, 200, "10", "1.0", "0.5");
        add("eresco65", "ERESCO 65 MF4", "Waygate", 15, 300, "10", "3.0", "0.5");
        add("xrs3", "XRS-3", "Golden", 270, 270, "0.1", "3.0", "0.2");
        add("mg452", "MG452", "Comet", 20, 450, "15", "5.5", "1.0");
    }

    private Tubes() {}

    /**
     * The milliampere seconds of a shot, linear in both of its inputs.
     *
     * @throws ValidationError for a current that is not positive
     */
    public static BigDecimal milliampereSeconds(BigDecimal currentMa, Duration span) {
        if (currentMa.signum() <= 0) {
            throw new ValidationError("a current has to be positive");
        }
        return Decimals.multiply(currentMa, span.seconds());
    }

    /**
     * How long to run at that current to reach a required exposure, up to the next whole second.
     *
     * @throws ValidationError for a current or an exposure that is not positive
     */
    public static Duration timeFor(BigDecimal currentMa, BigDecimal mas) {
        if (currentMa.signum() <= 0) {
            throw new ValidationError("a current has to be positive");
        }
        if (mas.signum() <= 0) {
            throw new ValidationError("an exposure has to be positive");
        }
        return Duration.fromSeconds(Decimals.divide(mas, currentMa)).roundUpToSecond();
    }

    /**
     * Look a set up by its key.
     *
     * @throws ConfigurationError for a set the shop does not own
     */
    public static Tube tube(String key) {
        Tube found = TUBES.get(key);
        if (found == null) {
            throw new ConfigurationError("no such x-ray set", Context.of("key", key));
        }
        return found;
    }

    /** Every set key, sorted. */
    public static List<String> knownTubes() {
        return TUBES.keySet().stream().sorted().toList();
    }

    /** Every set in the shop that will make that kilovoltage, finest spot first. */
    public static List<Tube> tubesReaching(int kv) {
        return TUBES.values().stream()
                .filter(candidate -> candidate.canReach(kv))
                .sorted(Comparator.comparing(Tube::focalSpot))
                .toList();
    }

    private static void add(
            String key,
            String name,
            String manufacturer,
            int minKv,
            int maxKv,
            String maxMa,
            String focalSpotMm,
            String dutyCycle) {
        TUBES.put(
                key,
                new Tube(
                        key,
                        name,
                        manufacturer,
                        minKv,
                        maxKv,
                        new BigDecimal(maxMa),
                        Length.fromMm(focalSpotMm),
                        new BigDecimal(dutyCycle)));
    }
}
