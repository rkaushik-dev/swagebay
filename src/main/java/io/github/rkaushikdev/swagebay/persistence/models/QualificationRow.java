package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One welder, one process, one test weld, whose coupon has a thickness.
 */
@Entity
@Table(
        name = "qualification",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_qualification_welder",
                    columnNames = {"welder", "process", "qualified_on"})
        },
        indexes = {@Index(name = "ix_qualification_welder", columnList = "welder")})
@Check(name = "ck_qualification_coupon_positive", constraints = "coupon > 0")
public class QualificationRow extends Keyed {

    @Column(name = "welder", length = 40, nullable = false)
    private String welder;

    @Column(name = "process", length = 20, nullable = false)
    private String process;

    @Column(name = "position", length = 10, nullable = false)
    private String position;

    @Convert(converter = LengthColumn.class)
    @Column(name = "coupon", nullable = false)
    private Length coupon;

    @Column(name = "material_group", length = 20, nullable = false)
    private String materialGroup;

    @Column(name = "qualified_on", nullable = false)
    private LocalDate qualifiedOn;

    @Column(name = "last_used_on", nullable = false)
    private LocalDate lastUsedOn;

    public String getWelder() {
        return welder;
    }

    public void setWelder(String welder) {
        this.welder = welder;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Length getCoupon() {
        return coupon;
    }

    public void setCoupon(Length coupon) {
        this.coupon = coupon;
    }

    public String getMaterialGroup() {
        return materialGroup;
    }

    public void setMaterialGroup(String materialGroup) {
        this.materialGroup = materialGroup;
    }

    public LocalDate getQualifiedOn() {
        return qualifiedOn;
    }

    public void setQualifiedOn(LocalDate qualifiedOn) {
        this.qualifiedOn = qualifiedOn;
    }

    public LocalDate getLastUsedOn() {
        return lastUsedOn;
    }

    public void setLastUsedOn(LocalDate lastUsedOn) {
        this.lastUsedOn = lastUsedOn;
    }
}
