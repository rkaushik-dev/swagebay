package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.ActivityColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;

/**
 * A sealed source as its certificate describes it, with the leak tests, trips and sightings that
 * hang off it.
 *
 * <p>The children go with the source: deleting a source deletes them, and one taken out of its
 * source's list is deleted too. A leak test with no source is not a record, it is a fragment, and
 * leaving them behind makes the quarterly accountability query wrong in a way nobody notices for
 * months.
 */
@Entity
@Table(
        name = "source",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_source_serial",
                    columnNames = {"serial"})
        })
@Check(name = "ck_source_assay_positive", constraints = "assay_activity > 0")
@Check(name = "ck_source_pellet_diameter_positive", constraints = "active_diameter > 0")
@Check(name = "ck_source_pellet_length_positive", constraints = "active_length > 0")
public class SourceRow extends Keyed {

    @Column(name = "serial", length = 40, nullable = false)
    private String serial;

    @Column(name = "isotope_key", length = 20, nullable = false)
    private String isotopeKey;

    @Convert(converter = ActivityColumn.class)
    @Column(name = "assay_activity", nullable = false)
    private Activity assayActivity;

    @Column(name = "assay_at", nullable = false)
    private LocalDateTime assayAt;

    @Column(name = "manufacturer", length = 80, nullable = false)
    private String manufacturer;

    @Column(name = "model", length = 80, nullable = false)
    private String model;

    @Convert(converter = LengthColumn.class)
    @Column(name = "active_diameter", nullable = false)
    private Length activeDiameter;

    @Convert(converter = LengthColumn.class)
    @Column(name = "active_length", nullable = false)
    private Length activeLength;

    @Column(name = "state", length = 30, nullable = false)
    private String state = "in service";

    @Convert(converter = ActivityColumn.class)
    @Column(name = "working_floor", nullable = false)
    private Activity workingFloor;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<LeakTestRow> leakTests = new ArrayList<>();

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<TripRow> trips = new ArrayList<>();

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<SightingRow> sightings = new ArrayList<>();

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getIsotopeKey() {
        return isotopeKey;
    }

    public void setIsotopeKey(String isotopeKey) {
        this.isotopeKey = isotopeKey;
    }

    public Activity getAssayActivity() {
        return assayActivity;
    }

    public void setAssayActivity(Activity assayActivity) {
        this.assayActivity = assayActivity;
    }

    public LocalDateTime getAssayAt() {
        return assayAt;
    }

    public void setAssayAt(LocalDateTime assayAt) {
        this.assayAt = assayAt;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Length getActiveDiameter() {
        return activeDiameter;
    }

    public void setActiveDiameter(Length activeDiameter) {
        this.activeDiameter = activeDiameter;
    }

    public Length getActiveLength() {
        return activeLength;
    }

    public void setActiveLength(Length activeLength) {
        this.activeLength = activeLength;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Activity getWorkingFloor() {
        return workingFloor;
    }

    public void setWorkingFloor(Activity workingFloor) {
        this.workingFloor = workingFloor;
    }

    public List<LeakTestRow> getLeakTests() {
        return Collections.unmodifiableList(leakTests);
    }

    /** Hang {@code leakTest} off this row; it is written with this one. */
    public void addLeakTest(LeakTestRow leakTest) {
        leakTest.setSource(this);
        leakTests.add(leakTest);
    }

    public List<TripRow> getTrips() {
        return Collections.unmodifiableList(trips);
    }

    /** Hang {@code trip} off this row; it is written with this one. */
    public void addTrip(TripRow trip) {
        trip.setSource(this);
        trips.add(trip);
    }

    public List<SightingRow> getSightings() {
        return Collections.unmodifiableList(sightings);
    }

    /** Hang {@code sighting} off this row; it is written with this one. */
    public void addSighting(SightingRow sighting) {
        sighting.setSource(this);
        sightings.add(sighting);
    }
}
