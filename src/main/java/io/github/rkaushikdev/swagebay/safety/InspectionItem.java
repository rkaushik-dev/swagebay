package io.github.rkaushikdev.swagebay.safety;

import java.util.Arrays;
import java.util.List;

/**
 * The things on a device that get looked at, and what a fault on each one means.
 *
 * <p>A defect on a safety item stops the device that morning; a defect on anything else is written
 * down and worked around. The difference is decided by the item, not by whoever is in a hurry, so
 * the classification lives here and the person filling in the form does not get a say.
 */
public enum InspectionItem {
    DRIVE_CABLE("drive cable", Severity.STOPS_THE_DEVICE),
    GUIDE_TUBE("guide tube", Severity.STOPS_THE_DEVICE),
    CONNECTORS("connectors", Severity.STOPS_THE_DEVICE),
    SOURCE_LOCK("source lock", Severity.STOPS_THE_DEVICE),
    SELECTOR_RING("selector ring", Severity.STOPS_THE_DEVICE),
    CRANK_ASSEMBLY("crank assembly", Severity.STOPS_THE_DEVICE),
    LABELS("labels", Severity.WORK_AROUND),
    CARRY_HANDLE("carry handle", Severity.WORK_AROUND),
    CASE_AND_SEALS("case and seals", Severity.WORK_AROUND),
    WHEELS("wheels", Severity.WORK_AROUND);

    private final String label;
    private final Severity severity;

    InspectionItem(String label, Severity severity) {
        this.label = label;
        this.severity = severity;
    }

    /** The items a defect on stops the device, which is what the pre use check covers, in sheet order. */
    public static List<InspectionItem> safetyItems() {
        return Arrays.stream(values()).filter(InspectionItem::stopsTheDevice).toList();
    }

    /** The name on the inspection sheet, {@code drive cable}. */
    public String label() {
        return label;
    }

    /** What a defect on this item does to the day. */
    public Severity severity() {
        return severity;
    }

    /** Whether a defect on this item stops the device. */
    public boolean stopsTheDevice() {
        return severity == Severity.STOPS_THE_DEVICE;
    }

    /** The name on the inspection sheet. */
    @Override
    public String toString() {
        return label;
    }
}
