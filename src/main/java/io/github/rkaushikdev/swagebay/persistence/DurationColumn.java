package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.Duration;
import jakarta.persistence.AttributeConverter;

/** A duration, as whole milliseconds. Absent stays absent in both directions. */
public final class DurationColumn implements AttributeConverter<Duration, Long> {

    @Override
    public Long convertToDatabaseColumn(Duration value) {
        return value == null ? null : value.ms();
    }

    @Override
    public Duration convertToEntityAttribute(Long value) {
        return value == null ? null : new Duration(value);
    }
}
