package io.github.rkaushikdev.swagebay.calibration;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * The calibration register, which is the first thing an auditor asks for.
 *
 * <p>Every instrument the shop uses to produce a number has an interval. Keeping them in one
 * register with the interval as data, rather than in four places with the interval in the code, is
 * the difference between one overdue list and four.
 */
public final class Register {

    private final Map<String, Item> items = new LinkedHashMap<>();

    /** An empty register. */
    public Register() {}

    /** Every instrument by serial, in the order they were registered. */
    public Map<String, Item> items() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Put an instrument on the register.
     *
     * @throws ConflictError for a serial already on it
     */
    public void add(Item item) {
        if (items.containsKey(item.serial())) {
            throw new ConflictError("that instrument is already registered", Context.of("serial", item.serial()));
        }
        items.put(item.serial(), item);
    }

    /**
     * The instrument with that serial.
     *
     * @throws ConflictError for a serial that is not registered
     */
    public Item get(String serial) {
        Item item = items.get(serial);
        if (item == null) {
            throw new ConflictError("no instrument with that serial", Context.of("serial", serial));
        }
        return item;
    }

    /**
     * Add a certificate to an instrument.
     *
     * @throws ConflictError for a serial that is not registered
     */
    public void calibrate(String serial, Certificate certificate) {
        get(serial).calibrate(certificate);
    }

    /** Every instrument out of calibration on that day, including the ones never done, by serial. */
    public List<String> overdueOn(LocalDate day) {
        return bySerial()
                .filter(item -> !item.isCurrentOn(day))
                .map(Item::serial)
                .toList();
    }

    /**
     * What falls due inside the look ahead and can still be booked in, by serial: due on that day or
     * up to that many days later. Something already overdue is not on it.
     *
     * @throws ValidationError for a negative look ahead
     */
    public List<String> dueWithin(LocalDate day, int days) {
        if (days < 0) {
            throw new ValidationError("a look ahead is not negative", Context.of("days", days));
        }
        return bySerial()
                .filter(item -> {
                    OptionalLong left = item.daysLeftOn(day);
                    return left.isPresent() && left.getAsLong() >= 0 && left.getAsLong() <= days;
                })
                .map(Item::serial)
                .toList();
    }

    /**
     * The due date the instrument's interval implies, for when a certificate does not say.
     *
     * @throws ConflictError for a serial that is not registered
     */
    public LocalDate defaultDue(String serial, LocalDate calibratedOn) {
        return calibratedOn.plusDays(get(serial).kind().defaultInterval().toDays());
    }

    private Stream<Item> bySerial() {
        return items.values().stream().sorted(Comparator.comparing(Item::serial));
    }
}
