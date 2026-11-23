package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.stream.IntStream;

/**
 * One wire out of an ASTM E747 set, which is what ASME Section V means by a wire indicator.
 *
 * <p>The numbering runs the other way from ISO 19232. An E747 number goes up as the wire gets
 * thicker, from wire 1 at 0.0032 inch to wire 21 at 0.320, so a crew that reads W13 off an ISO
 * table and picks wire 13 out of an ASTM case is holding a wire six times too thick.
 *
 * <p>The stock sets overlap by one wire: set A is wires 1 to 6, B is 6 to 11, C is 11 to 16 and D
 * is 16 to 21.
 *
 * @param number the E747 wire number, 1 to 21
 */
public record AstmWire(int number) {

    /** The thinnest wire in the series. */
    public static final int FIRST = 1;

    /** The thickest wire in the series. */
    public static final int LAST = 21;

    private static final List<String> DIAMETERS_IN = List.of(
            "0.0032", "0.004", "0.005", "0.0063", "0.008", "0.010", "0.013", "0.016", "0.020", "0.025", "0.032",
            "0.040", "0.050", "0.063", "0.080", "0.100", "0.126", "0.160", "0.200", "0.250", "0.320");

    private static final List<String> SETS = List.of("A", "B", "C", "D");

    /**
     * A wire from the E747 series.
     *
     * @throws ConfigurationError for a number outside the series
     */
    public AstmWire {
        if (number < FIRST || number > LAST) {
            throw new ConfigurationError("no such astm e747 wire", Context.of("number", number));
        }
    }

    /** The wire diameter. */
    public Length diameter() {
        return Length.fromInch(DIAMETERS_IN.get(number - 1));
    }

    /** The sets that hold this wire, in alphabetical order; two for the wire the sets share. */
    public List<String> sets() {
        return IntStream.range(0, SETS.size())
                .filter(set -> number >= 1 + 5 * set && number <= 6 + 5 * set)
                .mapToObj(SETS::get)
                .toList();
    }

    /** The wire as the technique sheet writes it: {@code wire 8}. */
    @Override
    public String toString() {
        return "wire " + number;
    }
}
