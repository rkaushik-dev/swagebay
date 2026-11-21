package io.github.rkaushikdev.swagebay.safety;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What one person is carrying.
 *
 * <p>All three kinds of dosimeter are required and the requirement is not a preference: an assistant
 * without an alarm on his belt is not allowed inside the boundary, whatever the badge situation is,
 * so each kind is reported separately.
 *
 * @param badge the badge of the person carrying them
 * @param workerClass which limit they are held to
 * @param dosimeters what they carry
 */
public record Assignment(String badge, WorkerClass workerClass, List<Dosimeter> dosimeters) {

    /** An assignment; the dosimeters are copied. */
    public Assignment {
        Objects.requireNonNull(badge, "badge");
        Objects.requireNonNull(workerClass, "workerClass");
        dosimeters = List.copyOf(dosimeters);
    }

    /** An adult worker carrying nothing yet. */
    public Assignment(String badge) {
        this(badge, WorkerClass.ADULT, List.of());
    }

    /** An adult worker carrying those dosimeters. */
    public Assignment(String badge, List<Dosimeter> dosimeters) {
        this(badge, WorkerClass.ADULT, dosimeters);
    }

    /** The kinds of dosimeter carried. */
    public Set<DosimeterKind> kinds() {
        Set<DosimeterKind> kinds = EnumSet.noneOf(DosimeterKind.class);
        dosimeters.forEach(dosimeter -> kinds.add(dosimeter.kind()));
        return Collections.unmodifiableSet(kinds);
    }

    /**
     * Every kind that is absent or out of calibration on that day, in the order badge, direct
     * reading, alarm. Of two of one kind, the later one carried is the one judged.
     */
    public List<String> missingOn(LocalDate day) {
        Map<DosimeterKind, Dosimeter> held = new EnumMap<>(DosimeterKind.class);
        dosimeters.forEach(dosimeter -> held.put(dosimeter.kind(), dosimeter));
        List<String> out = new ArrayList<>();
        for (DosimeterKind kind : DosimeterKind.values()) {
            Dosimeter instrument = held.get(kind);
            if (instrument == null) {
                out.add("no " + kind);
            } else if (!instrument.isCurrentOn(day)) {
                out.add(kind + " " + instrument.serial() + " is out of calibration");
            }
        }
        return List.copyOf(out);
    }

    /** Whether the person may go inside the boundary on that day: nothing missing. */
    public boolean mayEnterBoundaryOn(LocalDate day) {
        return missingOn(day).isEmpty();
    }
}
