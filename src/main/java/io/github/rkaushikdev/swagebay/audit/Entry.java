package io.github.rkaushikdev.swagebay.audit;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One change to one field of one record.
 *
 * <p>The old value is on the entry as well as the new one, because a trail that says only what a
 * field became cannot show what it was without replaying every entry from the beginning. Nothing is
 * recorded without an actor: a change with no badge on it is a change nobody can be asked about.
 *
 * @param at when the change was made
 * @param actor the badge of whoever made it, never blank
 * @param action what was done
 * @param record the kind of record, {@code weld}
 * @param identity which one, {@code GW-104}
 * @param fieldName the field changed, empty unless the action is a change
 * @param was what the field held before, empty for nothing
 * @param became what it holds now, empty for nothing
 * @param reason why, when somebody said
 */
public record Entry(
        LocalDateTime at,
        String actor,
        Action action,
        String record,
        String identity,
        String fieldName,
        String was,
        String became,
        String reason) {

    /** The minute a trail line is stamped with. */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    /**
     * An entry.
     *
     * @throws ValidationError without an actor, a record or an identity, or for a change that names
     *     no field or changed nothing
     */
    public Entry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(became, "became");
        Objects.requireNonNull(reason, "reason");
        if (Text.strip(actor).isEmpty()) {
            throw new ValidationError("a change is made by somebody");
        }
        if (Text.strip(record).isEmpty() || Text.strip(identity).isEmpty()) {
            throw new ValidationError("a change names the record it changed");
        }
        if (action == Action.CHANGED && Text.strip(fieldName).isEmpty()) {
            throw new ValidationError("a change names the field it changed");
        }
        if (action == Action.CHANGED && was.equals(became)) {
            throw new ValidationError(
                    "a change that changed nothing is not a change", Context.of("field", fieldName, "value", was));
        }
    }

    /** An entry that touches no single field: a creation, a deletion or a signature. */
    public Entry(LocalDateTime at, String actor, Action action, String record, String identity) {
        this(at, actor, action, record, identity, "", "", "", "");
    }

    /** An entry with no reason given. */
    public Entry(
            LocalDateTime at,
            String actor,
            Action action,
            String record,
            String identity,
            String fieldName,
            String was,
            String became) {
        this(at, actor, action, record, identity, fieldName, was, became, "");
    }

    /**
     * The line as the trail reads: {@code 2025-05-12 09:00 0142 weld GW-104 disposition repair to
     * accept (client agreed)}. An empty value reads as {@code nothing}.
     */
    public String describe() {
        String body = action == Action.CHANGED
                ? fieldName + " " + orNothing(was) + " to " + orNothing(became)
                : action.toString();
        String note = Text.strip(reason).isEmpty() ? "" : " (" + reason + ")";
        return at.format(STAMP) + " " + actor + " " + record + " " + identity + " " + body + note;
    }

    private static String orNothing(String value) {
        return value.isEmpty() ? "nothing" : value;
    }
}
