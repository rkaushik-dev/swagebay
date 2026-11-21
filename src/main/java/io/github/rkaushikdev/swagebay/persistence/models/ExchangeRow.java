package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One source swap, on one device, on one day.
 *
 * <p>Source exchanges and the utilisation log are the two shop registers. Both are append only in
 * practice and neither is enforced as such here, because both get corrected: a utilisation entry
 * written from memory at the end of the week gets an exposure count added on Monday, and an
 * exchange gets its shipment number when the paperwork arrives. The audit trail covers who changed
 * them.
 */
@Entity
@Table(
        name = "exchange",
        indexes = {
            @Index(name = "ix_exchange_device", columnList = "device_serial, performed_on"),
            @Index(name = "ix_exchange_shipment", columnList = "shipment")
        })
@Check(name = "ck_exchange_an_exchange_names_the_device", constraints = "length(device_serial) > 0")
@Check(name = "ck_exchange_an_exchange_is_signed", constraints = "length(performed_by) > 0")
@Check(name = "ck_exchange_an_exchange_names_the_shipment", constraints = "length(shipment) > 0")
@Check(
        name = "ck_exchange_an_exchange_moved_something",
        constraints = "removed_serial IS NOT NULL OR installed_serial IS NOT NULL")
@Check(
        name = "ck_exchange_an_exchange_is_not_the_same_source_twice",
        constraints = "removed_serial IS NULL OR removed_serial <> installed_serial")
public class ExchangeRow extends Keyed {

    @Column(name = "device_serial", length = 40, nullable = false)
    private String deviceSerial;

    @Column(name = "removed_serial", length = 40)
    private String removedSerial;

    @Column(name = "installed_serial", length = 40)
    private String installedSerial;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "performed_by", length = 20, nullable = false)
    private String performedBy;

    @Column(name = "shipment", length = 60, nullable = false)
    private String shipment;

    @Column(name = "notes", length = 400, nullable = false)
    private String notes = "";

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public Optional<String> getRemovedSerial() {
        return Optional.ofNullable(removedSerial);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setRemovedSerial(String removedSerial) {
        this.removedSerial = removedSerial;
    }

    public Optional<String> getInstalledSerial() {
        return Optional.ofNullable(installedSerial);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setInstalledSerial(String installedSerial) {
        this.installedSerial = installedSerial;
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

    public String getShipment() {
        return shipment;
    }

    public void setShipment(String shipment) {
        this.shipment = shipment;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
