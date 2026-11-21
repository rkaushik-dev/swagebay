package io.github.rkaushikdev.swagebay.nonconformance;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One non conformance report, from raised to closed.
 *
 * <p>The report is the piece of paper that says work did not meet the specification and records
 * what was decided about it. A disposition arrives with its paperwork or not at all. Closure needs
 * somebody other than the person who raised it: that is the whole control, and a report raised and
 * closed by one person is a note to self.
 */
public final class Report {

    /** A report open longer than this many days escalates. */
    public static final int ESCALATE_AFTER_DAYS = 30;

    private final String reference;
    private final String job;
    private final String item;
    private final String requirement;
    private final String account;
    private final LocalDate raisedOn;
    private final String raisedBy;
    private Disposition disposition;
    private String concession;
    private String removal;
    private LocalDate closedOn;
    private String closedBy;

    /**
     * A report as raised: open and undecided.
     *
     * @throws ValidationError naming the first blank field among the reference, job, item,
     *     requirement, account and who raised it
     */
    public Report(
            String reference,
            String job,
            String item,
            String requirement,
            String account,
            LocalDate raisedOn,
            String raisedBy) {
        this(
                reference,
                job,
                item,
                requirement,
                account,
                raisedOn,
                raisedBy,
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                "");
    }

    /**
     * A report as it stands on the register, decided or closed or neither.
     *
     * @throws ValidationError naming the first blank field among the reference, job, item,
     *     requirement, account and who raised it
     */
    public Report(
            String reference,
            String job,
            String item,
            String requirement,
            String account,
            LocalDate raisedOn,
            String raisedBy,
            Optional<Disposition> disposition,
            String concession,
            String removal,
            Optional<LocalDate> closedOn,
            String closedBy) {
        required("reference", reference);
        required("job", job);
        required("item", item);
        required("requirement", requirement);
        required("account", account);
        required("raised_by", raisedBy);
        this.reference = reference;
        this.job = job;
        this.item = item;
        this.requirement = requirement;
        this.account = account;
        this.raisedOn = Objects.requireNonNull(raisedOn, "raisedOn");
        this.raisedBy = raisedBy;
        this.disposition = disposition.orElse(null);
        this.concession = Objects.requireNonNull(concession, "concession");
        this.removal = Objects.requireNonNull(removal, "removal");
        this.closedOn = closedOn.orElse(null);
        this.closedBy = Objects.requireNonNull(closedBy, "closedBy");
    }

    /** The report number, {@code NCR-1}. */
    public String reference() {
        return reference;
    }

    /** The job it was raised against. */
    public String job() {
        return job;
    }

    /** The item that did not conform, {@code GW-104}. */
    public String item() {
        return item;
    }

    /** The requirement it did not meet. */
    public String requirement() {
        return requirement;
    }

    /** What was found, in words. */
    public String account() {
        return account;
    }

    /** The day it was raised. */
    public LocalDate raisedOn() {
        return raisedOn;
    }

    /** The badge of whoever raised it. */
    public String raisedBy() {
        return raisedBy;
    }

    /** What was decided, once something has been. */
    public Optional<Disposition> disposition() {
        return Optional.ofNullable(disposition);
    }

    /** The concession reference the client signed, empty when none was needed. */
    public String concession() {
        return concession;
    }

    /** Where a rejected item went, empty when nothing was rejected. */
    public String removal() {
        return removal;
    }

    /** The day it closed, once it has. */
    public Optional<LocalDate> closedOn() {
        return Optional.ofNullable(closedOn);
    }

    /** The badge of whoever closed it, empty while it is open. */
    public String closedBy() {
        return closedBy;
    }

    /** Whether it is still open. */
    public boolean isOpen() {
        return closedOn == null;
    }

    /** {@link #decide(Disposition, String, String)} with no concession and no removal reference. */
    public void decide(Disposition disposition) {
        decide(disposition, "", "");
    }

    /**
     * Set the disposition, refusing one that arrives without its paperwork. The references are kept
     * without surrounding white space.
     *
     * @throws ConflictError on a closed report, for a disposition that needs a concession without
     *     one, or for a rejection without a removal reference
     */
    public void decide(Disposition disposition, String concession, String removal) {
        if (!isOpen()) {
            throw new ConflictError("that report is closed", Context.of("reference", reference));
        }
        if (disposition.needsConcession() && Text.strip(concession).isEmpty()) {
            throw new ConflictError(
                    "that disposition needs a concession reference",
                    Context.of("reference", reference, "disposition", disposition.toString()));
        }
        if (disposition.needsRemoval() && Text.strip(removal).isEmpty()) {
            throw new ConflictError("a rejected item needs a removal reference", Context.of("reference", reference));
        }
        this.disposition = disposition;
        this.concession = Text.strip(concession);
        this.removal = Text.strip(removal);
    }

    /**
     * Close it, refusing every way a close can be worthless.
     *
     * @throws ConflictError when it is already closed, when nothing has been decided, or when the
     *     person closing it is the one who raised it
     * @throws ValidationError for an unsigned close, or a close dated before the report was raised
     */
    public void close(LocalDate on, String by) {
        if (!isOpen()) {
            throw new ConflictError("that report is already closed", Context.of("reference", reference));
        }
        if (disposition == null) {
            throw new ConflictError("nothing has been decided yet", Context.of("reference", reference));
        }
        String signed = Text.strip(by);
        if (signed.isEmpty()) {
            throw new ValidationError("a close is signed");
        }
        if (signed.equals(raisedBy)) {
            throw new ConflictError(
                    "a report is not closed by the person who raised it",
                    Context.of("reference", reference, "badge", by));
        }
        if (on.isBefore(raisedOn)) {
            throw new ValidationError("a report cannot close before it was raised", Context.of("reference", reference));
        }
        this.closedOn = on;
        this.closedBy = signed;
    }

    /** The days it has been open as of that day, stopping when it closed, never below zero. */
    public long daysOpen(LocalDate on) {
        LocalDate end = closedOn != null ? closedOn : on;
        return Math.max(0, ChronoUnit.DAYS.between(raisedOn, end));
    }

    /** Whether it is open and has been for more than {@link #ESCALATE_AFTER_DAYS}. */
    public boolean isOverdue(LocalDate on) {
        return isOpen() && daysOpen(on) > ESCALATE_AFTER_DAYS;
    }

    /** The first day it is overdue if it stays open. */
    public LocalDate escalatesOn() {
        return raisedOn.plusDays(ESCALATE_AFTER_DAYS + 1);
    }

    /** The register line: {@code NCR-1 24-0001 GW-104: rework, closed 2025-06-30 by 0303}. */
    public String describe() {
        String state = isOpen() ? "open" : "closed " + closedOn + " by " + closedBy;
        String decided = disposition != null ? disposition.toString() : "undecided";
        return reference + " " + job + " " + item + ": " + decided + ", " + state;
    }

    private static void required(String name, String value) {
        if (Text.strip(value).isEmpty()) {
            throw new ValidationError("a report needs this filled in", Context.of("field", name));
        }
    }
}
