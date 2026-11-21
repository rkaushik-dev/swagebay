package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.safety.MaintenanceRegister;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything the dispatch gate needs about the whole shop, read once.
 *
 * <p>Asking about one load is three small queries and that is fine. Asking about every pairing in
 * the yard through the same path was three per pairing, so six cameras and four sources cost
 * seventy two round trips to read ten rows. This is those ten rows.
 *
 * @param register the inspections of every device on the inventory
 * @param devices the serial of every device on the inventory, sorted
 * @param sources every source in service, by serial in serial order, its wipes loaded
 * @param out the serial of every source out on a trip, sorted
 */
public record Yard(MaintenanceRegister register, Set<String> devices, Map<String, SourceRow> sources, Set<String> out) {

    /** A yard; the sets and the map are copied, keeping their order. */
    public Yard {
        Objects.requireNonNull(register, "register");
        devices = Collections.unmodifiableSet(new TreeSet<>(devices));
        sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        out = Collections.unmodifiableSet(new TreeSet<>(out));
    }

    /** Read the whole yard in four queries, however big the fleet is. */
    public static Yard load(UnitOfWork work) {
        List<String> serials =
                work.sources().devices().stream().map(DeviceRow::getSerial).toList();
        Map<String, SourceRow> sources = new LinkedHashMap<>();
        for (SourceRow row : work.sources().inService()) {
            sources.put(row.getSerial(), row);
        }
        return new Yard(
                work.maintenance().registerFor(serials),
                new TreeSet<>(serials),
                sources,
                work.sources().openTripSerials());
    }
}
