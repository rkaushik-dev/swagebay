package io.github.rkaushikdev.swagebay.imaging;

/** The ASTM E1815 film system classes, finest grain first. */
public enum FilmClass {
    SPECIAL("special"),
    CLASS_I("I"),
    CLASS_II("II"),
    CLASS_III("III");

    private final String value;

    FilmClass(String value) {
        this.value = value;
    }

    /** The class as the standard writes it. */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
