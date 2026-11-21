package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One instrument issued to one person. A badge needs no calibration date; the instruments that read
 * directly do.
 */
@Entity
@Table(
        name = "dosimeter",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_dosimeter_serial",
                    columnNames = {"serial"})
        })
@Check(name = "ck_dosimeter_kind_known", constraints = "kind IN ('badge', 'direct reading', 'alarming ratemeter')")
public class DosimeterRow extends Keyed {

    @Column(name = "serial", length = 40, nullable = false)
    private String serial;

    @Column(name = "kind", length = 30, nullable = false)
    private String kind;

    @Column(name = "badge", length = 20, nullable = false)
    private String badge;

    @Column(name = "worker_class", length = 30, nullable = false)
    private String workerClass;

    @Column(name = "calibration_due")
    private LocalDate calibrationDue;

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public String getWorkerClass() {
        return workerClass;
    }

    public void setWorkerClass(String workerClass) {
        this.workerClass = workerClass;
    }

    public Optional<LocalDate> getCalibrationDue() {
        return Optional.ofNullable(calibrationDue);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setCalibrationDue(LocalDate calibrationDue) {
        this.calibrationDue = calibrationDue;
    }
}
