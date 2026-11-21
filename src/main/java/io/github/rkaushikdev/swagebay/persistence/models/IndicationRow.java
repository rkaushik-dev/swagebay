package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DecimalColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One discontinuity, on one piece of film.
 */
@Entity
@Table(
        name = "indication",
        indexes = {@Index(name = "ix_indication_radiograph_id", columnList = "radiograph_id")})
@Check(name = "ck_indication_starts_on_the_weld", constraints = "starts_at >= 0")
@Check(name = "ck_indication_length_not_negative", constraints = "length >= 0")
@Check(name = "ck_indication_width_not_negative", constraints = "width >= 0")
public class IndicationRow extends Keyed {

    @Column(name = "kind", length = 8, nullable = false)
    private String kind;

    @Convert(converter = LengthColumn.class)
    @Column(name = "starts_at", nullable = false)
    private Length startsAt;

    @Convert(converter = LengthColumn.class)
    @Column(name = "length", nullable = false)
    private Length length;

    @Convert(converter = LengthColumn.class)
    @Column(name = "width", nullable = false)
    private Length width;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "depth_fraction", columnDefinition = Columns.TEXT)
    private BigDecimal depthFraction;

    @Column(name = "clock_minutes")
    private Integer clockMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "radiograph_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_indication_radiograph_id_radiograph"))
    private RadiographRow radiograph;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Length getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Length startsAt) {
        this.startsAt = startsAt;
    }

    public Length getLength() {
        return length;
    }

    public void setLength(Length length) {
        this.length = length;
    }

    public Length getWidth() {
        return width;
    }

    public void setWidth(Length width) {
        this.width = width;
    }

    public Optional<BigDecimal> getDepthFraction() {
        return Optional.ofNullable(depthFraction);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setDepthFraction(BigDecimal depthFraction) {
        this.depthFraction = depthFraction;
    }

    public Optional<Integer> getClockMinutes() {
        return Optional.ofNullable(clockMinutes);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setClockMinutes(Integer clockMinutes) {
        this.clockMinutes = clockMinutes;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<RadiographRow> getRadiograph() {
        return Optional.ofNullable(radiograph);
    }

    void setRadiograph(RadiographRow radiograph) {
        this.radiograph = radiograph;
    }
}
