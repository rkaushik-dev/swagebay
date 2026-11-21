package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One non conformance report, from raised to closed.
 *
 * <p>The reference is the natural key and it is unique across the whole register rather than per
 * job, because that is how it is written on the paper and how the client refers to it in an email.
 * A per job uniqueness would let two NCR-1s exist, and the first time somebody forwarded one
 * without the job number the whole thread would be about the wrong weld.
 *
 * <p>The disposition may be absent and that is deliberate. A report exists from the moment somebody
 * raises it, and the gap between raising and deciding is the thing the register is watching. A
 * default of rework would close that gap by lying about it.
 */
@Entity
@Table(
        name = "nonconformance",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_nonconformance_reference",
                    columnNames = {"reference"})
        },
        indexes = {
            @Index(name = "ix_nonconformance_job", columnList = "job, raised_on"),
            @Index(name = "ix_nonconformance_open", columnList = "closed_on")
        })
@Check(name = "ck_nonconformance_a_report_has_a_reference", constraints = "length(reference) > 0")
@Check(name = "ck_nonconformance_a_report_is_raised_by_somebody", constraints = "length(raised_by) > 0")
@Check(
        name = "ck_nonconformance_a_report_closes_after_it_opens",
        constraints = "closed_on IS NULL OR closed_on >= raised_on")
@Check(
        name = "ck_nonconformance_a_closed_report_has_been_decided",
        constraints = "closed_on IS NULL OR disposition IS NOT NULL")
@Check(
        name = "ck_nonconformance_a_report_is_not_closed_by_its_author",
        constraints = "closed_by IS NULL OR closed_by <> raised_by")
public class NonconformanceRow extends Keyed {

    @Column(name = "reference", length = 40, nullable = false)
    private String reference;

    @Column(name = "job", length = 20, nullable = false)
    private String job;

    @Column(name = "item", length = 60, nullable = false)
    private String item;

    @Column(name = "requirement", length = 120, nullable = false)
    private String requirement;

    @Column(name = "account", nullable = false, columnDefinition = Columns.TEXT)
    private String account;

    @Column(name = "raised_on", nullable = false)
    private LocalDate raisedOn;

    @Column(name = "raised_by", length = 20, nullable = false)
    private String raisedBy;

    @Column(name = "disposition", length = 20)
    private String disposition;

    @Column(name = "concession", length = 60, nullable = false)
    private String concession = "";

    @Column(name = "removal", length = 60, nullable = false)
    private String removal = "";

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "closed_by", length = 20)
    private String closedBy;

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public LocalDate getRaisedOn() {
        return raisedOn;
    }

    public void setRaisedOn(LocalDate raisedOn) {
        this.raisedOn = raisedOn;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public void setRaisedBy(String raisedBy) {
        this.raisedBy = raisedBy;
    }

    public Optional<String> getDisposition() {
        return Optional.ofNullable(disposition);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getConcession() {
        return concession;
    }

    public void setConcession(String concession) {
        this.concession = concession;
    }

    public String getRemoval() {
        return removal;
    }

    public void setRemoval(String removal) {
        this.removal = removal;
    }

    public Optional<LocalDate> getClosedOn() {
        return Optional.ofNullable(closedOn);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public Optional<String> getClosedBy() {
        return Optional.ofNullable(closedBy);
    }

    /** Sets the value, or clears it with {@code null}. */
    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }
}
