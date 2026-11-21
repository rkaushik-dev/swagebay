package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One calibration certificate against one instrument. An instrument holds as many certificates as
 * it has been calibrated, and each expires after it was issued.
 */
@Entity
@Table(
        name = "calibration",
        indexes = {@Index(name = "ix_calibration_serial_due", columnList = "serial, due_on")})
@Check(name = "ck_calibration_expires_after_issue", constraints = "due_on > calibrated_on")
public class CalibrationRow extends Keyed {

    @Column(name = "serial", length = 40, nullable = false)
    private String serial;

    @Column(name = "instrument_kind", length = 40, nullable = false)
    private String instrumentKind;

    @Column(name = "calibrated_on", nullable = false)
    private LocalDate calibratedOn;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "laboratory", length = 120, nullable = false)
    private String laboratory;

    @Column(name = "certificate_reference", length = 60, nullable = false)
    private String certificateReference;

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getInstrumentKind() {
        return instrumentKind;
    }

    public void setInstrumentKind(String instrumentKind) {
        this.instrumentKind = instrumentKind;
    }

    public LocalDate getCalibratedOn() {
        return calibratedOn;
    }

    public void setCalibratedOn(LocalDate calibratedOn) {
        this.calibratedOn = calibratedOn;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public void setDueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }

    public String getLaboratory() {
        return laboratory;
    }

    public void setLaboratory(String laboratory) {
        this.laboratory = laboratory;
    }

    public String getCertificateReference() {
        return certificateReference;
    }

    public void setCertificateReference(String certificateReference) {
        this.certificateReference = certificateReference;
    }
}
