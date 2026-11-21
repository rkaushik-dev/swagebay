package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.clock.Moments;
import io.github.rkaushikdev.swagebay.safety.DeviceUnit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which source is in which device, and every exchange that got it there.
 *
 * <p>An exchange changes four things at once: the device holds a different source, the old source
 * is in transit and cannot be booked out, the new one is in service and can, and the history gains
 * a line. {@link #apply} does all four or none. The rules are the ones that have actually caught
 * something. A device holds one source, so loading a second without unloading the first is refused
 * even though the physical device would not fit them. A source that failed its last wipe goes into
 * a return package, not a device. And the device certificate decides what may be loaded, both the
 * isotope and the curies.
 */
public final class Loading {

    private final Map<String, String> held = new LinkedHashMap<>();
    private final List<Exchange> history = new ArrayList<>();

    /** A record with no device loaded and no exchange on it. */
    public Loading() {}

    /** The serial of the source in each loaded device, by device serial, in the order they were loaded. */
    public Map<String, String> held() {
        return Collections.unmodifiableMap(held);
    }

    /** Every exchange, in the order it was applied. */
    public List<Exchange> history() {
        return Collections.unmodifiableList(history);
    }

    /** The source a device holds, if it holds one. */
    public Optional<String> sourceIn(String deviceSerial) {
        return Optional.ofNullable(held.get(deviceSerial));
    }

    /** The device holding a source, if one is. */
    public Optional<String> deviceHolding(String sourceSerial) {
        return held.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sourceSerial))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** The serials of the devices holding something, sorted. */
    public List<String> loadedDevices() {
        return held.keySet().stream().sorted().toList();
    }

    /** The exchanges against one device, in date order, those on one day in the order applied. */
    public List<Exchange> exchangesFor(String deviceSerial) {
        return history.stream()
                .filter(exchange -> exchange.deviceSerial().equals(deviceSerial))
                .sorted(Comparator.comparing(Exchange::performedOn))
                .toList();
    }

    /** {@link #apply(Exchange, DeviceUnit, Optional, Optional)} with neither source in hand. */
    public void apply(Exchange exchange, DeviceUnit device) {
        apply(exchange, device, Optional.empty(), Optional.empty());
    }

    /**
     * Do the exchange, or refuse it and change nothing.
     *
     * <p>A source in hand is checked against the exchange and moved to its new state: the one coming
     * out goes into transit and the one going in into service.
     *
     * @param removed the source coming out, when it is in hand
     * @param installed the source going in, when it is in hand
     * @throws ValidationError for an exchange against another device, or a source that is not the
     *     one the exchange names
     * @throws ConflictError when the device does not hold what the exchange takes out, already holds
     *     a source, or the source going in is in another device, has no current wipe or is awaiting
     *     disposal
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError when the device is not certified for
     *     the source going in
     */
    public void apply(
            Exchange exchange, DeviceUnit device, Optional<SealedSource> removed, Optional<SealedSource> installed) {
        check(exchange, device, removed, installed);
        if (exchange.removedSerial().isPresent()) {
            held.remove(exchange.deviceSerial());
            removed.ifPresent(source -> source.setState(SourceState.IN_TRANSIT));
        }
        if (exchange.installedSerial().isPresent()) {
            held.put(exchange.deviceSerial(), exchange.installedSerial().get());
            installed.ifPresent(source -> source.setState(SourceState.IN_SERVICE));
        }
        history.add(exchange);
    }

    /**
     * Add an exchange already made and on record, moving the serials as it says without checking
     * it again. This is how the record is rebuilt from the stored exchanges, in the order they were
     * made.
     */
    public void replay(Exchange exchange) {
        if (exchange.removedSerial().isPresent()) {
            held.remove(exchange.deviceSerial());
        }
        exchange.installedSerial().ifPresent(serial -> held.put(exchange.deviceSerial(), serial));
        history.add(exchange);
    }

    private void check(
            Exchange exchange, DeviceUnit device, Optional<SealedSource> removed, Optional<SealedSource> installed) {
        if (!device.serial().equals(exchange.deviceSerial())) {
            throw new ValidationError(
                    "that exchange is against a different device",
                    Context.of("named", exchange.deviceSerial(), "given", device.serial()));
        }
        Optional<String> holding = sourceIn(exchange.deviceSerial());
        if (exchange.removedSerial().isPresent() && !holding.equals(exchange.removedSerial())) {
            throw new ConflictError(
                    "that device is not holding the source the exchange takes out",
                    Context.of(
                            "device",
                            exchange.deviceSerial(),
                            "holding",
                            orNothing(holding),
                            "named",
                            exchange.removedSerial().get()));
        }
        if (exchange.removedSerial().isEmpty() && holding.isPresent()) {
            throw new ConflictError(
                    "that device already holds a source",
                    Context.of("device", exchange.deviceSerial(), "holding", holding.get()));
        }
        if (exchange.installedSerial().isPresent()) {
            String serial = exchange.installedSerial().get();
            Optional<String> elsewhere = deviceHolding(serial);
            if (elsewhere.isPresent() && !elsewhere.get().equals(exchange.deviceSerial())) {
                throw new ConflictError(
                        "that source is already in another device",
                        Context.of("source", serial, "device", elsewhere.get()));
            }
        }
        if (installed.isPresent()) {
            checkInstalled(exchange, device, installed.get());
        }
        if (removed.isPresent()
                && !exchange.removedSerial().equals(Optional.of(removed.get().serial()))) {
            throw new ValidationError(
                    "that is not the source the exchange takes out",
                    Context.of(
                            "named",
                            orNothing(exchange.removedSerial()),
                            "given",
                            removed.get().serial()));
        }
    }

    private static void checkInstalled(Exchange exchange, DeviceUnit device, SealedSource installed) {
        if (!exchange.installedSerial().equals(Optional.of(installed.serial()))) {
            throw new ValidationError(
                    "that is not the source the exchange puts in",
                    Context.of("named", orNothing(exchange.installedSerial()), "given", installed.serial()));
        }
        LocalDateTime at = Moments.startOf(exchange.performedOn());
        if (!installed.leakTestCurrentAt(at)) {
            throw new ConflictError(
                    "a source with no current wipe test goes into a package, not a device",
                    Context.of("source", installed.serial()));
        }
        if (installed.state() == SourceState.AWAITING_DISPOSAL) {
            throw new ConflictError("that source is awaiting disposal", Context.of("source", installed.serial()));
        }
        device.model().checkLoad(installed.isotope(), installed.activityAt(at));
    }

    /** The serial, or {@code nothing} when there is none to name. */
    private static String orNothing(Optional<String> serial) {
        return serial.filter(text -> !text.isEmpty()).orElse("nothing");
    }
}
