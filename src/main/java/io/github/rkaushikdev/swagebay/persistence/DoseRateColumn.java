package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import jakarta.persistence.AttributeConverter;

/** A dose rate, as whole nanosieverts an hour. Absent stays absent in both directions. */
public final class DoseRateColumn implements AttributeConverter<DoseRate, Long> {

    @Override
    public Long convertToDatabaseColumn(DoseRate value) {
        return value == null ? null : value.nsvPerHour();
    }

    @Override
    public DoseRate convertToEntityAttribute(Long value) {
        return value == null ? null : new DoseRate(value);
    }
}
