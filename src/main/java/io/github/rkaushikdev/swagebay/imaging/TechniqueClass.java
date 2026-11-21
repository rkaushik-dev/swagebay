package io.github.rkaushikdev.swagebay.imaging;

/** The ISO 17636-1 technique class every job falls in; class B is the improved one. */
public enum TechniqueClass {
    A("A"),
    B("B");

    private final String value;

    TechniqueClass(String value) {
        this.value = value;
    }

    /** The class letter. */
    public String value() {
        return value;
    }

    /** Whether this is the improved class, which asks for a finer wire and a better image. */
    public boolean isImproved() {
        return this == B;
    }

    /** The class the way a specification names it: {@code class B}. */
    @Override
    public String toString() {
        return "class " + value;
    }
}
