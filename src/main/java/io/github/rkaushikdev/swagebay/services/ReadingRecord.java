package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One reader, one film, and what they wrote down.
 *
 * @param radiographNumber the film read
 * @param reader who read it
 * @param readAt when
 * @param indications everything they called, none for a clean film
 */
public record ReadingRecord(
        String radiographNumber, Person reader, LocalDateTime readAt, List<Indication> indications) {

    /**
     * A reading; the indications are copied.
     *
     * @throws ValidationError for a reading that names no film
     */
    public ReadingRecord {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(readAt, "readAt");
        indications = List.copyOf(indications);
        if (Text.isBlank(radiographNumber)) {
            throw new ValidationError("a reading names the film it was made on");
        }
    }

    /** A reading of a clean film, with nothing called on it. */
    public ReadingRecord(String radiographNumber, Person reader, LocalDateTime readAt) {
        this(radiographNumber, reader, readAt, List.of());
    }
}
