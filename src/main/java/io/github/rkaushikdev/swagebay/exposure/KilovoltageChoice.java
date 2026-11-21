package io.github.rkaushikdev.swagebay.exposure;

import java.util.List;

/**
 * A kilovoltage, with why it is not the table value.
 *
 * @param kv the kilovoltage chosen
 * @param fromTable the lowest workable kilovoltage the table gives, where every choice starts
 * @param reasons why the choice moved off the table value, in the order they applied
 */
public record KilovoltageChoice(int kv, int fromTable, List<String> reasons) {

    /** A choice; the reasons are copied. */
    public KilovoltageChoice {
        reasons = List.copyOf(reasons);
    }

    /** Whether the choice is the table value. */
    public boolean isTableValue() {
        return kv == fromTable;
    }

    /** How many whole contrast steps the choice sits above the table value. */
    public int stepsAboveTable() {
        return Math.floorDiv(kv - fromTable, Kilovoltage.CONTRAST_STEP);
    }

    /** The choice as the sheet prints it: {@code 150 kV}, or what it gave up and why. */
    public String describe() {
        if (isTableValue()) {
            return kv + " kV";
        }
        return kv + " kV, up from " + fromTable + ": " + String.join("; ", reasons);
    }
}
