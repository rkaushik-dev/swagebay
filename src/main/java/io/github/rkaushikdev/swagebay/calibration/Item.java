package io.github.rkaushikdev.swagebay.calibration;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One instrument the shop owns, with every certificate it has had.
 *
 * <p>An instrument with no calibration on record is overdue, not unknown: an unknown sorts out of
 * the way and an overdue does not.
 */
public final class Item {

    private final String serial;
    private final Instrument kind;
    private final List<Certificate> certificates = new ArrayList<>();

    /**
     * An instrument with no certificate yet.
     *
     * @throws ValidationError without a serial number
     */
    public Item(String serial, Instrument kind) {
        this(serial, kind, List.of());
    }

    /**
     * An instrument with the certificates it has had.
     *
     * @throws ValidationError without a serial number
     */
    public Item(String serial, Instrument kind, List<Certificate> certificates) {
        if (Text.strip(serial).isEmpty()) {
            throw new ValidationError("an instrument has a serial number");
        }
        this.serial = serial;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.certificates.addAll(certificates);
    }

    /** The serial number. */
    public String serial() {
        return serial;
    }

    /** What kind of instrument it is. */
    public Instrument kind() {
        return kind;
    }

    /** Every certificate, in the order they were added. */
    public List<Certificate> certificates() {
        return Collections.unmodifiableList(certificates);
    }

    /** The certificate with the latest calibration date, the first added of any on the same day. */
    public Optional<Certificate> latest() {
        return certificates.stream().max(Comparator.comparing(Certificate::calibratedOn));
    }

    /** When the latest certificate runs out, if there is one. */
    public Optional<LocalDate> dueOn() {
        return latest().map(Certificate::dueOn);
    }

    /** Whether the latest certificate holds on that day; never, without one. */
    public boolean isCurrentOn(LocalDate day) {
        return latest().map(certificate -> certificate.isCurrentOn(day)).orElse(false);
    }

    /** The days from that day to the due date, negative once it has passed; empty without a certificate. */
    public OptionalLong daysLeftOn(LocalDate day) {
        Optional<LocalDate> due = dueOn();
        return due.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ChronoUnit.DAYS.between(day, due.get()));
    }

    /** Add a certificate; the register does this. */
    void calibrate(Certificate certificate) {
        certificates.add(Objects.requireNonNull(certificate, "certificate"));
    }
}
