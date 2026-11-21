package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;

/**
 * One inspection of one device, with the defects raised on it.
 *
 * <p>Two tables rather than one, because a defect is a row with two facts on it, and squeezing them
 * into a text column on the inspection means the day somebody asks which devices have ever had a
 * drive cable fault, the answer is a pattern match against prose.
 *
 * <p>The items checked do go in one column, as a comma separated list of item keys. That is the one
 * place a list is denormalised here, and it is deliberate: the list is written once, read whole,
 * and never filtered on. A join table for it would be three hundred rows a quarter that nothing
 * ever queries.
 */
@Entity
@Table(
        name = "inspection",
        indexes = {@Index(name = "ix_inspection_device", columnList = "device_serial, performed_on")})
@Check(name = "ck_inspection_an_inspection_names_the_device", constraints = "length(device_serial) > 0")
@Check(name = "ck_inspection_an_inspection_is_signed", constraints = "length(performed_by) > 0")
@Check(name = "ck_inspection_an_inspection_checked_something", constraints = "length(items_checked) > 0")
public class InspectionRow extends Keyed {

    @Column(name = "device_serial", length = 40, nullable = false)
    private String deviceSerial;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "performed_by", length = 20, nullable = false)
    private String performedBy;

    @Column(name = "is_quarterly", nullable = false)
    private boolean isQuarterly;

    @Column(name = "items_checked", nullable = false, columnDefinition = Columns.TEXT)
    private String itemsChecked;

    @Column(name = "notes", length = 400, nullable = false)
    private String notes = "";

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<DefectRow> defects = new ArrayList<>();

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public LocalDate getPerformedOn() {
        return performedOn;
    }

    public void setPerformedOn(LocalDate performedOn) {
        this.performedOn = performedOn;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public boolean isQuarterly() {
        return isQuarterly;
    }

    public void setQuarterly(boolean isQuarterly) {
        this.isQuarterly = isQuarterly;
    }

    public String getItemsChecked() {
        return itemsChecked;
    }

    public void setItemsChecked(String itemsChecked) {
        this.itemsChecked = itemsChecked;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<DefectRow> getDefects() {
        return Collections.unmodifiableList(defects);
    }

    /** Hang {@code defect} off this row; it is written with this one. */
    public void addDefect(DefectRow defect) {
        defect.setInspection(this);
        defects.add(defect);
    }
}
