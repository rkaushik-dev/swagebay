package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;

/**
 * One ASTM E1025 hole type plaque, identified by its thickness in thousandths of an inch.
 *
 * <p>The plaque has three drilled holes at one, two and four times its thickness. Seeing the
 * essential hole means the technique resolves a change in thickness of that size over that area.
 *
 * @param identity the number stamped on the lead, the thickness in thousandths of an inch
 */
public record HoleIndicator(int identity) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final Length DRILLING_FLOOR = Length.fromThou(10);

    /**
     * A published plaque.
     *
     * @throws ConfigurationError for an identity the standard does not publish
     */
    public HoleIndicator {
        if (!Indicators.HOLE_IDENTITIES.contains(identity)) {
            throw new ConfigurationError("no such hole type identity", Context.of("identity", identity));
        }
    }

    /** The plaque thickness. */
    public Length thickness() {
        return Length.fromThou(identity);
    }

    /**
     * The drilled diameter, with the small plaque floor applied.
     *
     * <p>Below ten thousandths the plaque is too thin to drill a 1T hole that survives handling, so
     * the standard puts a floor on the diameter and the hole stops being a multiple of the
     * thickness.
     */
    public Length holeDiameter(HoleSize hole) {
        Length nominal = thickness().times(hole.value());
        return nominal.compareTo(DRILLING_FLOOR) >= 0 ? nominal : DRILLING_FLOOR;
    }

    /**
     * The plaque thickness as a percentage of what the beam crossed.
     *
     * @throws ValidationError when the penetrated thickness is not positive
     */
    public BigDecimal thicknessSensitivityAt(Length penetrated) {
        Indicators.requirePenetrated(penetrated);
        return Decimals.multiply(thickness().ratio(penetrated), HUNDRED);
    }

    /**
     * The equivalent penetrameter sensitivity of the E1025 appendix.
     *
     * <p>A thin plaque with a big hole and a thick plaque with a small hole can be equally hard to
     * see, and this is the number that says so. It falls out of the area of the hole as much as the
     * thickness of the plaque, which is why a 4T hole on a thin plaque buys so little.
     *
     * @throws ValidationError when the penetrated thickness is not positive
     */
    public BigDecimal equivalentSensitivityAt(Length penetrated, HoleSize hole) {
        Indicators.requirePenetrated(penetrated);
        BigDecimal plaque = BigDecimal.valueOf(thickness().um());
        BigDecimal diameter = BigDecimal.valueOf(holeDiameter(hole).um());
        BigDecimal root = Decimals.sqrt(Decimals.divide(Decimals.multiply(plaque, diameter), TWO));
        return Decimals.divide(Decimals.multiply(HUNDRED, root), BigDecimal.valueOf(penetrated.um()));
    }

    /** The number as it is written in the indicator column of a report. */
    public String label() {
        return Integer.toString(identity);
    }

    @Override
    public String toString() {
        return "hole " + identity;
    }
}
