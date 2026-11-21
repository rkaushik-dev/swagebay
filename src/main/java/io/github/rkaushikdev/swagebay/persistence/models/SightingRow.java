package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Somebody laid eyes on a source and signed for it.
 */
@Entity
@Table(name = "sighting")
public class SightingRow extends Keyed {

    @Column(name = "seen_on", nullable = false)
    private LocalDate seenOn;

    @Column(name = "seen_by", length = 40, nullable = false)
    private String seenBy;

    @Column(name = "location", length = 120, nullable = false)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sighting_source_id_source"))
    private SourceRow source;

    public LocalDate getSeenOn() {
        return seenOn;
    }

    public void setSeenOn(LocalDate seenOn) {
        this.seenOn = seenOn;
    }

    public String getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(String seenBy) {
        this.seenBy = seenBy;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<SourceRow> getSource() {
        return Optional.ofNullable(source);
    }

    void setSource(SourceRow source) {
        this.source = source;
    }
}
