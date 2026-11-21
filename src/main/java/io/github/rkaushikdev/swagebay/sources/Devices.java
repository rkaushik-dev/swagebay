package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The exposure device models and the collimators the shop owns. */
public final class Devices {

    private static final Map<String, DeviceModel> DEVICES = new LinkedHashMap<>();
    private static final Map<String, Collimator> COLLIMATORS = new LinkedHashMap<>();

    static {
        addDevice("880d", "880 Delta", "QSA", "ir192", 130, "1.8", 18, "22.2", 12, "II");
        addDevice("880s", "880 Sigma", "QSA", "ir192", 150, "1.9", 19, "24.5", 12, "II");
        addDevice("tsi3", "Gammamat TSI 3", "Isotopen", "ir192", 120, "1.5", 15, "18.0", 9, "II");
        addDevice("spec150", "SPEC 150", "SPEC", "se75", 100, "0.9", 9, "11.3", 9, "I");
        addDevice("tk100", "Gammamat TK 100", "Isotopen", "co60", 45, "1.9", 19, "118.0", 6, "II");

        addCollimator(new Collimator("none", "no collimator", "none", 360, BigDecimal.ONE, BigDecimal.ZERO));
        addCollimator(new Collimator(
                "w60", "tungsten 60 degree", "tungsten", 60, new BigDecimal("0.06"), new BigDecimal("7.5")));
        addCollimator(new Collimator(
                "w90", "tungsten 90 degree", "tungsten", 90, new BigDecimal("0.08"), new BigDecimal("6.2")));
        addCollimator(new Collimator(
                "du45", "depleted uranium 45", "uranium", 45, new BigDecimal("0.03"), new BigDecimal("9.1")));
    }

    private Devices() {}

    /**
     * Look a device model up by its key.
     *
     * @throws ConfigurationError for a model the shop does not own
     */
    public static DeviceModel deviceModel(String key) {
        DeviceModel found = DEVICES.get(key);
        if (found == null) {
            throw new ConfigurationError("no such exposure device", Context.of("key", key));
        }
        return found;
    }

    /**
     * Look a collimator up by its key; {@code none} is an entry, not an absence.
     *
     * @throws ConfigurationError for a collimator the shop does not own
     */
    public static Collimator collimator(String key) {
        Collimator found = COLLIMATORS.get(key);
        if (found == null) {
            throw new ConfigurationError("no such collimator", Context.of("key", key));
        }
        return found;
    }

    /** Every device model key, sorted. */
    public static List<String> knownDevices() {
        return DEVICES.keySet().stream().sorted().toList();
    }

    /** Every collimator key, sorted. */
    public static List<String> knownCollimators() {
        return COLLIMATORS.keySet().stream().sorted().toList();
    }

    /** The device models certified for an isotope, biggest capacity first. */
    public static List<DeviceModel> devicesFor(Isotope sourceIsotope) {
        return DEVICES.values().stream()
                .filter(model -> model.isotopeKey().equals(sourceIsotope.key()))
                .sorted(Comparator.comparingLong(
                                (DeviceModel model) -> model.capacity().bq())
                        .reversed())
                .toList();
    }

    private static void addDevice(
            String key,
            String name,
            String manufacturer,
            String isotopeKey,
            long capacityCurie,
            String surfaceMsvPerHour,
            long metreUsvPerHour,
            String massKg,
            long maxGuideTubeM,
            String isoCategory) {
        DEVICES.put(
                key,
                new DeviceModel(
                        key,
                        name,
                        manufacturer,
                        isotopeKey,
                        Activity.fromCurie(capacityCurie),
                        DoseRate.fromMsvPerHour(surfaceMsvPerHour),
                        DoseRate.fromUsvPerHour(metreUsvPerHour),
                        new BigDecimal(massKg),
                        Length.fromM(maxGuideTubeM),
                        isoCategory));
    }

    private static void addCollimator(Collimator collimator) {
        COLLIMATORS.put(collimator.key(), collimator);
    }
}
