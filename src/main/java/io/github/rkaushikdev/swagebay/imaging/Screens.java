package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A front and back lead screen pair for one radiation quality, and the tube port filter.
 *
 * <p>A lead screen does two things at once and they pull in opposite directions. Electrons knocked
 * out of the lead expose the film, which intensifies the image and shortens the shot. Scattered
 * radiation is softer than the primary beam and the lead absorbs it, which sharpens the image. The
 * first effect wants thin lead and the second wants thick, so every quality has its own thickness,
 * and the iridium screens on a hundred kilovolt shot fog the film rather than helping it.
 *
 * <p>Filtration is the tube side of the same argument: a sheet of copper at the port absorbs the
 * softest part of the spectrum, which would never have got through the part anyway and only adds
 * scatter. See {@link #recommendedFilter}.
 *
 * @param quality the radiation quality the pair is specified for
 * @param front the front screen thickness
 * @param back the back screen thickness
 * @param intensification how many times faster the shot is with the screens fitted, at least one
 */
public record Screens(Quality quality, Length front, Length back, BigDecimal intensification) {

    private static final Map<Quality, Screens> SCREENS = new EnumMap<>(Quality.class);

    static {
        add(new Screens(Quality.KV100, Length.fromMm("0.02"), Length.fromMm("0.10"), new BigDecimal("1.2")));
        add(new Screens(Quality.KV150, Length.fromMm("0.05"), Length.fromMm("0.10"), new BigDecimal("1.5")));
        add(new Screens(Quality.KV220, Length.fromMm("0.10"), Length.fromMm("0.15"), new BigDecimal("2.0")));
        add(new Screens(Quality.KV400, Length.fromMm("0.10"), Length.fromMm("0.15"), new BigDecimal("2.5")));
        add(new Screens(Quality.IR192, Length.fromMm("0.13"), Length.fromMm("0.15"), new BigDecimal("2.5")));
        add(new Screens(Quality.CO60, Length.fromMm("0.25"), Length.fromMm("0.25"), new BigDecimal("3.0")));
    }

    /**
     * A screen pair.
     *
     * @throws ValidationError for a negative thickness, or an intensification under one, which would
     *     make the shot longer
     */
    public Screens {
        if (front.um() < 0 || back.um() < 0) {
            throw new ValidationError("a screen cannot be a negative thickness");
        }
        if (intensification.compareTo(BigDecimal.ONE) < 0) {
            throw new ValidationError(
                    "a screen does not make a shot longer", Context.of("factor", intensification.toString()));
        }
    }

    /**
     * The pair specified for a quality.
     *
     * @throws ConfigurationError for a quality with no pair specified
     */
    public static Screens screensFor(Quality quality) {
        Screens found = SCREENS.get(quality);
        if (found == null) {
            throw new ConfigurationError(
                    "no screen pair is specified for that quality", Context.of("quality", quality.value()));
        }
        return found;
    }

    /** Every quality with a pair specified, in order of the quality's name. */
    public static List<Quality> qualitiesWithScreens() {
        return SCREENS.keySet().stream()
                .sorted(Comparator.comparing(Quality::name))
                .toList();
    }

    /**
     * Why a screen pair does not belong on a shot of this quality, if it does not: front lead
     * thinner than specified will fog, heavier will lose speed.
     *
     * @throws ConfigurationError when the fitted pair is wrong and the quality has no pair specified
     */
    public static Optional<String> wrongScreens(Screens fitted, Quality quality) {
        if (fitted.suits(quality)) {
            return Optional.empty();
        }
        Screens wanted = screensFor(quality);
        String fittedFront = fitted.front().formatMm(2);
        String wantedFront = wanted.front().formatMm(2);
        if (fitted.front().compareTo(wanted.front()) < 0) {
            return Optional.of(fittedFront + " of front lead is thin for " + quality + ", which wants " + wantedFront
                    + " and will fog");
        }
        return Optional.of(fittedFront + " of front lead is heavy for " + quality + ", which wants " + wantedFront
                + " and will lose speed");
    }

    /**
     * The filter for that much steel, if any. Above 25 mm the soft end of the beam is only making
     * scatter, and above 50 mm it takes twice the copper.
     *
     * @throws ValidationError for a thickness that is not positive
     */
    public static Optional<Filter> recommendedFilter(Length steelThickness) {
        if (steelThickness.um() <= 0) {
            throw new ValidationError("a thickness has to be positive");
        }
        if (steelThickness.compareTo(Length.fromMm(25)) < 0) {
            return Optional.empty();
        }
        if (steelThickness.compareTo(Length.fromMm(50)) < 0) {
            return Optional.of(new Filter("copper", Length.fromMm("0.5"), new BigDecimal("1.4")));
        }
        return Optional.of(new Filter("copper", Length.fromMm("1.0"), new BigDecimal("2.0")));
    }

    /** Multiply a chart time by this when the screens are fitted. */
    public BigDecimal timeFactor() {
        return Decimals.divide(BigDecimal.ONE, intensification);
    }

    /** Whether this pair is the one for that quality. */
    public boolean suits(Quality quality) {
        return quality == this.quality;
    }

    /** {@code 0.13 mm front, 0.15 mm back, x2.5 faster}. */
    public String describe() {
        return front.formatMm(2) + " front, " + back.formatMm(2) + " back, x" + intensification + " faster";
    }

    private static void add(Screens pair) {
        SCREENS.put(pair.quality(), pair);
    }
}
