package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;

/**
 * A type approved exposure device, as its certificate describes it.
 *
 * <p>The device is the yellow box: it holds the source in a shielded S tube and has a crank that
 * drives the pigtail out through a guide tube to the weld. Its type approval certificate says how
 * hot it reads at the surface and at a metre with a full load in it. Those two numbers are measured
 * by the manufacturer on the type test, so they are carried here as data and the shielding model
 * reads them rather than deriving them.
 *
 * <p>A device rated for 130 curies of iridium is not loaded with 150, and a device rated for
 * iridium does not take cobalt at all. The check is per isotope and it is a refusal, not a warning.
 *
 * @param key the key the model is filed under
 * @param name the model name
 * @param manufacturer who makes it
 * @param isotopeKey the one isotope it is certified for
 * @param capacity the most activity it is certified to hold
 * @param surfaceRateAtCapacity the certified rate at the surface with a full load
 * @param metreRateAtCapacity the certified rate at a metre with a full load
 * @param massKg the mass, which somebody carries
 * @param maxGuideTube the longest guide tube it is certified with
 * @param isoCategory the ISO 3999 category
 */
public record DeviceModel(
        String key,
        String name,
        String manufacturer,
        String isotopeKey,
        Activity capacity,
        DoseRate surfaceRateAtCapacity,
        DoseRate metreRateAtCapacity,
        BigDecimal massKg,
        Length maxGuideTube,
        String isoCategory) {

    /** The isotope the device is certified for. */
    public Isotope isotope() {
        return Isotopes.isotope(isotopeKey);
    }

    /** Whether the certificate covers that load. */
    public boolean accepts(Isotope sourceIsotope, Activity activity) {
        return sourceIsotope.key().equals(isotopeKey) && activity.compareTo(capacity) <= 0;
    }

    /**
     * Refuse a load the certificate does not cover.
     *
     * @throws ConfigurationError for the wrong isotope, or more activity than the certified capacity
     */
    public void checkLoad(Isotope sourceIsotope, Activity activity) {
        if (!sourceIsotope.key().equals(isotopeKey)) {
            throw new ConfigurationError(
                    "this device is not certified for that isotope",
                    Context.of("device", key, "certified_for", isotopeKey, "offered", sourceIsotope.key()));
        }
        if (activity.compareTo(capacity) > 0) {
            throw new ConfigurationError(
                    "load exceeds the certified capacity",
                    Context.of(
                            "device",
                            key,
                            "capacity_ci",
                            capacity.curie().toString(),
                            "offered_ci",
                            activity.curie().toString()));
        }
    }

    /** The certified surface rate scaled to the load actually in the device. */
    public DoseRate surfaceRateWith(Activity activity) {
        return surfaceRateAtCapacity.times(activity.ratio(capacity));
    }

    /** The certified rate at a metre scaled to the load actually in the device. */
    public DoseRate metreRateWith(Activity activity) {
        return metreRateAtCapacity.times(activity.ratio(capacity));
    }
}
