package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;

/**
 * The wire the standard demands, and the stock sets that carry it.
 *
 * @param penetrated the penetrated thickness the table was read at
 * @param techniqueClass the class whose table was read
 * @param wire the essential wire
 * @param availableSets the stock sets that hold that wire, in alphabetical order
 */
public record WireSelection(
        Length penetrated, TechniqueClass techniqueClass, WireIndicator wire, List<String> availableSets)
        implements IndicatorSelection {

    /** A wire selection; the sets are copied. */
    public WireSelection {
        availableSets = List.copyOf(availableSets);
    }

    /** {@code W13 class A, set C/D}. */
    @Override
    public String describe() {
        String sets = availableSets.isEmpty() ? "no stock set" : String.join("/", availableSets);
        return wire.isoLabel() + " " + techniqueClass + ", set " + sets;
    }
}
