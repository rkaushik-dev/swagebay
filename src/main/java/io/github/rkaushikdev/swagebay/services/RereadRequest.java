package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A second reading of the same film, by somebody else, on the record.
 *
 * @param radiographNumber the film read again
 * @param reader the second reader
 * @param readAt when they read it
 * @param reason why the second reading was asked for
 * @param indications everything the second reader called, none for a clean film
 */
public record RereadRequest(
        String radiographNumber, Person reader, LocalDateTime readAt, String reason, List<Indication> indications) {

    /**
     * A reread; the indications are copied.
     *
     * @throws ValidationError for a reread that names no film or gives no reason
     */
    public RereadRequest {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(readAt, "readAt");
        indications = List.copyOf(indications);
        if (Text.isBlank(radiographNumber)) {
            throw new ValidationError("a reread names the film it was made on");
        }
        if (Text.isBlank(reason)) {
            throw new ValidationError("a reread says why it was asked for");
        }
    }

    /** A reread that finds nothing on the film. */
    public RereadRequest(String radiographNumber, Person reader, LocalDateTime readAt, String reason) {
        this(radiographNumber, reader, readAt, reason, List.of());
    }
}
