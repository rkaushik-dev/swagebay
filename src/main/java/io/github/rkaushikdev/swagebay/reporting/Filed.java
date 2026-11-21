package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One radiograph, filed.
 *
 * @param radiograph the radiograph number
 * @param job the job it was shot on
 * @param filedOn the day it went in the cabinet
 * @param retention how long it is kept
 * @param location where it is, absent when nobody recorded it
 */
public record Filed(
        String radiograph, String job, LocalDate filedOn, Retention retention, Optional<Location> location) {

    /**
     * A filed radiograph.
     *
     * @throws ValidationError for a blank radiograph number
     */
    public Filed {
        Objects.requireNonNull(radiograph, "radiograph");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(filedOn, "filedOn");
        Objects.requireNonNull(retention, "retention");
        Objects.requireNonNull(location, "location");
        if (Text.isBlank(radiograph)) {
            throw new ValidationError("a filed radiograph has a number");
        }
    }

    /** A radiograph filed where it can be found. */
    public Filed(String radiograph, String job, LocalDate filedOn, Retention retention, Location location) {
        this(radiograph, job, filedOn, retention, Optional.of(location));
    }

    /** The day the film may be destroyed, for a retention that has one. */
    public Optional<LocalDate> destroyAfter() {
        return retention.days().isPresent()
                ? Optional.of(filedOn.plusDays(retention.days().getAsInt()))
                : Optional.empty();
    }

    /** Whether the film may be destroyed on that day: on the day the retention runs out or after. */
    public boolean isDueOn(LocalDate day) {
        return destroyAfter().map(due -> !day.isBefore(due)).orElse(false);
    }

    /** Why the film does not count as retained: marked retained with no location. */
    public List<String> faults() {
        if (location.isEmpty()) {
            return List.of(radiograph + " is marked retained with no location");
        }
        return List.of();
    }
}
