package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
 * iridium does not take cobalt at all. Some newer bodies are certified for selenium as well, each
 * isotope with its own capacity and its own type test readings, so the certificate carries one
 * line per isotope and the first line is the one the device was bought for. The check is per
 * isotope and it is a refusal, not a warning.
 *
 * @param key the key the model is filed under
 * @param name the model name
 * @param manufacturer who makes it
 * @param certifications one line per isotope the certificate covers, the first the device's own
 * @param massKg the mass, which somebody carries
 * @param maxGuideTube the longest guide tube it is certified with
 * @param isoCategory the ISO 3999 category
 */
public record DeviceModel(
        String key,
        String name,
        String manufacturer,
        List<Certification> certifications,
        BigDecimal massKg,
        Length maxGuideTube,
        String isoCategory) {

    /**
     * One isotope on the certificate.
     *
     * @param isotopeKey the isotope
     * @param capacity the most activity of it the device is certified to hold
     * @param surfaceRateAtCapacity the certified rate at the surface with that load
     * @param metreRateAtCapacity the certified rate at a metre with that load
     */
    public record Certification(
            String isotopeKey, Activity capacity, DoseRate surfaceRateAtCapacity, DoseRate metreRateAtCapacity) {}

    /**
     * A device model.
     *
     * @throws ConfigurationError for a certificate that covers no isotope, or one isotope twice
     */
    public DeviceModel {
        certifications = List.copyOf(certifications);
        if (certifications.isEmpty()) {
            throw new ConfigurationError("a device is certified for at least one isotope", Context.of("device", key));
        }
        if (certifications.stream().map(Certification::isotopeKey).distinct().count() != certifications.size()) {
            throw new ConfigurationError("a certificate names each isotope once", Context.of("device", key));
        }
    }

    /** The isotope the device was bought for, the first on its certificate. */
    public String isotopeKey() {
        return primary().isotopeKey();
    }

    /** The certified capacity for the isotope the device was bought for. */
    public Activity capacity() {
        return primary().capacity();
    }

    /** The certified surface rate with a full load of the isotope the device was bought for. */
    public DoseRate surfaceRateAtCapacity() {
        return primary().surfaceRateAtCapacity();
    }

    /** The certified rate at a metre with a full load of the isotope the device was bought for. */
    public DoseRate metreRateAtCapacity() {
        return primary().metreRateAtCapacity();
    }

    /** The isotope the device was bought for. */
    public Isotope isotope() {
        return Isotopes.isotope(isotopeKey());
    }

    /** The certificate line for that isotope, if the certificate covers it. */
    public Optional<Certification> certificationFor(Isotope sourceIsotope) {
        return certifications.stream()
                .filter(line -> line.isotopeKey().equals(sourceIsotope.key()))
                .findFirst();
    }

    /** Whether the certificate covers that load. */
    public boolean accepts(Isotope sourceIsotope, Activity activity) {
        return certificationFor(sourceIsotope)
                .map(line -> activity.compareTo(line.capacity()) <= 0)
                .orElse(false);
    }

    /**
     * Refuse a load the certificate does not cover.
     *
     * @throws ConfigurationError for an isotope not on the certificate, or more activity than its
     *     certified capacity
     */
    public void checkLoad(Isotope sourceIsotope, Activity activity) {
        Certification line = certificationFor(sourceIsotope)
                .orElseThrow(() -> new ConfigurationError(
                        "this device is not certified for that isotope",
                        Context.of(
                                "device",
                                key,
                                "certified_for",
                                certifications.stream()
                                        .map(Certification::isotopeKey)
                                        .collect(Collectors.joining(", ")),
                                "offered",
                                sourceIsotope.key())));
        if (activity.compareTo(line.capacity()) > 0) {
            throw new ConfigurationError(
                    "load exceeds the certified capacity",
                    Context.of(
                            "device",
                            key,
                            "capacity_ci",
                            line.capacity().curie().toString(),
                            "offered_ci",
                            activity.curie().toString()));
        }
    }

    /** The certified surface rate scaled to the load actually in the device. */
    public DoseRate surfaceRateWith(Activity activity) {
        return surfaceRateAtCapacity().times(activity.ratio(capacity()));
    }

    /** The certified rate at a metre scaled to the load actually in the device. */
    public DoseRate metreRateWith(Activity activity) {
        return metreRateAtCapacity().times(activity.ratio(capacity()));
    }

    /**
     * The certified rate at a metre scaled to a load of that isotope.
     *
     * @throws ConfigurationError for an isotope not on the certificate
     */
    public DoseRate metreRateWith(Isotope sourceIsotope, Activity activity) {
        Certification line = certificationFor(sourceIsotope)
                .orElseThrow(() -> new ConfigurationError(
                        "this device is not certified for that isotope",
                        Context.of("device", key, "offered", sourceIsotope.key())));
        return line.metreRateAtCapacity().times(activity.ratio(line.capacity()));
    }

    private Certification primary() {
        return certifications.get(0);
    }
}
