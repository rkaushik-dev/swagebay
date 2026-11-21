package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * A directional shield on the end of the guide tube.
 *
 * <p>A collimator is a tungsten cup that cuts the field down to the sector pointed at the film, and
 * it is the single cheapest thing a crew can do to shrink the boundary they have to rope off. The
 * attenuation is directional: the full beam through the aperture, a published transmission fraction
 * everywhere else.
 *
 * @param key the key the collimator is filed under
 * @param name the name on the shelf
 * @param material what the cup is made of
 * @param apertureDegrees the width of the open sector
 * @param transmission the fraction of the unshielded rate that gets through the body of the cup
 * @param massKg the mass, which the crew carries
 */
public record Collimator(
        String key, String name, String material, int apertureDegrees, BigDecimal transmission, BigDecimal massKg) {

    private static final BigDecimal TURN = BigDecimal.valueOf(360);
    private static final BigDecimal HALF_TURN = BigDecimal.valueOf(180);

    /**
     * A collimator.
     *
     * @throws ValidationError for an aperture outside (0, 360] degrees or a transmission outside (0, 1]
     */
    public Collimator {
        if (apertureDegrees <= 0 || apertureDegrees > 360) {
            throw new ValidationError("an aperture is an angle", Context.of("degrees", apertureDegrees));
        }
        if (transmission.signum() <= 0 || transmission.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationError(
                    "a transmission fraction sits in (0, 1]", Context.of("transmission", transmission.toString()));
        }
    }

    /** Whether a bearing off the beam axis is inside the open sector, its edge included. */
    public boolean covers(BigDecimal bearingDegrees) {
        BigDecimal half = Decimals.divide(BigDecimal.valueOf(apertureDegrees), 2);
        return wrap(bearingDegrees).abs().compareTo(half) <= 0;
    }

    /** The share of the unshielded rate that reaches a bearing: all of it inside the sector. */
    public BigDecimal factorAt(BigDecimal bearingDegrees) {
        return covers(bearingDegrees) ? BigDecimal.ONE : transmission;
    }

    /**
     * Fold a bearing towards the half open interval from minus 180 to 180. The remainder keeps the
     * sign of the bearing, so a bearing below minus 180 stays below it.
     */
    private static BigDecimal wrap(BigDecimal degrees) {
        BigDecimal turned = degrees.remainder(TURN, Decimals.CONTEXT);
        return turned.compareTo(HALF_TURN) > 0 ? Decimals.subtract(turned, TURN) : turned;
    }
}
