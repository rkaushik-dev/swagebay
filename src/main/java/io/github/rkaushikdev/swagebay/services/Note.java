package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Something added to a day log after it was closed, with its own time on it.
 *
 * @param writtenAt when it was written
 * @param writtenBy who wrote it
 * @param text what it says
 */
public record Note(LocalDateTime writtenAt, String writtenBy, String text) {

    /**
     * A note.
     *
     * @throws ValidationError for a note nobody signed, or one that says nothing
     */
    public Note {
        Objects.requireNonNull(writtenAt, "writtenAt");
        if (Text.isBlank(writtenBy) || Text.isBlank(text)) {
            throw new ValidationError("a note is signed and says something");
        }
    }
}
