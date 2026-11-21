package io.github.rkaushikdev.swagebay.geometry;

/** What kind of weld a joint is, which decides what shots are available on it. */
public enum JointForm {
    PIPE_GIRTH("girth weld"),
    PIPE_SEAM("longitudinal seam"),
    PLATE_BUTT("plate butt weld"),
    NOZZLE("nozzle attachment weld"),
    SOCKET("socket weld");

    private final String value;

    JointForm(String value) {
        this.value = value;
    }

    /** The form as the technique sheet writes it: {@code girth weld}. */
    public String value() {
        return value;
    }

    /** Whether the weld runs round a circumference: a girth, a nozzle or a socket weld. */
    public boolean isCircumferential() {
        return this == PIPE_GIRTH || this == NOZZLE || this == SOCKET;
    }

    @Override
    public String toString() {
        return value;
    }
}
