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
 * One processor control strip: one a processor a day, with believable readings.
 */
@Entity
@Table(
        name = "control_strip",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_control_strip_processor",
                    columnNames = {"processor", "read_on"})
        })
@Check(name = "ck_control_strip_readings_positive", constraints = "speed > 0 AND contrast > 0 AND fog > 0")
public class ControlStripRow extends Keyed {

    @Column(name = "processor", length = 40, nullable = false)
    private String processor;

    @Column(name = "read_on", nullable = false)
    private LocalDate readOn;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "speed", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal speed;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "contrast", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal contrast;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "fog", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal fog;

    @Column(name = "operator", length = 40, nullable = false)
    private String operator;

    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }

    public LocalDate getReadOn() {
        return readOn;
    }

    public void setReadOn(LocalDate readOn) {
        this.readOn = readOn;
    }

    public BigDecimal getSpeed() {
        return speed;
    }

    public void setSpeed(BigDecimal speed) {
        this.speed = speed;
    }

    public BigDecimal getContrast() {
        return contrast;
    }

    public void setContrast(BigDecimal contrast) {
        this.contrast = contrast;
    }

    public BigDecimal getFog() {
        return fog;
    }

    public void setFog(BigDecimal fog) {
        this.fog = fog;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
