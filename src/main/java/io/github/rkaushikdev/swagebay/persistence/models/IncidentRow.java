package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One abnormal occurrence and what is owed because of it.
 *
 * <p>Incidents, calibrations, the film archive, film stock, control strips and welder
 * qualifications all look alike: a key, a few columns, a date, and a query that asks what is
 * overdue. It is tempting to put them in one table with a kind column and a blob of attributes, and
 * it would be wrong. Each one has its own constraints. A calibration certificate expires after it
 * was issued. A lot expires after it arrived. A qualification has a coupon thickness that has to be
 * positive. A single table with a kind column can express none of those, so every one of them would
 * move from the schema into the code, where nothing enforces it on the way in. Small tables with
 * real constraints beat one wide one with none.
 */
@Entity
@Table(
        name = "incident",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_incident_reference",
                    columnNames = {"reference"})
        },
        indexes = {@Index(name = "ix_incident_discovered_at", columnList = "discovered_at")})
@Check(name = "ck_incident_reported_after_found", constraints = "notified_at IS NULL OR notified_at >= discovered_at")
public class IncidentRow extends Keyed {

    @Column(name = "reference", length = 40, nullable = false)
    private String reference;

    @Column(name = "occurrence", length = 60, nullable = false)
    private String occurrence;

    @Column(name = "discovered_at", nullable = false)
    private LocalDateTime discoveredAt;

    @Column(name = "site", length = 120, nullable = false)
    private String site;

    @Column(name = "reported_by", length = 40, nullable = false)
    private String reportedBy;

    @Column(name = "account", length = 2000, nullable = false)
    private String account;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getOccurrence() {
        return occurrence;
    }

    public void setOccurrence(String occurrence) {
        this.occurrence = occurrence;
    }

    public LocalDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(LocalDateTime discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Optional<LocalDateTime> getNotifiedAt() {
        return Optional.ofNullable(notifiedAt);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }
}
