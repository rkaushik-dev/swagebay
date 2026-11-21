package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One abnormal occurrence, and what is owed to the regulator because of it.
 *
 * <p>The clock starts when the shop finds out, not when it decides. So an incident carries the
 * moment it was discovered and its own class, and the deadline falls out of both. Storing a deadline
 * instead would let somebody correct it, which is exactly the field that gets corrected when a report
 * is late. Nothing here notifies anybody: it says what is due and when, and a person makes the call.
 */
public final class Incident {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    private final String reference;
    private final Occurrence occurrence;
    private final LocalDateTime discoveredAt;
    private final String site;
    private final String reportedBy;
    private final String account;
    private LocalDateTime notifiedAt;

    /**
     * An incident not yet reported to the regulator.
     *
     * @throws ValidationError naming the first blank field among the reference, site, who reported
     *     it and the account
     */
    public Incident(
            String reference,
            Occurrence occurrence,
            LocalDateTime discoveredAt,
            String site,
            String reportedBy,
            String account) {
        this(reference, occurrence, discoveredAt, site, reportedBy, account, Optional.empty());
    }

    /**
     * An incident, reported to the regulator or not.
     *
     * @throws ValidationError naming the first blank field among the reference, site, who reported
     *     it and the account, or for a report made before the incident was found
     */
    public Incident(
            String reference,
            Occurrence occurrence,
            LocalDateTime discoveredAt,
            String site,
            String reportedBy,
            String account,
            Optional<LocalDateTime> notifiedAt) {
        required("reference", reference);
        required("site", site);
        required("reported_by", reportedBy);
        required("account", account);
        if (notifiedAt.isPresent() && notifiedAt.get().isBefore(discoveredAt)) {
            throw new ValidationError(
                    "an incident cannot be reported before it was found", Context.of("reference", reference));
        }
        this.reference = reference;
        this.occurrence = Objects.requireNonNull(occurrence, "occurrence");
        this.discoveredAt = Objects.requireNonNull(discoveredAt, "discoveredAt");
        this.site = site;
        this.reportedBy = reportedBy;
        this.account = account;
        this.notifiedAt = notifiedAt.orElse(null);
    }

    /** The incident reference, {@code INC-1}. */
    public String reference() {
        return reference;
    }

    /** What happened. */
    public Occurrence occurrence() {
        return occurrence;
    }

    /** When the shop found out, which is when the clock started. */
    public LocalDateTime discoveredAt() {
        return discoveredAt;
    }

    /** Where it happened. */
    public String site() {
        return site;
    }

    /** The badge of whoever reported it inside the shop. */
    public String reportedBy() {
        return reportedBy;
    }

    /** What happened, in words. */
    public String account() {
        return account;
    }

    /** When the regulator was told, once they have been. */
    public Optional<LocalDateTime> notifiedAt() {
        return Optional.ofNullable(notifiedAt);
    }

    /** When the regulator has to have been told by. */
    public LocalDateTime dueAt() {
        return discoveredAt.plus(occurrence.deadline());
    }

    /** Whether the regulator has been told. */
    public boolean isNotified() {
        return notifiedAt != null;
    }

    /**
     * Whether the report is late at that moment: told after the deadline, which stays late forever,
     * or not told and the deadline passed.
     */
    public boolean isLateAt(LocalDateTime moment) {
        if (notifiedAt != null) {
            return notifiedAt.isAfter(dueAt());
        }
        return moment.isAfter(dueAt());
    }

    /** The time left to the deadline at that moment, negative once it has passed; none once told. */
    public Duration timeLeftAt(LocalDateTime moment) {
        if (notifiedAt != null) {
            return Duration.ZERO;
        }
        return Duration.between(moment, dueAt());
    }

    /**
     * Record that the regulator was told at that moment.
     *
     * @throws ConflictError when it is already recorded as reported
     * @throws ValidationError for a moment before the incident was found
     */
    public void recordNotification(LocalDateTime at) {
        if (notifiedAt != null) {
            throw new ConflictError("that incident is already reported", Context.of("reference", reference));
        }
        if (at.isBefore(discoveredAt)) {
            throw new ValidationError("an incident cannot be reported before it was found");
        }
        notifiedAt = at;
    }

    /** The register line: {@code INC-1 dose over a limit, due 2025-02-25 23:40}, or {@code reported}. */
    public String describe() {
        String state = isNotified() ? "reported" : "due " + dueAt().format(DUE);
        return reference + " " + occurrence + ", " + state;
    }

    private static void required(String name, String value) {
        if (Text.strip(value).isEmpty()) {
            throw new ValidationError("an incident needs this filled in", Context.of("field", name));
        }
    }
}
