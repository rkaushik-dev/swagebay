package io.github.rkaushikdev.swagebay.reporting;

/** One thing a submittal can contain. */
public enum Enclosure {
    REPORT("radiographic report"),
    RADIOGRAPHS("the radiographs"),
    PROCEDURE("the procedure revision"),
    CERTIFICATIONS("personnel certifications"),
    CALIBRATIONS("equipment calibration certificates"),
    WELD_MAP("weld map"),
    REPAIR_RECORD("repair record");

    private final String value;

    Enclosure(String value) {
        this.value = value;
    }

    /** What it is called on a covering note, {@code the procedure revision}. */
    public String value() {
        return value;
    }

    /** What it is called on a covering note. */
    @Override
    public String toString() {
        return value;
    }
}
