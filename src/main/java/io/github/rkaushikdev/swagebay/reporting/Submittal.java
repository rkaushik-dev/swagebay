package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What is going to the client, and under what covering note.
 *
 * <p>A report package describes the work. A submittal is what physically goes to the client, and it
 * contains several things the package does not: the procedure the work was done to, the personnel
 * certifications of everybody who signed anything, the equipment calibration certificates, and the
 * film itself. Those are the ones that come back, so the parcel is checked against what is required
 * before it goes rather than after it comes back.
 *
 * <p>Required is not a constant. A client specification says what it wants enclosed, and the ones
 * that ask for less are as real as the ones that ask for more, so every check takes the requirement,
 * and {@link #DEFAULT_REQUIRED} is only a default.
 *
 * @param job the job the parcel is for
 * @param client who it goes to
 * @param preparedOn the day it was made up
 * @param preparedBy who signed the covering note
 * @param enclosures what is in it
 * @param revision how many times it has been sent again, from zero
 */
public record Submittal(
        String job, String client, LocalDate preparedOn, String preparedBy, Set<Enclosure> enclosures, int revision) {

    /** What a client is sent unless its specification says otherwise. */
    public static final List<Enclosure> DEFAULT_REQUIRED = List.of(
            Enclosure.REPORT,
            Enclosure.RADIOGRAPHS,
            Enclosure.PROCEDURE,
            Enclosure.CERTIFICATIONS,
            Enclosure.CALIBRATIONS);

    private static final Comparator<Enclosure> BY_NAME = Comparator.comparing(Enclosure::name);

    /**
     * A submittal; the enclosures are copied.
     *
     * @throws ValidationError for a note nobody signed, or a negative revision
     */
    public Submittal {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(preparedOn, "preparedOn");
        enclosures = Collections.unmodifiableSet(copyOf(enclosures));
        if (Text.isBlank(preparedBy)) {
            throw new ValidationError("a submittal is signed");
        }
        if (revision < 0) {
            throw new ValidationError("a revision is not negative", Context.of("revision", revision));
        }
    }

    /** A first submittal, revision zero. */
    public Submittal(String job, String client, LocalDate preparedOn, String preparedBy, Set<Enclosure> enclosures) {
        this(job, client, preparedOn, preparedBy, enclosures, 0);
    }

    /** What {@link #DEFAULT_REQUIRED} asks for that is not enclosed, in the order it is asked for. */
    public List<Enclosure> missing() {
        return missing(DEFAULT_REQUIRED);
    }

    /** What {@code required} asks for that is not enclosed, in the order it is asked for. */
    public List<Enclosure> missing(List<Enclosure> required) {
        return required.stream().filter(item -> !enclosures.contains(item)).toList();
    }

    /** {@link #extra(List)} against {@link #DEFAULT_REQUIRED}. */
    public List<Enclosure> extra() {
        return extra(DEFAULT_REQUIRED);
    }

    /** What is enclosed that was not asked for, which is not a fault, by name. */
    public List<Enclosure> extra(List<Enclosure> required) {
        return enclosures.stream()
                .filter(item -> !required.contains(item))
                .sorted(BY_NAME)
                .toList();
    }

    /** Whether everything {@link #DEFAULT_REQUIRED} asks for is enclosed. */
    public boolean isReady() {
        return isReady(DEFAULT_REQUIRED);
    }

    /** Whether everything {@code required} asks for is enclosed. */
    public boolean isReady(List<Enclosure> required) {
        return missing(required).isEmpty();
    }

    /**
     * The covering note: the job and the client, who prepared it and when, the revision when it has
     * been sent before, and every enclosure by name.
     */
    public String coveringNote() {
        List<String> lines = new ArrayList<>();
        lines.add("Job " + job + " for " + client);
        lines.add("Prepared " + Times.iso(preparedOn) + " by " + preparedBy);
        if (revision != 0) {
            lines.add("Submittal revision " + revision);
        }
        lines.add("Enclosed:");
        enclosures.stream().sorted(BY_NAME).forEach(item -> lines.add("  " + item));
        return String.join("\n", lines);
    }

    private static Set<Enclosure> copyOf(Collection<Enclosure> enclosures) {
        return enclosures.isEmpty() ? EnumSet.noneOf(Enclosure.class) : EnumSet.copyOf(enclosures);
    }
}
