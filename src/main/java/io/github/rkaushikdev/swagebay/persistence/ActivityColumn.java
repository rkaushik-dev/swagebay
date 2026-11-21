package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import jakarta.persistence.AttributeConverter;

/** An activity, as whole becquerels. Absent stays absent in both directions. */
public final class ActivityColumn implements AttributeConverter<Activity, Long> {

    @Override
    public Long convertToDatabaseColumn(Activity value) {
        return value == null ? null : value.bq();
    }

    @Override
    public Activity convertToEntityAttribute(Long value) {
        return value == null ? null : new Activity(value);
    }
}
