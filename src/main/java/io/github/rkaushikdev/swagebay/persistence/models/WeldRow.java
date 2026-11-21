package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.Length;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One weld, or one repair round of one weld, and the radiographs shot of it.
 *
 * <p>Repair rounds live on the weld row rather than in a separate table. A repair is a round of the
 * same weld, so it is the same identity with a different round number, and the unique key is on the
 * job, the prefix, the number and the round together. Filing a repair as a new weld is exactly the
 * thing that has to be made impossible, and a unique key is the way to make it impossible rather
 * than merely discouraged.
 */
@Entity
@Table(
        name = "weld",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_weld_job_id",
                    columnNames = {"job_id", "prefix", "number", "repair_round"})
        },
        indexes = {@Index(name = "ix_weld_job_id", columnList = "job_id")})
@Check(name = "ck_weld_repair_round_not_negative", constraints = "repair_round >= 0")
@Check(name = "ck_weld_wall_positive", constraints = "wall > 0")
@Check(name = "ck_weld_weld_length_positive", constraints = "weld_length > 0")
public class WeldRow extends Keyed {

    @Column(name = "prefix", length = 4, nullable = false)
    private String prefix;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "repair_round", nullable = false)
    private int repairRound;

    @Column(name = "material_key", length = 40, nullable = false)
    private String materialKey;

    @Convert(converter = LengthColumn.class)
    @Column(name = "outside_diameter")
    private Length outsideDiameter;

    @Convert(converter = LengthColumn.class)
    @Column(name = "wall", nullable = false)
    private Length wall;

    @Convert(converter = LengthColumn.class)
    @Column(name = "reinforcement", nullable = false)
    private Length reinforcement;

    @Convert(converter = LengthColumn.class)
    @Column(name = "weld_length", nullable = false)
    private Length weldLength;

    @Column(name = "welder", length = 40, nullable = false)
    private String welder;

    @Column(name = "welded_on", nullable = false)
    private LocalDate weldedOn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_weld_job_id_job"))
    private JobRow job;

    @OneToMany(mappedBy = "weld", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<RadiographRow> radiographs = new ArrayList<>();

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getRepairRound() {
        return repairRound;
    }

    public void setRepairRound(int repairRound) {
        this.repairRound = repairRound;
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public void setMaterialKey(String materialKey) {
        this.materialKey = materialKey;
    }

    public Optional<Length> getOutsideDiameter() {
        return Optional.ofNullable(outsideDiameter);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setOutsideDiameter(Length outsideDiameter) {
        this.outsideDiameter = outsideDiameter;
    }

    public Length getWall() {
        return wall;
    }

    public void setWall(Length wall) {
        this.wall = wall;
    }

    public Length getReinforcement() {
        return reinforcement;
    }

    public void setReinforcement(Length reinforcement) {
        this.reinforcement = reinforcement;
    }

    public Length getWeldLength() {
        return weldLength;
    }

    public void setWeldLength(Length weldLength) {
        this.weldLength = weldLength;
    }

    public String getWelder() {
        return welder;
    }

    public void setWelder(String welder) {
        this.welder = welder;
    }

    public LocalDate getWeldedOn() {
        return weldedOn;
    }

    public void setWeldedOn(LocalDate weldedOn) {
        this.weldedOn = weldedOn;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<JobRow> getJob() {
        return Optional.ofNullable(job);
    }

    void setJob(JobRow job) {
        this.job = job;
    }

    public List<RadiographRow> getRadiographs() {
        return Collections.unmodifiableList(radiographs);
    }

    /** Hang {@code radiograph} off this row; it is written with this one. */
    public void addRadiograph(RadiographRow radiograph) {
        radiograph.setWeld(this);
        radiographs.add(radiograph);
    }
}
