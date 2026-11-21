package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One job, which is a client, a site and an acceptance code, and the welds shot on it.
 *
 * <p>The shape of the work tables follows the record rather than the convenience. A job holds
 * welds, a weld holds radiographs, a radiograph holds indications, and nothing skips a level. It is
 * tempting to hang indications off the weld, because that is where the acceptance rules look at
 * them, but an indication was seen on a particular piece of film by a particular person, and losing
 * that link makes the report undefendable.
 */
@Entity
@Table(
        name = "job",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_job_year",
                    columnNames = {"year", "serial"})
        })
@Check(name = "ck_job_serial_in_range", constraints = "serial BETWEEN 1 AND 9999")
@Check(name = "ck_job_year_two_digits", constraints = "year BETWEEN 0 AND 99")
public class JobRow extends Keyed {

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "serial", nullable = false)
    private Integer serial;

    @Column(name = "client", length = 120, nullable = false)
    private String client;

    @Column(name = "site", length = 120, nullable = false)
    private String site;

    @Column(name = "acceptance_code", length = 40, nullable = false)
    private String acceptanceCode;

    @Column(name = "procedure_number", length = 40, nullable = false)
    private String procedureNumber;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private final List<WeldRow> welds = new ArrayList<>();

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getSerial() {
        return serial;
    }

    public void setSerial(int serial) {
        this.serial = serial;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getAcceptanceCode() {
        return acceptanceCode;
    }

    public void setAcceptanceCode(String acceptanceCode) {
        this.acceptanceCode = acceptanceCode;
    }

    public String getProcedureNumber() {
        return procedureNumber;
    }

    public void setProcedureNumber(String procedureNumber) {
        this.procedureNumber = procedureNumber;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public Optional<LocalDate> getClosedOn() {
        return Optional.ofNullable(closedOn);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public List<WeldRow> getWelds() {
        return Collections.unmodifiableList(welds);
    }

    /** Hang {@code weld} off this row; it is written with this one. */
    public void addWeld(WeldRow weld) {
        weld.setJob(this);
        welds.add(weld);
    }
}
