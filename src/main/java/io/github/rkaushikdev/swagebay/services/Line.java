package io.github.rkaushikdev.swagebay.services;

import java.util.Objects;

/**
 * One thing on the morning sheet somebody has to do something about, and how soon.
 *
 * @param urgency {@code today}, {@code this week} or {@code late}
 * @param subject the device, source or report it is about
 * @param account what is wrong
 */
public record Line(String urgency, String subject, String account) {

    /** A line. */
    public Line {
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(account, "account");
    }

    /** {@code D-77: no quarterly inspection on record}. */
    public String describe() {
        return subject + ": " + account;
    }
}
