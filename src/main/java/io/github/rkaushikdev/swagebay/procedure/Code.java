package io.github.rkaushikdev.swagebay.procedure;

import io.github.rkaushikdev.swagebay.util.Repr;

/** The code a procedure is written to. */
public enum Code {
    ASME_V("ASME Section V"),
    API_1104("API 1104"),
    ISO_17636("ISO 17636-1"),
    ASME_VIII("ASME Section VIII Division 1");

    private final String value;

    Code(String value) {
        this.value = value;
    }

    /**
     * The code with that stored title.
     *
     * @throws IllegalArgumentException for a title that names no code
     */
    public static Code fromValue(String value) {
        for (Code code : values()) {
            if (code.value.equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("no procedure code is stored as " + Repr.of(value));
    }

    /** The title of the code, {@code API 1104}. */
    public String value() {
        return value;
    }

    /** The title of the code. */
    @Override
    public String toString() {
        return value;
    }
}
