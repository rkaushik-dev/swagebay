package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One thing wrong, on one item, on one inspection.
 */
@Entity
@Table(
        name = "defect",
        indexes = {
            @Index(name = "ix_defect_inspection_id", columnList = "inspection_id"),
            @Index(name = "ix_defect_item", columnList = "item_key")
        })
@Check(name = "ck_defect_a_defect_says_what_is_wrong", constraints = "length(account) > 0")
public class DefectRow extends Keyed {

    @Column(name = "item_key", length = 30, nullable = false)
    private String itemKey;

    @Column(name = "account", length = 400, nullable = false)
    private String account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "inspection_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_defect_inspection_id_inspection"))
    private InspectionRow inspection;

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<InspectionRow> getInspection() {
        return Optional.ofNullable(inspection);
    }

    void setInspection(InspectionRow inspection) {
        this.inspection = inspection;
    }
}
