package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.AttributeConverter;

/**
 * A length, as whole micrometres.
 *
 * <p>The column holds a plain integer and the row holds a {@link Length}, so the unit never lives
 * in a column name and nothing can write a bare number where a length belongs. Absent stays absent
 * in both directions: a null is never read back as zero.
 */
public final class LengthColumn implements AttributeConverter<Length, Long> {

    @Override
    public Long convertToDatabaseColumn(Length value) {
        return value == null ? null : value.um();
    }

    @Override
    public Length convertToEntityAttribute(Long value) {
        return value == null ? null : new Length(value);
    }
}
