package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.Dose;
import jakarta.persistence.AttributeConverter;

/** A dose, as whole nanosieverts. Absent stays absent in both directions. */
public final class DoseColumn implements AttributeConverter<Dose, Long> {

    @Override
    public Long convertToDatabaseColumn(Dose value) {
        return value == null ? null : value.nsv();
    }

    @Override
    public Dose convertToEntityAttribute(Long value) {
        return value == null ? null : new Dose(value);
    }
}
