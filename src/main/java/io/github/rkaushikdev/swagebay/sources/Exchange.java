package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One source exchange: what came out of a device, what went in, and the paperwork behind it.
 *
 * <p>About twice a year the shop swaps the source in a device. The old one goes back to the
 * manufacturer in a type B package and the new one comes out of the package it arrived in. The
 * shipment number ties the two halves together, because the day somebody asks where a source went,
 * the answer has to be a document and not a recollection. A load has nothing coming out and an
 * unload has nothing going in; an exchange that moves nothing is not an exchange.
 *
 * @param deviceSerial the device the exchange is against
 * @param removedSerial the source that came out, if one did
 * @param installedSerial the source that went in, if one did
 * @param performedOn the day of the exchange, which is what the paperwork carries
 * @param performedBy the badge of whoever did it
 * @param shipment the shipment the sources travelled on
 * @param notes anything else worth writing down
 */
public record Exchange(
        String deviceSerial,
        Optional<String> removedSerial,
        Optional<String> installedSerial,
        LocalDate performedOn,
        String performedBy,
        String shipment,
        String notes) {

    /**
     * An exchange.
     *
     * @throws ValidationError unsigned, without a shipment, moving nothing, or taking one source out
     *     and putting the same one back
     */
    public Exchange {
        Objects.requireNonNull(removedSerial, "removedSerial");
        Objects.requireNonNull(installedSerial, "installedSerial");
        if (Text.strip(performedBy).isEmpty()) {
            throw new ValidationError("an exchange is signed by the person who did it");
        }
        if (Text.strip(shipment).isEmpty()) {
            throw new ValidationError("an exchange names the shipment it came on");
        }
        if (removedSerial.isEmpty() && installedSerial.isEmpty()) {
            throw new ValidationError("an exchange that moved nothing is not an exchange");
        }
        if (removedSerial.isPresent() && removedSerial.equals(installedSerial)) {
            throw new ValidationError(
                    "that is the same source going out and coming back in", Context.of("serial", removedSerial.get()));
        }
    }

    /** An exchange with nothing in the notes. */
    public Exchange(
            String deviceSerial,
            Optional<String> removedSerial,
            Optional<String> installedSerial,
            LocalDate performedOn,
            String performedBy,
            String shipment) {
        this(deviceSerial, removedSerial, installedSerial, performedOn, performedBy, shipment, "");
    }

    /** Whether nothing came out: the device was empty and a source went in. */
    public boolean isLoad() {
        return removedSerial.isEmpty();
    }

    /** Whether nothing went in: a source came out and left the device empty. */
    public boolean isUnload() {
        return installedSerial.isEmpty();
    }

    /** What the exchange did, as the shop log writes it. */
    public String describe() {
        if (isLoad()) {
            return installedSerial.orElseThrow() + " into " + deviceSerial + " on " + performedOn;
        }
        if (isUnload()) {
            return removedSerial.orElseThrow() + " out of " + deviceSerial + " on " + performedOn;
        }
        return removedSerial.orElseThrow() + " out and " + installedSerial.orElseThrow() + " into " + deviceSerial
                + " on " + performedOn;
    }
}
