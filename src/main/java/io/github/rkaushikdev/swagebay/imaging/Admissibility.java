package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a radiograph is allowed to have an opinion about the weld, with every reason against it.
 *
 * <p>{@link #assess} puts the pieces of the imaging rules together, and the assembly is the
 * interesting part. The medium decides what the other rules measure. On film the density gate runs
 * and the noise gate is not applicable: reporting a noise failure on a piece of film is a category
 * error, not a strict reading. On a plate or a panel it is the other way round, and the resolution
 * gate joins in, because a digital system can be sharp enough or not and film always is.
 *
 * <p>The indicator gate runs on everything and cannot be waived. A radiograph whose essential hole
 * is not visible is not a marginal radiograph, it is not a radiograph, whatever its density says.
 *
 * <p>Every reason is collected. Stopping at the first failure produces a shooter who fixes one
 * thing, reshoots, and finds the second thing, which on a night shift is two nights instead of one.
 *
 * @param admissible whether there is no reason against the radiograph
 * @param reasons every reason against it, in the order the gates ran
 * @param gatesRun the gates that ran, in order
 */
public record Admissibility(boolean admissible, List<String> reasons, List<String> gatesRun) {

    /** A verdict; the lists are copied. */
    public Admissibility {
        reasons = List.copyOf(reasons);
        gatesRun = List.copyOf(gatesRun);
    }

    /** Run the gates the medium calls for, and collect every failure. */
    public static Admissibility assess(Evidence evidence) {
        List<String> reasons = new ArrayList<>();
        List<String> gates = new ArrayList<>();

        gates.add("indicator");
        if (!evidence.indicator().satisfied()) {
            reasons.add(evidence.indicator().describeFailure());
        }

        if (evidence.system().medium().gradedOnDensity()) {
            gates.add("density");
            Optional<DensityVerdict> density = evidence.density();
            if (density.isEmpty()) {
                reasons.add("no density readings on record");
            } else if (!density.get().passed()) {
                reasons.addAll(density.get().reasons());
            }
        } else {
            gates.add("normalised snr");
            int floor = Noise.requiredNormalisedSnr(evidence.penetrated(), evidence.techniqueClass());
            Optional<NoiseMeasurement> noise = evidence.noise();
            if (noise.isEmpty()) {
                reasons.add("no signal to noise measurement on record");
            } else if (noise.get().normalised().compareTo(BigDecimal.valueOf(floor)) < 0) {
                reasons.add("normalised snr "
                        + Decimals.halfEven(noise.get().normalised(), 1).toPlainString()
                        + " is under the floor of " + floor);
            }
            gates.add("basic spatial resolution");
            reasons.addAll(resolutionReasons(evidence));
        }

        return new Admissibility(reasons.isEmpty(), reasons, gates);
    }

    /** Whether the density gate applies to images from that system, which is to say film. */
    public static boolean densityIsApplicable(ImagingSystem system) {
        return system.medium() == Medium.FILM;
    }

    /** The normalised signal to noise floor the evidence is held to. */
    public static BigDecimal noiseFloorFor(Evidence evidence) {
        return BigDecimal.valueOf(Noise.requiredNormalisedSnr(evidence.penetrated(), evidence.techniqueClass()));
    }

    /** {@code admissible}, or every reason joined with semicolons. */
    public String describe() {
        if (admissible) {
            return "admissible";
        }
        return String.join("; ", reasons);
    }

    private static List<String> resolutionReasons(Evidence evidence) {
        if (evidence.requiredResolution().isEmpty()) {
            return List.of();
        }
        Length required = evidence.requiredResolution().get();
        if (evidence.duplexReading().isEmpty()) {
            return List.of("no duplex gauge reading on record");
        }
        Length measured = Duplex.resolutionFromReading(evidence.duplexReading().get());
        if (measured.compareTo(required) > 0) {
            return List.of("measured resolution " + measured.formatMm(3) + " is coarser than " + required.formatMm(3));
        }
        return List.of();
    }
}
