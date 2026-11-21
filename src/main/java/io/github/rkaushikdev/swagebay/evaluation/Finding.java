package io.github.rkaushikdev.swagebay.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * One rule that was broken, with the clause and the numbers.
 *
 * @param clause the clause of the code, {@code 9.3.8} or {@code UW-51(b2)}
 * @param kind the kind of imperfection the finding is against
 * @param reason the finding in words, with the numbers
 * @param indications the indications it is about
 */
public record Finding(String clause, Kind kind, String reason, List<Indication> indications) {

    /** A finding; the indications are copied. */
    public Finding {
        Objects.requireNonNull(clause, "clause");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        indications = List.copyOf(indications);
    }

    /** A finding that names no indication. */
    public Finding(String clause, Kind kind, String reason) {
        this(clause, kind, reason, List.of());
    }

    /** The finding as a report line: {@code 9.3.8 ESI: ...}. */
    public String describe() {
        return clause + " " + kind.code() + ": " + reason;
    }
}
