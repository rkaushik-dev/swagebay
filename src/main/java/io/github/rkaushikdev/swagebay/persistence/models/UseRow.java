package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One device, on one day, at one site, with the crew that ran it.
 *
 * <p>The radiographers on a utilisation entry are a comma separated column rather than a join
 * table. A badge is looked up by exact match against a list of two or three, the list is written
 * once and read whole, and a join table here would be two rows per day per device that nothing ever
 * queries on its own.
 */
@Entity
@Table(
        name = "device_use",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_device_use_device_serial",
                    columnNames = {"device_serial", "used_on", "site"})
        },
        indexes = {
            @Index(name = "ix_device_use_device", columnList = "device_serial, used_on"),
            @Index(name = "ix_device_use_source", columnList = "source_serial, used_on"),
            @Index(name = "ix_device_use_day", columnList = "used_on")
        })
@Check(name = "ck_device_use_a_use_names_somebody", constraints = "length(radiographers) > 0")
@Check(name = "ck_device_use_a_use_names_where", constraints = "length(site) > 0")
@Check(name = "ck_device_use_exposures_do_not_go_backwards", constraints = "exposures >= 0")
public class UseRow extends Keyed {

    @Column(name = "device_serial", length = 40, nullable = false)
    private String deviceSerial;

    @Column(name = "source_serial", length = 40, nullable = false)
    private String sourceSerial;

    @Column(name = "used_on", nullable = false)
    private LocalDate usedOn;

    @Column(name = "site", length = 120, nullable = false)
    private String site;

    @Column(name = "radiographers", nullable = false, columnDefinition = Columns.TEXT)
    private String radiographers;

    @Column(name = "exposures", nullable = false)
    private int exposures;

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public String getSourceSerial() {
        return sourceSerial;
    }

    public void setSourceSerial(String sourceSerial) {
        this.sourceSerial = sourceSerial;
    }

    public LocalDate getUsedOn() {
        return usedOn;
    }

    public void setUsedOn(LocalDate usedOn) {
        this.usedOn = usedOn;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getRadiographers() {
        return radiographers;
    }

    public void setRadiographers(String radiographers) {
        this.radiographers = radiographers;
    }

    public int getExposures() {
        return exposures;
    }

    public void setExposures(int exposures) {
        this.exposures = exposures;
    }
}
