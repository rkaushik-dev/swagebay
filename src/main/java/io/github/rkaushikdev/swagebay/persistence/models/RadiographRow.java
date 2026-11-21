package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DecimalColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One image, what it was shot to, who read it, and what was seen on it.
 *
 * <p>The day log asks for every exposure on a date across every job, and the unread listing asks
 * for the ones with no reader. Both scanned the whole table before their indexes existed, which was
 * fine at four hundred rows and was not at forty thousand.
 */
@Entity
@Table(
        name = "radiograph",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_radiograph_number",
                    columnNames = {"number"}),
            @UniqueConstraint(
                    name = "uq_radiograph_weld_id",
                    columnNames = {"weld_id", "exposure_index"})
        },
        indexes = {
            @Index(name = "ix_radiograph_exposed_at", columnList = "exposed_at"),
            @Index(name = "ix_radiograph_read_at", columnList = "read_at")
        })
@Check(name = "ck_radiograph_exposure_from_one", constraints = "exposure_index >= 1")
@Check(name = "ck_radiograph_reread_follows_a_reading", constraints = "reread_by IS NULL OR read_by IS NOT NULL")
public class RadiographRow extends Keyed {

    @Column(name = "number", length = 40, nullable = false)
    private String number;

    @Column(name = "exposure_index", nullable = false)
    private Integer exposureIndex;

    @Column(name = "exposed_at", nullable = false)
    private LocalDateTime exposedAt;

    @Column(name = "technique_reference", length = 80, nullable = false)
    private String techniqueReference;

    @Column(name = "medium", length = 40, nullable = false)
    private String medium;

    @Column(name = "admissible", nullable = false)
    private boolean admissible;

    @Column(name = "inadmissible_reason", length = 400)
    private String inadmissibleReason;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "density_through_indicator", columnDefinition = Columns.TEXT)
    private BigDecimal densityThroughIndicator;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "normalised_snr", columnDefinition = Columns.TEXT)
    private BigDecimal normalisedSnr;

    @Column(name = "read_by", length = 40)
    private String readBy;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * A reread is a second reading of the same film by a second person, and it is kept beside the
     * first rather than over it. Overwriting the reader would leave a report that names one reader
     * for a film two people read, which is the one fact a disputed reading turns on.
     */
    @Column(name = "reread_by", length = 40)
    private String rereadBy;

    @Column(name = "reread_at")
    private LocalDateTime rereadAt;

    @Column(name = "reread_reason", length = 200)
    private String rereadReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weld_id", nullable = false, foreignKey = @ForeignKey(name = "fk_radiograph_weld_id_weld"))
    private WeldRow weld;

    @OneToMany(mappedBy = "radiograph", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<IndicationRow> indications = new ArrayList<>();

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public int getExposureIndex() {
        return exposureIndex;
    }

    public void setExposureIndex(int exposureIndex) {
        this.exposureIndex = exposureIndex;
    }

    public LocalDateTime getExposedAt() {
        return exposedAt;
    }

    public void setExposedAt(LocalDateTime exposedAt) {
        this.exposedAt = exposedAt;
    }

    public String getTechniqueReference() {
        return techniqueReference;
    }

    public void setTechniqueReference(String techniqueReference) {
        this.techniqueReference = techniqueReference;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public boolean isAdmissible() {
        return admissible;
    }

    public void setAdmissible(boolean admissible) {
        this.admissible = admissible;
    }

    public Optional<String> getInadmissibleReason() {
        return Optional.ofNullable(inadmissibleReason);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setInadmissibleReason(String inadmissibleReason) {
        this.inadmissibleReason = inadmissibleReason;
    }

    public Optional<BigDecimal> getDensityThroughIndicator() {
        return Optional.ofNullable(densityThroughIndicator);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setDensityThroughIndicator(BigDecimal densityThroughIndicator) {
        this.densityThroughIndicator = densityThroughIndicator;
    }

    public Optional<BigDecimal> getNormalisedSnr() {
        return Optional.ofNullable(normalisedSnr);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setNormalisedSnr(BigDecimal normalisedSnr) {
        this.normalisedSnr = normalisedSnr;
    }

    public Optional<String> getReadBy() {
        return Optional.ofNullable(readBy);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setReadBy(String readBy) {
        this.readBy = readBy;
    }

    public Optional<LocalDateTime> getReadAt() {
        return Optional.ofNullable(readAt);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public Optional<String> getRereadBy() {
        return Optional.ofNullable(rereadBy);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setRereadBy(String rereadBy) {
        this.rereadBy = rereadBy;
    }

    public Optional<LocalDateTime> getRereadAt() {
        return Optional.ofNullable(rereadAt);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setRereadAt(LocalDateTime rereadAt) {
        this.rereadAt = rereadAt;
    }

    public Optional<String> getRereadReason() {
        return Optional.ofNullable(rereadReason);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setRereadReason(String rereadReason) {
        this.rereadReason = rereadReason;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<WeldRow> getWeld() {
        return Optional.ofNullable(weld);
    }

    void setWeld(WeldRow weld) {
        this.weld = weld;
    }

    public List<IndicationRow> getIndications() {
        return Collections.unmodifiableList(indications);
    }

    /** Hang {@code indication} off this row; it is written with this one. */
    public void addIndication(IndicationRow indication) {
        indication.setRadiograph(this);
        indications.add(indication);
    }
}
