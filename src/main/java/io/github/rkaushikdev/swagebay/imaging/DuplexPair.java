package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * One pair of wires on a duplex gauge, separated by a gap equal to the wire diameter.
 *
 * <p>The finest pair that still reads as two wires rather than one grey smear names the
 * unsharpness of the whole imaging chain. Half the unsharpness is the basic spatial resolution,
 * which is the number on a detector qualification: a pair that reads at 0.40 mm unsharpness is a
 * system with 0.20 mm basic spatial resolution.
 *
 * @param number the pair number on the gauge, 1 coarsest to 13 finest
 */
public record DuplexPair(int number) {

    /**
     * A pair on the gauge.
     *
     * @throws ConfigurationError for a number the gauge does not have
     */
    public DuplexPair {
        if (!Duplex.WIRE_DIAMETERS.containsKey(number)) {
            throw new ConfigurationError("no such duplex pair", Context.of("number", number));
        }
    }

    /** The diameter of each wire, which is also the gap between them. */
    public Length wireDiameter() {
        return Duplex.WIRE_DIAMETERS.get(number);
    }

    /** Wire plus gap, which is what the pair resolves to. */
    public Length unsharpness() {
        return wireDiameter().times(2);
    }

    /** Half the unsharpness. This is the number on the certificate. */
    public Length basicSpatialResolution() {
        return wireDiameter();
    }

    /** The pair as the reader names it: {@code D7}. */
    public String label() {
        return "D" + number;
    }

    @Override
    public String toString() {
        return label();
    }
}
