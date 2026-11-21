package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DecimalColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One wipe test against one source. What came off the wipe is never negative.
 */
@Entity
@Table(name = "leak_test")
@Check(name = "ck_leak_test_removed_not_negative", constraints = "becquerels_removed >= 0")
public class LeakTestRow extends Keyed {

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "becquerels_removed", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal becquerelsRemoved;

    @Column(name = "technician", length = 40, nullable = false)
    private String technician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, foreignKey = @ForeignKey(name = "fk_leak_test_source_id_source"))
    private SourceRow source;

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public BigDecimal getBecquerelsRemoved() {
        return becquerelsRemoved;
    }

    public void setBecquerelsRemoved(BigDecimal becquerelsRemoved) {
        this.becquerelsRemoved = becquerelsRemoved;
    }

    public String getTechnician() {
        return technician;
    }

    public void setTechnician(String technician) {
        this.technician = technician;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<SourceRow> getSource() {
        return Optional.ofNullable(source);
    }

    void setSource(SourceRow source) {
        this.source = source;
    }
}
