package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One source, out in one device, on one job. It comes back on or after the day it went out, or it
 * has not come back yet.
 */
@Entity
@Table(
        name = "trip",
        indexes = {@Index(name = "ix_trip_source_id_came_back", columnList = "source_id, came_back")})
@Check(name = "ck_trip_exposures_not_negative", constraints = "exposures >= 0")
@Check(name = "ck_trip_return_after_departure", constraints = "came_back IS NULL OR came_back >= went_out")
public class TripRow extends Keyed {

    @Column(name = "crew_badge", length = 20, nullable = false)
    private String crewBadge;

    @Column(name = "site", length = 120, nullable = false)
    private String site;

    @Column(name = "went_out", nullable = false)
    private LocalDate wentOut;

    @Column(name = "came_back")
    private LocalDate cameBack;

    @Column(name = "exposures", nullable = false)
    private int exposures;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_source_id_source"))
    private SourceRow source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_device_id_device"))
    private DeviceRow device;

    public String getCrewBadge() {
        return crewBadge;
    }

    public void setCrewBadge(String crewBadge) {
        this.crewBadge = crewBadge;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public LocalDate getWentOut() {
        return wentOut;
    }

    public void setWentOut(LocalDate wentOut) {
        this.wentOut = wentOut;
    }

    public Optional<LocalDate> getCameBack() {
        return Optional.ofNullable(cameBack);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setCameBack(LocalDate cameBack) {
        this.cameBack = cameBack;
    }

    public int getExposures() {
        return exposures;
    }

    public void setExposures(int exposures) {
        this.exposures = exposures;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<SourceRow> getSource() {
        return Optional.ofNullable(source);
    }

    public void setSource(SourceRow source) {
        this.source = source;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<DeviceRow> getDevice() {
        return Optional.ofNullable(device);
    }

    public void setDevice(DeviceRow device) {
        this.device = device;
    }
}
