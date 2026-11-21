package io.github.rkaushikdev.swagebay.persistence;

import jakarta.persistence.AttributeConverter;
import java.math.BigDecimal;

/**
 * A decimal, as its text, because a floating point column would round the last digit off.
 *
 * <p>The text is exactly what the decimal prints, scale included, so a density read as {@code
 * 2.50} comes back as {@code 2.50} and not as {@code 2.5}. Text sorts wrongly in raw SQL, which is
 * the right trade because nothing sorts on a density. A check constraint that compares one of these
 * columns with a number still compares numbers: the database reads the text as a decimal to do it.
 */
public final class DecimalColumn implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toString();
    }

    @Override
    public BigDecimal convertToEntityAttribute(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
