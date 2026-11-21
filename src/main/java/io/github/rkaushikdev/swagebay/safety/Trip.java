package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One source, out in one device, with one crew, to one site.
 *
 * <p>This is a line of the utilization log, filled in at the time rather than reconstructed. It
 * stays open until the source comes back, so the day it came back is set later.
 */
public final class Trip {

    private final String sourceSerial;
    private final String deviceSerial;
    private final String crewBadge;
    private final String site;
    private final LocalDate wentOut;
    private LocalDate cameBack;
    private final int exposures;

    /** A trip still out, with no exposures counted. */
    public Trip(String sourceSerial, String deviceSerial, String crewBadge, String site, LocalDate wentOut) {
        this(sourceSerial, deviceSerial, crewBadge, site, wentOut, Optional.empty(), 0);
    }

    /** A trip that has come back, with no exposures counted. */
    public Trip(
            String sourceSerial,
            String deviceSerial,
            String crewBadge,
            String site,
            LocalDate wentOut,
            LocalDate cameBack) {
        this(sourceSerial, deviceSerial, crewBadge, site, wentOut, Optional.of(cameBack), 0);
    }

    /** A trip that has come back, with the exposures made on it. */
    public Trip(
            String sourceSerial,
            String deviceSerial,
            String crewBadge,
            String site,
            LocalDate wentOut,
            LocalDate cameBack,
            int exposures) {
        this(sourceSerial, deviceSerial, crewBadge, site, wentOut, Optional.of(cameBack), exposures);
    }

    /**
     * A trip as the log has it, back or not.
     *
     * @throws ValidationError for a source that came back before it went out
     */
    public Trip(
            String sourceSerial,
            String deviceSerial,
            String crewBadge,
            String site,
            LocalDate wentOut,
            Optional<LocalDate> cameBack,
            int exposures) {
        this.sourceSerial = Objects.requireNonNull(sourceSerial, "sourceSerial");
        this.deviceSerial = Objects.requireNonNull(deviceSerial, "deviceSerial");
        this.crewBadge = Objects.requireNonNull(crewBadge, "crewBadge");
        this.site = Objects.requireNonNull(site, "site");
        this.wentOut = Objects.requireNonNull(wentOut, "wentOut");
        this.cameBack = cameBack.orElse(null);
        this.exposures = exposures;
        if (this.cameBack != null && this.cameBack.isBefore(wentOut)) {
            throw new ValidationError(
                    "a source cannot come back before it went out", Context.of("source", sourceSerial));
        }
    }

    /** The source that went out. */
    public String sourceSerial() {
        return sourceSerial;
    }

    /** The device it went out in. */
    public String deviceSerial() {
        return deviceSerial;
    }

    /** The badge of the crew it went out with. */
    public String crewBadge() {
        return crewBadge;
    }

    /** Where it went. */
    public String site() {
        return site;
    }

    /** The day it went out. */
    public LocalDate wentOut() {
        return wentOut;
    }

    /** The day it came back, once it has. */
    public Optional<LocalDate> cameBack() {
        return Optional.ofNullable(cameBack);
    }

    /** How many exposures were made on the trip. */
    public int exposures() {
        return exposures;
    }

    /** Whether the source is still out. */
    public boolean isOpen() {
        return cameBack == null;
    }

    /** Whether the source was out on that day, both the day out and the day back included. */
    public boolean covers(LocalDate day) {
        if (day.isBefore(wentOut)) {
            return false;
        }
        return cameBack == null || !day.isAfter(cameBack);
    }

    /** Close the trip on the day the source came back; the inventory checks the day first. */
    void recordReturn(LocalDate on) {
        cameBack = Objects.requireNonNull(on, "on");
    }
}
