package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One shift, assembled from the record and topped up by hand.
 *
 * <p>The parts that cannot be assembled from the record are the parts typed here: the crew, the
 * weather, and the account of anything unusual. {@link DayLogService} assembles the rest.
 *
 * <p>A log is closed at the end of the shift and then it is not editable. An addition after the
 * fact is a separate {@link Note} with its own timestamp, which is what a lawyer would expect and
 * what a shift supervisor would do on paper.
 */
public final class DayLog {

    private final LocalDate day;
    private final String site;
    private final List<String> crew;
    private final String deviceSerial;
    private final String sourceSerial;
    private final String weather;
    private String account = "";
    private LocalDateTime closedAt;
    private final List<Note> notes = new ArrayList<>();

    /**
     * An open log with nothing said about the weather.
     *
     * @throws ValidationError for a log that names no site or no crew
     */
    public DayLog(LocalDate day, String site, List<String> crew, String deviceSerial, String sourceSerial) {
        this(day, site, crew, deviceSerial, sourceSerial, "");
    }

    /**
     * An open log.
     *
     * @param crew the badges of everybody on the shift
     * @param weather what the weather was, as the crew wrote it
     * @throws ValidationError for a log that names no site or no crew
     */
    public DayLog(
            LocalDate day, String site, List<String> crew, String deviceSerial, String sourceSerial, String weather) {
        this.day = Objects.requireNonNull(day, "day");
        this.crew = List.copyOf(crew);
        this.deviceSerial = Objects.requireNonNull(deviceSerial, "deviceSerial");
        this.sourceSerial = Objects.requireNonNull(sourceSerial, "sourceSerial");
        this.weather = Objects.requireNonNull(weather, "weather");
        if (Text.isBlank(site)) {
            throw new ValidationError("a log names the site");
        }
        if (this.crew.isEmpty()) {
            throw new ValidationError("a log names the crew");
        }
        this.site = site;
    }

    /** The day of the shift. */
    public LocalDate day() {
        return day;
    }

    /** Where the shift worked. */
    public String site() {
        return site;
    }

    /** The badges of everybody on the shift, in the order given. */
    public List<String> crew() {
        return crew;
    }

    /** The camera that went out. */
    public String deviceSerial() {
        return deviceSerial;
    }

    /** The source that went out in it. */
    public String sourceSerial() {
        return sourceSerial;
    }

    /** What the weather was. */
    public String weather() {
        return weather;
    }

    /** The account of anything unusual, empty until somebody writes one. */
    public String account() {
        return account;
    }

    /** When the log was closed, if it has been. */
    public Optional<LocalDateTime> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    /** The notes added, in the order they were added. */
    public List<Note> notes() {
        return Collections.unmodifiableList(notes);
    }

    /** Whether the log has been closed. */
    public boolean isClosed() {
        return closedAt != null;
    }

    /**
     * Close the log at the end of the shift.
     *
     * @throws ConflictError when it is already closed
     */
    public void close(LocalDateTime at) {
        if (closedAt != null) {
            throw new ConflictError("that log is already closed", Context.of("day", Times.iso(day)));
        }
        closedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Add a note, which may be done at any time. Nothing already written changes.
     *
     * @throws ValidationError for a note dated before the log closed
     */
    public void addNote(Note note) {
        if (closedAt != null && note.writtenAt().isBefore(closedAt)) {
            throw new ValidationError("a note added after the log closed cannot be dated before it");
        }
        notes.add(note);
    }

    /**
     * Write the account of the shift.
     *
     * @throws ConflictError once the log is closed
     */
    public void setAccount(String text) {
        if (isClosed()) {
            throw new ConflictError("a closed log is not edited, add a note instead");
        }
        account = Objects.requireNonNull(text, "text");
    }

    /** The handover line: {@code 2025-03-03 Come By Chance, 2 on crew, open}. */
    public String describe() {
        String state = isClosed() ? "closed" : "open";
        return Times.iso(day) + " " + site + ", " + crew.size() + " on crew, " + state;
    }
}
