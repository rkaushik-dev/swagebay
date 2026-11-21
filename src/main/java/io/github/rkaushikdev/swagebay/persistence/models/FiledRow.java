package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One radiograph in the filing cabinet. Where it is may not be known yet: the site, cabinet and box
 * are filled in when it is put away.
 */
@Entity
@Table(
        name = "filed",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_filed_radiograph",
                    columnNames = {"radiograph"})
        },
        indexes = {@Index(name = "ix_filed_job", columnList = "job")})
public class FiledRow extends Keyed {

    @Column(name = "radiograph", length = 40, nullable = false)
    private String radiograph;

    @Column(name = "job", length = 20, nullable = false)
    private String job;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "retention", length = 40, nullable = false)
    private String retention;

    @Column(name = "site", length = 120)
    private String site;

    @Column(name = "cabinet", length = 40)
    private String cabinet;

    @Column(name = "box", length = 40)
    private String box;

    public String getRadiograph() {
        return radiograph;
    }

    public void setRadiograph(String radiograph) {
        this.radiograph = radiograph;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public LocalDate getFiledOn() {
        return filedOn;
    }

    public void setFiledOn(LocalDate filedOn) {
        this.filedOn = filedOn;
    }

    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    public Optional<String> getSite() {
        return Optional.ofNullable(site);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setSite(String site) {
        this.site = site;
    }

    public Optional<String> getCabinet() {
        return Optional.ofNullable(cabinet);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setCabinet(String cabinet) {
        this.cabinet = cabinet;
    }

    public Optional<String> getBox() {
        return Optional.ofNullable(box);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setBox(String box) {
        this.box = box;
    }
}
