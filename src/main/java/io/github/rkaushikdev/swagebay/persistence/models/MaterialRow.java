package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * A material and its density, as of a revision.
 *
 * <p>The reference tables look redundant: the catalogues are already in the code. They are here
 * because a record has to keep pointing at what it pointed at. A technique sheet from two years ago
 * used an equivalence factor that has since been revised, and the sheet is only defensible if the
 * factor it actually used can be recovered. The code carries today's values; these tables carry the
 * values as of the row that references them, and the row references a revision.
 *
 * <p>So the reference tables are append only. A revised entry is a new row with a later effective
 * date, never an update in place, and the constraint that makes that stick is the unique key on key
 * plus revision rather than on key alone.
 */
@Entity
@Table(
        name = "material",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_material_key",
                    columnNames = {"key", "revision"})
        })
@Check(name = "ck_material_revision_not_negative", constraints = "revision >= 0")
@Check(name = "ck_material_density_positive", constraints = "density_kg_m3 > 0")
public class MaterialRow extends Keyed {

    @Column(name = "key", length = 40, nullable = false)
    private String key;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "density_kg_m3", nullable = false)
    private Integer densityKgM3;

    @Column(name = "ferrous", nullable = false)
    private boolean ferrous;

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

    public int getDensityKgM3() {
        return densityKgM3;
    }

    public void setDensityKgM3(int densityKgM3) {
        this.densityKgM3 = densityKgM3;
    }

    public boolean isFerrous() {
        return ferrous;
    }

    public void setFerrous(boolean ferrous) {
        this.ferrous = ferrous;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public void setEffectiveOn(LocalDate effectiveOn) {
        this.effectiveOn = effectiveOn;
    }
}
