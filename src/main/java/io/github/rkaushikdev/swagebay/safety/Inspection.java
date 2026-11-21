package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One inspection of one device, quarterly or before use.
 *
 * <p>The quarterly is a strip down: every three months the device, the drive cable, the guide tube,
 * the connectors and the source changer come apart and get looked at, and the record is kept for
 * three years. Miss it and the device is not out of tolerance, it is not authorised. The pre use
 * check happens in the yard before the truck moves. It is a look and a pull at the safety items, it
 * takes four minutes, and it is the one that actually catches things.
 *
 * @param deviceSerial the device inspected, never blank
 * @param performedOn the day of the inspection
 * @param performedBy the badge of whoever signed it, never blank
 * @param isQuarterly whether it was the quarterly strip down rather than a pre use check
 * @param itemsChecked the items looked at, each once, at least one, and every item on a quarterly
 * @param defects what was found, each on an item that was checked
 * @param notes anything else worth writing down
 */
public record Inspection(
        String deviceSerial,
        LocalDate performedOn,
        String performedBy,
        boolean isQuarterly,
        List<InspectionItem> itemsChecked,
        List<Defect> defects,
        String notes) {

    /** A quarterly covers the device for this many days from the day it was done. */
    public static final int QUARTERLY_DAYS = 92;

    /** Inspection records are kept this many days, three years. */
    public static final int RECORD_RETENTION_DAYS = 3 * 365;

    /**
     * An inspection; the items and defects are copied.
     *
     * @throws ValidationError without a device or a signature, for an empty or repeated item list, for
     *     a defect on an item nobody checked, or for a quarterly that missed an item
     */
    public Inspection {
        Objects.requireNonNull(performedOn, "performedOn");
        Objects.requireNonNull(notes, "notes");
        itemsChecked = List.copyOf(itemsChecked);
        defects = List.copyOf(defects);
        if (Text.strip(deviceSerial).isEmpty()) {
            throw new ValidationError("an inspection names the device");
        }
        if (Text.strip(performedBy).isEmpty()) {
            throw new ValidationError("an inspection is signed");
        }
        if (itemsChecked.isEmpty()) {
            throw new ValidationError("an inspection that checked nothing is not an inspection");
        }
        if (new HashSet<>(itemsChecked).size() != itemsChecked.size()) {
            throw new ValidationError("that item is on the sheet twice");
        }
        List<InspectionItem> checked = itemsChecked;
        Optional<InspectionItem> unchecked = defects.stream()
                .map(Defect::item)
                .filter(item -> !checked.contains(item))
                .findFirst();
        if (unchecked.isPresent()) {
            throw new ValidationError(
                    "a defect was raised on an item nobody checked",
                    Context.of("item", unchecked.get().toString()));
        }
        if (isQuarterly) {
            List<InspectionItem> missed = Arrays.stream(InspectionItem.values())
                    .filter(item -> !checked.contains(item))
                    .toList();
            if (!missed.isEmpty()) {
                throw new ValidationError(
                        "a quarterly covers every item on the device",
                        Context.of(
                                "missed",
                                missed.stream().map(InspectionItem::toString).collect(Collectors.joining(", "))));
            }
        }
    }

    /** An inspection with nothing found and no notes. */
    public Inspection(
            String deviceSerial,
            LocalDate performedOn,
            String performedBy,
            boolean isQuarterly,
            List<InspectionItem> itemsChecked) {
        this(deviceSerial, performedOn, performedBy, isQuarterly, itemsChecked, List.of(), "");
    }

    /** An inspection with no notes. */
    public Inspection(
            String deviceSerial,
            LocalDate performedOn,
            String performedBy,
            boolean isQuarterly,
            List<InspectionItem> itemsChecked,
            List<Defect> defects) {
        this(deviceSerial, performedOn, performedBy, isQuarterly, itemsChecked, defects, "");
    }

    /** The yard check with nothing found, which covers the safety items and nothing else. */
    public static Inspection preUse(String deviceSerial, LocalDate on, String by) {
        return preUse(deviceSerial, on, by, List.of(), "");
    }

    /** The yard check, which covers the safety items and nothing else. */
    public static Inspection preUse(String deviceSerial, LocalDate on, String by, List<Defect> defects) {
        return preUse(deviceSerial, on, by, defects, "");
    }

    /** The yard check with notes, which covers the safety items and nothing else. */
    public static Inspection preUse(String deviceSerial, LocalDate on, String by, List<Defect> defects, String notes) {
        return new Inspection(deviceSerial, on, by, false, InspectionItem.safetyItems(), defects, notes);
    }

    /** The strip down with nothing found, which covers everything. */
    public static Inspection quarterly(String deviceSerial, LocalDate on, String by) {
        return quarterly(deviceSerial, on, by, List.of(), "");
    }

    /** The strip down, which covers everything. */
    public static Inspection quarterly(String deviceSerial, LocalDate on, String by, List<Defect> defects) {
        return quarterly(deviceSerial, on, by, defects, "");
    }

    /** The strip down with notes, which covers everything. */
    public static Inspection quarterly(
            String deviceSerial, LocalDate on, String by, List<Defect> defects, String notes) {
        return new Inspection(deviceSerial, on, by, true, List.of(InspectionItem.values()), defects, notes);
    }

    /** Whether nothing found stops the device. */
    public boolean passed() {
        return blockingDefects().isEmpty();
    }

    /** The defects that stop the device, in the order found. */
    public List<Defect> blockingDefects() {
        return defects.stream().filter(Defect::stopsTheDevice).toList();
    }

    /** The defects written down and worked around, in the order found. */
    public List<Defect> notedDefects() {
        return defects.stream().filter(defect -> !defect.stopsTheDevice()).toList();
    }

    /**
     * The first day a quarterly no longer covers the device. A pre use check covers only the day it
     * was done, so it has none.
     */
    public Optional<LocalDate> expiresOn() {
        if (!isQuarterly) {
            return Optional.empty();
        }
        return Optional.of(performedOn.plusDays(QUARTERLY_DAYS));
    }

    /** The day the record may be destroyed. */
    public LocalDate destroyAfter() {
        return performedOn.plusDays(RECORD_RETENTION_DAYS);
    }

    /** The sheet line: {@code 2025-04-14 D-77 quarterly passed by 0303}. */
    public String describe() {
        String kind = isQuarterly ? "quarterly" : "pre use";
        String verdict = passed() ? "passed" : "failed";
        return performedOn + " " + deviceSerial + " " + kind + " " + verdict + " by " + performedBy;
    }
}
