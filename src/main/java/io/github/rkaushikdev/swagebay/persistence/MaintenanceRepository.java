package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.models.DefectRow;
import io.github.rkaushikdev.swagebay.persistence.models.InspectionRow;
import io.github.rkaushikdev.swagebay.safety.Defect;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import io.github.rkaushikdev.swagebay.safety.MaintenanceRegister;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Inspections in, and a register out.
 *
 * <p>The defects are loaded with the inspections rather than behind them. Every caller reads them,
 * because whether an inspection passed is a question about its defects, so loading them afterwards
 * is a guaranteed second query per row, and it was showing up as twelve queries to check six
 * cameras.
 *
 * <p>The register is a domain object with a fleet's history in it, and it is built here from the
 * rows for the devices actually being asked about rather than for everything. That is the
 * difference between a morning check that reads six rows and one that reads six thousand by the end
 * of the second year.
 */
public final class MaintenanceRepository implements Repository {

    /** What separates the item keys in the column of items checked. */
    public static final String SEPARATOR = ",";

    private final EntityManager entityManager;

    /**
     * A device, and what was found wrong with one of its items.
     *
     * @param deviceSerial the device the defect was raised on
     * @param account what the inspector wrote
     */
    public record Fault(String deviceSerial, String account) {}

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public MaintenanceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /**
     * A row and its defects, back into the inspection.
     *
     * @throws ValidationError for an item key nothing here inspects
     */
    public static Inspection rowToInspection(InspectionRow row) {
        List<InspectionItem> items = Arrays.stream(row.getItemsChecked().split(SEPARATOR, -1))
                .filter(key -> !key.isEmpty())
                .map(MaintenanceRepository::itemNamed)
                .toList();
        List<Defect> defects = row.getDefects().stream()
                .map(defect -> new Defect(itemNamed(defect.getItemKey()), defect.getAccount()))
                .toList();
        return new Inspection(
                row.getDeviceSerial(),
                row.getPerformedOn(),
                row.getPerformedBy(),
                row.isQuarterly(),
                items,
                defects,
                row.getNotes());
    }

    /** An inspection as a row, its defects hanging off it, each item by its key. */
    public static InspectionRow inspectionToRow(Inspection inspection) {
        InspectionRow row = new InspectionRow();
        row.setDeviceSerial(inspection.deviceSerial());
        row.setPerformedOn(inspection.performedOn());
        row.setPerformedBy(inspection.performedBy());
        row.setQuarterly(inspection.isQuarterly());
        row.setItemsChecked(
                inspection.itemsChecked().stream().map(InspectionItem::name).collect(Collectors.joining(SEPARATOR)));
        row.setNotes(inspection.notes());
        for (Defect defect : inspection.defects()) {
            DefectRow found = new DefectRow();
            found.setItemKey(defect.item().name());
            found.setAccount(defect.account());
            row.addDefect(found);
        }
        return row;
    }

    /** Write an inspection, and the defects raised on it, in this unit's transaction. */
    public InspectionRow record(Inspection inspection) {
        InspectionRow row = inspectionToRow(inspection);
        entityManager.persist(row);
        return row;
    }

    /** Every inspection of one device, in the order they were done. */
    public List<Inspection> forDevice(String serial) {
        return entityManager
                .createQuery(
                        "select i from InspectionRow i left join fetch i.defects where i.deviceSerial = :serial"
                                + " order by i.performedOn, i.id",
                        InspectionRow.class)
                .setParameter("serial", serial)
                .getResultList()
                .stream()
                .map(MaintenanceRepository::rowToInspection)
                .toList();
    }

    /** A register holding the inspections of those devices and no others; none asked for, none held. */
    public MaintenanceRegister registerFor(List<String> serials) {
        MaintenanceRegister register = new MaintenanceRegister();
        if (serials.isEmpty()) {
            return register;
        }
        entityManager
                .createQuery(
                        "select i from InspectionRow i left join fetch i.defects where i.deviceSerial in :serials"
                                + " order by i.performedOn, i.id",
                        InspectionRow.class)
                .setParameter("serials", serials)
                .getResultList()
                .forEach(row -> register.record(rowToInspection(row)));
        return register;
    }

    /** Every device that has ever had a defect on {@code item}, with the account, in the order found. */
    public List<Fault> defectsOn(InspectionItem item) {
        return entityManager
                .createQuery(
                        "select i.deviceSerial, d.account from DefectRow d join d.inspection i"
                                + " where d.itemKey = :item order by i.performedOn, i.id",
                        Tuple.class)
                .setParameter("item", item.name())
                .getResultList()
                .stream()
                .map(row -> new Fault(row.get(0, String.class), row.get(1, String.class)))
                .toList();
    }

    private static InspectionItem itemNamed(String key) {
        return Arrays.stream(InspectionItem.values())
                .filter(item -> item.name().equals(key))
                .findFirst()
                .orElseThrow(() -> new ValidationError("nothing here inspects that", Context.of("item", key)));
    }
}
