package io.github.rkaushikdev.swagebay.procedure;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One revision of one procedure, as approved.
 *
 * <p>A revision is not effective because somebody typed it. It is effective because a Level III
 * signed it, on a date, and the signature is part of the record. A revision with no approval is a
 * draft: a legal object, and one the register never returns as effective.
 *
 * @param number the procedure number, never blank
 * @param revision the revision number, from zero
 * @param title the title on the cover sheet
 * @param code the code it is written to
 * @param effectiveOn the first day it governs work
 * @param approvedBy who signed it, absent on a draft
 * @param approvedOn when it was signed, absent on a draft
 */
public record Revision(
        String number,
        int revision,
        String title,
        Code code,
        LocalDate effectiveOn,
        Optional<Person> approvedBy,
        Optional<LocalDate> approvedOn) {

    /**
     * A revision.
     *
     * @throws ValidationError without a number, for a negative revision, for a signature without a
     *     date or a date without a signature, or for an approval after the revision took effect
     */
    public Revision {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(effectiveOn, "effectiveOn");
        Objects.requireNonNull(approvedBy, "approvedBy");
        Objects.requireNonNull(approvedOn, "approvedOn");
        if (Text.strip(number).isEmpty()) {
            throw new ValidationError("a procedure needs a number");
        }
        if (revision < 0) {
            throw new ValidationError("a revision number is not negative", Context.of("revision", revision));
        }
        if (approvedBy.isEmpty() != approvedOn.isEmpty()) {
            throw new ValidationError("an approval needs both a signature and a date", Context.of("number", number));
        }
        if (approvedOn.isPresent() && approvedOn.get().isAfter(effectiveOn)) {
            throw new ValidationError(
                    "a revision cannot take effect before it was approved",
                    Context.of("approved_on", Times.iso(approvedOn.get()), "effective_on", Times.iso(effectiveOn)));
        }
    }

    /** A draft, with no approval on it. */
    public Revision(String number, int revision, String title, Code code, LocalDate effectiveOn) {
        this(number, revision, title, code, effectiveOn, Optional.empty(), Optional.empty());
    }

    /** A revision signed by somebody on a day. */
    public Revision(
            String number,
            int revision,
            String title,
            Code code,
            LocalDate effectiveOn,
            Person approvedBy,
            LocalDate approvedOn) {
        this(number, revision, title, code, effectiveOn, Optional.of(approvedBy), Optional.of(approvedOn));
    }

    /** Whether somebody has signed it. */
    public boolean isApproved() {
        return approvedBy.isPresent();
    }

    /** The label on the cover sheet: {@code RT-001 rev 2}. */
    public String label() {
        return number + " rev " + revision;
    }

    /** Whether the signature was one the signer was entitled to give on the day it was given. */
    public boolean approvalIsSound() {
        if (approvedBy.isEmpty() || approvedOn.isEmpty()) {
            return false;
        }
        return approvedBy.get().mayApproveProcedureOn(approvedOn.get());
    }

    /** The label and the code: {@code RT-001 rev 2 (ASME Section V)}. */
    @Override
    public String toString() {
        return label() + " (" + code + ")";
    }
}
