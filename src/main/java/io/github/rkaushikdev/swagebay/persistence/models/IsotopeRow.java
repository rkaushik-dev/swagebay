package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.DecimalColumn;
import io.github.rkaushikdev.swagebay.persistence.Keyed;
import io.github.rkaushikdev.swagebay.persistence.LengthColumn;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * An isotope and the numbers planning needs, as of a revision. Append only, like every reference
 * table: see {@link MaterialRow}.
 */
@Entity
@Table(
        name = "isotope",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_isotope_key",
                    columnNames = {"key", "revision"})
        })
@Check(name = "ck_isotope_half_life_positive", constraints = "half_life_days > 0")
@Check(name = "ck_isotope_energy_band_ordered", constraints = "energy_kev_low <= energy_kev_high")
public class IsotopeRow extends Keyed {

    @Column(name = "key", length = 20, nullable = false)
    private String key;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "half_life_days", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal halfLifeDays;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "gamma_constant", nullable = false, columnDefinition = Columns.TEXT)
    private BigDecimal gammaConstant;

    @Column(name = "energy_kev_low", nullable = false)
    private Integer energyKevLow;

    @Column(name = "energy_kev_high", nullable = false)
    private Integer energyKevHigh;

    @Convert(converter = LengthColumn.class)
    @Column(name = "steel_min", nullable = false)
    private Length steelMin;

    @Convert(converter = LengthColumn.class)
    @Column(name = "steel_max", nullable = false)
    private Length steelMax;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getHalfLifeDays() {
        return halfLifeDays;
    }

    public void setHalfLifeDays(BigDecimal halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public BigDecimal getGammaConstant() {
        return gammaConstant;
    }

    public void setGammaConstant(BigDecimal gammaConstant) {
        this.gammaConstant = gammaConstant;
    }

    public int getEnergyKevLow() {
        return energyKevLow;
    }

    public void setEnergyKevLow(int energyKevLow) {
        this.energyKevLow = energyKevLow;
    }

    public int getEnergyKevHigh() {
        return energyKevHigh;
    }

    public void setEnergyKevHigh(int energyKevHigh) {
        this.energyKevHigh = energyKevHigh;
    }

    public Length getSteelMin() {
        return steelMin;
    }

    public void setSteelMin(Length steelMin) {
        this.steelMin = steelMin;
    }

    public Length getSteelMax() {
        return steelMax;
    }

    public void setSteelMax(Length steelMax) {
        this.steelMax = steelMax;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public void setEffectiveOn(LocalDate effectiveOn) {
        this.effectiveOn = effectiveOn;
    }
}
