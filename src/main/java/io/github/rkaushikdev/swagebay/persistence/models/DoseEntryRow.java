package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DoseColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

/**
 * One dose taken by one person, appended and never corrected in place.
 *
 * <p>The dose table is an append only ledger of entries rather than a running total per person, and
 * the running total is a sum over it. That is slower and it is the only defensible shape. A running
 * total column is a number somebody can correct, and a corrected dose total with no entry behind
 * the correction is exactly what an inspector will ask about.
 */
@Entity
@Table(
        name = "dose_entry",
        indexes = {@Index(name = "ix_dose_entry_badge_year", columnList = "badge, year")})
@Check(name = "ck_dose_entry_dose_not_negative", constraints = "taken >= 0")
public class DoseEntryRow extends Keyed {

    @Column(name = "badge", length = 20, nullable = false)
    private String badge;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Convert(converter = DoseColumn.class)
    @Column(name = "taken", nullable = false)
    private Dose taken;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "source_of_entry", length = 40, nullable = false)
    private String sourceOfEntry;

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Dose getTaken() {
        return taken;
    }

    public void setTaken(Dose taken) {
        this.taken = taken;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getSourceOfEntry() {
        return sourceOfEntry;
    }

    public void setSourceOfEntry(String sourceOfEntry) {
        this.sourceOfEntry = sourceOfEntry;
    }
}
