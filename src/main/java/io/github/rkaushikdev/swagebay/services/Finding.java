package io.github.rkaushikdev.swagebay.services;

import java.util.Objects;

/**
 * One thing in the database that cannot be true, and what it is about.
 *
 * @param check the name of the check that found it
 * @param subject the record it is about: a serial, a job, a file name or a report
 * @param account what is wrong with it
 */
public record Finding(String check, String subject, String account) {

    /** A finding. */
    public Finding {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(account, "account");
    }

    /** {@code unknown device: D-99 has inspections and is not on the inventory}. */
    public String describe() {
        return check + ": " + subject + " " + account;
    }
}
