package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/**
 * One counter, for one kind of number in one scope: the table behind every number that has to be
 * gapless.
 *
 * <p>A radiograph number cannot have holes in it. A client asked to accept a report package with
 * radiographs R-0041 through R-0043 and no R-0042 will ask what happened to R-0042, and the true
 * answer, that a database sequence handed the number out and then the transaction rolled back, is
 * not an answer anybody accepts. A missing radiograph is a missing radiograph.
 *
 * <p>So the counter is a row, not a sequence. Taking a number is an update inside the same
 * transaction as the work it numbers, and if the work rolls back the number rolls back with it.
 * That serialises number issuing, which is the cost, and it is the right cost for something a shop
 * issues a few hundred of a week.
 *
 * <p>The counter is per scope so that two jobs can number independently, and the scope is part of
 * the key rather than of the value, which means a scope that has never been used has no row rather
 * than a row holding zero.
 */
@Entity
@Table(
        name = "counter",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_counter_name",
                    columnNames = {"name", "scope"})
        })
@Check(name = "ck_counter_counter_starts_at_one", constraints = "next_value >= 1")
public class CounterRow extends Keyed {

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Column(name = "scope", length = 40, nullable = false)
    private String scope = "";

    @Column(name = "next_value", nullable = false)
    private int nextValue = 1;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public int getNextValue() {
        return nextValue;
    }

    public void setNextValue(int nextValue) {
        this.nextValue = nextValue;
    }
}
