package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.SightingRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceState;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registering sources, sending them out, and answering for them.
 *
 * <p>The rule that matters here is single occupancy. A source is either in the shop or on exactly
 * one job, and there is no state in between. A crew that takes a source without booking it out and
 * a coordinator who books it out to a second job both produce the same corrupt record, which is a
 * source in two places, and the accountability report cannot tell those apart from a lost source.
 *
 * <p>So sending out is refused if the source is already out, bringing back is refused if it is not,
 * and both go through the same place. Nothing else writes to the trip table.
 *
 * <p>Fitness is computed rather than stored. A source is fit on a given morning or it is not, and
 * the answer depends on the date, so storing it as a flag means storing a flag that is wrong every
 * day after the one it was written on.
 */
public final class SourceService {

    /** Where a source that is not out on a trip is. */
    public static final String SHOP = "shop";

    private SourceService() {}

    /**
     * Put a new source on the inventory.
     *
     * @throws ConflictError for a serial already on it
     */
    public static void registerSource(UnitOfWork work, SealedSource source) {
        if (work.sources().find(source.serial()).isPresent()) {
            throw new ConflictError("that source is already on the inventory", Context.of("serial", source.serial()));
        }
        work.sources().add(SourceMapper.sourceToRow(source));
        work.flush();
    }

    /**
     * Put a new device on the inventory as a model of camera.
     *
     * @throws ValidationError for a blank serial
     * @throws ConflictError for a serial already on it
     */
    public static void registerDevice(UnitOfWork work, String serial, String modelKey) {
        if (Text.isBlank(serial)) {
            throw new ValidationError("a device has a serial number");
        }
        if (work.sources().findDevice(serial).isPresent()) {
            throw new ConflictError("that device is already on the inventory", Context.of("serial", serial));
        }
        DeviceRow row = new DeviceRow();
        row.setSerial(serial);
        row.setModelKey(modelKey);
        work.entityManager().persist(row);
        work.flush();
    }

    /**
     * Book a source out to one job, refusing anything that is not cleared.
     *
     * <p>The single occupancy check stays here because it is about the trip table and nothing else
     * knows about it. Everything else the morning asks is in the dispatch gate, which is called
     * rather than restated, so a rule added there lands here without anybody remembering to do it.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source or a device that is not on
     *     the inventory
     * @throws ConflictError when the source is already out, or the load is not cleared to leave
     */
    public static void sendOut(UnitOfWork work, Booking booking) {
        SourceRow source = work.sources().get(booking.sourceSerial());
        DeviceRow device = work.sources().device(booking.deviceSerial());
        if (work.sources().openTrip(booking.sourceSerial()).isPresent()) {
            throw new ConflictError("that source is already out", Context.of("serial", booking.sourceSerial()));
        }
        DispatchService.requireClearance(
                work,
                booking.deviceSerial(),
                booking.sourceSerial(),
                booking.wentOut(),
                booking.crew(),
                booking.crewBadge());
        TripRow trip = new TripRow();
        trip.setDevice(device);
        trip.setCrewBadge(booking.crewBadge());
        trip.setSite(booking.site());
        trip.setWentOut(booking.wentOut());
        source.addTrip(trip);
        work.flush();
    }

    /** {@link #bringBack(UnitOfWork, String, LocalDate, int)} with no exposures counted. */
    public static void bringBack(UnitOfWork work, String serial, LocalDate on) {
        bringBack(work, serial, on, 0);
    }

    /**
     * Close the open trip of a source, with the exposures made on it.
     *
     * @throws ValidationError for a negative exposure count, or a day before the source went out
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     * @throws ConflictError when the source is not out on a job
     */
    public static void bringBack(UnitOfWork work, String serial, LocalDate on, int exposures) {
        if (exposures < 0) {
            throw new ValidationError("an exposure count is not negative", Context.of("exposures", exposures));
        }
        TripRow trip = work.sources()
                .openTrip(serial)
                .orElseThrow(() -> new ConflictError("that source is not out on a job", Context.of("serial", serial)));
        if (on.isBefore(trip.getWentOut())) {
            throw new ValidationError("a source cannot come back before it went out", Context.of("serial", serial));
        }
        trip.setCameBack(on);
        trip.setExposures(exposures);
        work.flush();
    }

    /**
     * Record that somebody saw the source on the day of an inventory count, and where.
     *
     * @throws ValidationError for a sighting nobody signed
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static void recordSighting(UnitOfWork work, String serial, LocalDate on, String by, String where) {
        if (Text.isBlank(by)) {
            throw new ValidationError("an inventory sighting is signed");
        }
        SourceRow source = work.sources().get(serial);
        SightingRow sighting = new SightingRow();
        sighting.setSeenOn(on);
        sighting.setSeenBy(by);
        sighting.setLocation(where);
        source.addSighting(sighting);
        work.flush();
    }

    /**
     * Whether the source can go out at that moment, and every reason it cannot.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static Fitness fitness(UnitOfWork work, String serial, LocalDateTime on) {
        SealedSource source = SourceMapper.rowToSource(work.sources().get(serial));
        List<String> reasons = source.reasonsNotToUse(on);
        return new Fitness(serial, on.toLocalDate(), reasons.isEmpty(), reasons);
    }

    /**
     * The site the source is out at, or {@value #SHOP} when it is not out.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static String location(UnitOfWork work, String serial) {
        return work.sources().openTrip(serial).map(TripRow::getSite).orElse(SHOP);
    }

    /**
     * Take a source out of service. It has to be back in the shop first.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     * @throws ConflictError when the source is out on a job
     */
    public static void retire(UnitOfWork work, String serial) {
        if (work.sources().openTrip(serial).isPresent()) {
            throw new ConflictError("that source is out on a job", Context.of("serial", serial));
        }
        work.sources().get(serial).setState(SourceState.AWAITING_DISPOSAL.value());
        work.flush();
    }
}
