package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A row with one column of each value type and nothing else, which the value column tests write and
 * read back. It is not part of the schema: only those tests map it.
 */
@Entity
@Table(name = "sample")
class SampleRow {

    @Id
    @Column(name = "id")
    Integer id;

    @Convert(converter = LengthColumn.class)
    @Column(name = "wall")
    Length wall;

    @Convert(converter = ActivityColumn.class)
    @Column(name = "load")
    Activity load;

    @Convert(converter = DurationColumn.class)
    @Column(name = "shot")
    Duration shot;

    @Convert(converter = DoseColumn.class)
    @Column(name = "taken")
    Dose taken;

    @Convert(converter = DoseRateColumn.class)
    @Column(name = "rate")
    DoseRate rate;

    @Convert(converter = DecimalColumn.class)
    @Column(name = "density", columnDefinition = "character varying")
    BigDecimal density;
}
