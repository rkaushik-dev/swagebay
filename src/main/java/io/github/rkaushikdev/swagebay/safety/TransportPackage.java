package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One device, boxed, as it goes on the truck.
 *
 * @param reference the package reference, never blank
 * @param isotope the isotope inside
 * @param activity how much of it
 * @param packageType the kind of package
 * @param surfaceRate the rate measured at the surface of the package
 * @param rateAtOneMetre the rate measured a metre from it
 */
public record TransportPackage(
        String reference,
        Isotope isotope,
        Activity activity,
        PackageType packageType,
        DoseRate surfaceRate,
        DoseRate rateAtOneMetre) {

    /**
     * A package.
     *
     * @throws ValidationError without a reference
     */
    public TransportPackage {
        Objects.requireNonNull(isotope, "isotope");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(packageType, "packageType");
        Objects.requireNonNull(surfaceRate, "surfaceRate");
        Objects.requireNonNull(rateAtOneMetre, "rateAtOneMetre");
        if (Text.strip(reference).isEmpty()) {
            throw new ValidationError("a package has a reference");
        }
    }

    /** The transport index from the reading at a metre. */
    public BigDecimal transportIndex() {
        return Transport.transportIndex(rateAtOneMetre);
    }

    /**
     * The label the package carries.
     *
     * @throws TechniqueError for a package over the limits for every label
     */
    public Label label() {
        return Transport.labelFor(surfaceRate, rateAtOneMetre);
    }

    /**
     * Everything wrong with the package before it leaves the yard: contents over the A1 limit for a
     * Type A package, a source travelling as an excepted package, and a package no label covers.
     *
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError for a Type A package of an isotope with
     *     no published limit
     */
    public List<String> faults() {
        List<String> out = new ArrayList<>();
        if (packageType == PackageType.TYPE_A) {
            Activity limit = Transport.a1Limit(isotope);
            if (activity.compareTo(limit) > 0) {
                out.add(activity.formatCurie() + " is over the " + limit.formatTbq()
                        + " A1 limit for a Type A package");
            }
        }
        if (packageType == PackageType.EXCEPTED) {
            out.add("a radiography source never travels as an excepted package");
        }
        try {
            label();
        } catch (TechniqueError over) {
            out.add(over.message());
        }
        return List.copyOf(out);
    }
}
