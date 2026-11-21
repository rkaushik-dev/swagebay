package io.github.rkaushikdev.swagebay.personnel;

/**
 * Certification under the written practice, which says what somebody may do with a radiograph.
 *
 * <p>A Level I sets up and processes, a Level II interprets and writes techniques, a Level III
 * owns the procedure.
 */
public enum Level {
    LEVEL_I(1, "Level I"),
    LEVEL_II(2, "Level II"),
    LEVEL_III(3, "Level III");

    private final int value;
    private final String label;

    Level(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * The level with that stored number.
     *
     * @throws IllegalArgumentException for a number that is not one, two or three
     */
    public static Level fromValue(int value) {
        for (Level level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException("no level is stored as " + value);
    }

    /** The number that is stored, {@code 2} for a Level II. */
    public int value() {
        return value;
    }

    /** Whether this level may read film and sign the result. */
    public boolean mayInterpret() {
        return value >= 2;
    }

    /** Whether this level may write a technique sheet. */
    public boolean mayWriteTechnique() {
        return value >= 2;
    }

    /** Whether this level may approve a procedure. */
    public boolean mayApproveProcedure() {
        return value >= 3;
    }

    /** The level as the card prints it: {@code Level II}. */
    @Override
    public String toString() {
        return label;
    }
}
