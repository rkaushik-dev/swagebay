package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

/**
 * One appended entry. Nothing rewrites one of these.
 *
 * <p>The audit table is the one table nothing updates. Every other row here can be corrected; this
 * one cannot, and the schema says so rather than the code, because a rule enforced only in the code
 * is a rule that lasts until somebody opens a database console.
 *
 * <p>There is no updated-at column, no revision and no soft delete flag. A mistaken entry is
 * followed by a correcting entry and both stay, which is what a trail means. The index is on the
 * record it describes rather than on the time, because the question asked of it is always about one
 * record and never about an hour.
 */
@Entity
@Table(
        name = "audit",
        indexes = {
            @Index(name = "ix_audit_record", columnList = "record, identity, at"),
            @Index(name = "ix_audit_actor", columnList = "actor, at")
        })
@Check(name = "ck_audit_audit_names_an_actor", constraints = "length(actor) > 0")
@Check(name = "ck_audit_audit_names_a_record", constraints = "length(record) > 0")
public class AuditRow extends Keyed {

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    @Column(name = "actor", length = 20, nullable = false)
    private String actor;

    @Column(name = "action", length = 12, nullable = false)
    private String action;

    @Column(name = "record", length = 40, nullable = false)
    private String record;

    @Column(name = "identity", length = 60, nullable = false)
    private String identity;

    @Column(name = "field_name", length = 40, nullable = false)
    private String fieldName = "";

    @Column(name = "was", nullable = false, columnDefinition = Columns.TEXT)
    private String was = "";

    @Column(name = "became", nullable = false, columnDefinition = Columns.TEXT)
    private String became = "";

    @Column(name = "reason", length = 200, nullable = false)
    private String reason = "";

    public LocalDateTime getAt() {
        return at;
    }

    public void setAt(LocalDateTime at) {
        this.at = at;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getWas() {
        return was;
    }

    public void setWas(String was) {
        this.was = was;
    }

    public String getBecame() {
        return became;
    }

    public void setBecame(String became) {
        this.became = became;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
