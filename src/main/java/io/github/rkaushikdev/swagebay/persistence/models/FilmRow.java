package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DecimalColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * A film system and its speed, as of a revision. Append only, like every reference table: see
 * {@link MaterialRow}.
 */
@Entity
@Table(
        name = "film",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_film_key",
                    columnNames = {"key", "revision"})
        })
@Check(name = "ck_film_speed_positive", constraints = "speed_index > 0")
public class FilmRow extends Keyed {

    @Column(name = "key", length = 20, nullable = false)
    private String key;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "manufacturer", length = 80, nullable = false)
    private String manufacturer;

    @Column(name = "film_class", length = 10, nullable = false)
    private String filmClass;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "speed_index", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal speedIndex;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "granularity", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal granularity;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getFilmClass() {
        return filmClass;
    }

    public void setFilmClass(String filmClass) {
        this.filmClass = filmClass;
    }

    public BigDecimal getSpeedIndex() {
        return speedIndex;
    }

    public void setSpeedIndex(BigDecimal speedIndex) {
        this.speedIndex = speedIndex;
    }

    public BigDecimal getGranularity() {
        return granularity;
    }

    public void setGranularity(BigDecimal granularity) {
        this.granularity = granularity;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public void setEffectiveOn(LocalDate effectiveOn) {
        this.effectiveOn = effectiveOn;
    }
}
