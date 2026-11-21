package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.clock.Moments;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.DeviceRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.safety.MaintenanceRegister;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The morning gate: can this crew take this camera to that site today.
 *
 * <p>Every part of this answer already existed and was in a different place. The source knows
 * whether its wipe test is current. The maintenance register knows whether the quarterly ran out and
 * whether anybody looked at the drive cable that morning. The person knows whether their card and
 * their eye exam are still good. Nobody was asking all three in one go, so the yard asked them one
 * at a time and stopped at the first yes.
 *
 * <p>The gate returns every reason rather than the first, because a crew that fixes one and comes
 * back is a crew that makes two trips to the shop. The order is the order somebody can act on: the
 * ones that are somebody driving to the office with a form, then the ones that are a phone call,
 * then the ones that are a week.
 *
 * <p>It refuses to guess. A device nobody has ever inspected is not treated as inspected, and a
 * source that is not on the inventory is not treated as fit. An absent record is a refusal here,
 * which is the opposite of how most of this package treats a missing optional, and it is the right
 * way round for the one question whose wrong answer puts somebody in a field beside a live source.
 */
public final class DispatchService {

    /** What stops a booking that has nobody on it at all. */
    private static final String NOBODY_NAMED = "nobody is named on the booking";

    private DispatchService() {}

    /** {@link #clearance(UnitOfWork, String, String, LocalDate, List, String)} with nobody named. */
    public static Clearance clearance(UnitOfWork work, String deviceSerial, String sourceSerial, LocalDate day) {
        return clearance(work, deviceSerial, sourceSerial, day, List.of(), "");
    }

    /** {@link #clearance(UnitOfWork, String, String, LocalDate, List, String)} without a lead badge. */
    public static Clearance clearance(
            UnitOfWork work, String deviceSerial, String sourceSerial, LocalDate day, List<Person> crew) {
        return clearance(work, deviceSerial, sourceSerial, day, crew, "");
    }

    /**
     * Ask all three questions at once and hand back every answer, reading what each needs.
     *
     * @param crew the people on the truck, whose credentials are checked on the day
     * @param leadBadge the badge on the booking, which is enough to have somebody named when the
     *     crew is not known in full
     */
    public static Clearance clearance(
            UnitOfWork work,
            String deviceSerial,
            String sourceSerial,
            LocalDate day,
            List<Person> crew,
            String leadBadge) {
        return ask(work, deviceSerial, sourceSerial, day, crew, leadBadge, Optional.empty());
    }

    /**
     * {@link #clearance(UnitOfWork, String, String, LocalDate, List, String)} against a yard already
     * read, for a caller asking about many pairings. Asking about one load without it is three small
     * queries and that is right; asking about every load in the yard that way was three per pairing.
     */
    public static Clearance clearance(
            UnitOfWork work,
            String deviceSerial,
            String sourceSerial,
            LocalDate day,
            List<Person> crew,
            String leadBadge,
            Yard yard) {
        return ask(work, deviceSerial, sourceSerial, day, crew, leadBadge, Optional.of(yard));
    }

    /**
     * The gate as a refusal, for the callers that cannot carry on without it.
     *
     * @throws ConflictError when the load is not cleared, with the device, the source and every
     *     reason
     */
    public static Clearance requireClearance(
            UnitOfWork work,
            String deviceSerial,
            String sourceSerial,
            LocalDate day,
            List<Person> crew,
            String leadBadge) {
        Clearance found = clearance(work, deviceSerial, sourceSerial, day, crew, leadBadge);
        if (!found.cleared()) {
            throw new ConflictError(
                    "that load is not cleared to leave the shop",
                    Context.of(
                            "device",
                            deviceSerial,
                            "source",
                            sourceSerial,
                            "reasons",
                            String.join("; ", found.reasons())));
        }
        return found;
    }

    /** {@link #requireClearance(UnitOfWork, String, String, LocalDate, List, String)} without a lead badge. */
    public static Clearance requireClearance(
            UnitOfWork work, String deviceSerial, String sourceSerial, LocalDate day, List<Person> crew) {
        return requireClearance(work, deviceSerial, sourceSerial, day, crew, "");
    }

    /** {@link #clearedDevices(UnitOfWork, String, LocalDate, List)} for a crew not yet named. */
    public static List<String> clearedDevices(UnitOfWork work, String sourceSerial, LocalDate day) {
        return clearedDevices(work, sourceSerial, day, List.of());
    }

    /**
     * Which cameras this source could go out in that day, sorted, reading the yard once.
     *
     * @throws NotFoundError when the shop has no devices at all
     */
    public static List<String> clearedDevices(UnitOfWork work, String sourceSerial, LocalDate day, List<Person> crew) {
        List<DeviceRow> devices = work.sources().devices();
        if (devices.isEmpty()) {
            throw new NotFoundError("device", "any");
        }
        Yard yard = Yard.load(work);
        return devices.stream()
                .map(DeviceRow::getSerial)
                .filter(serial -> clearance(work, serial, sourceSerial, day, crew, "listing", yard)
                        .cleared())
                .sorted()
                .toList();
    }

    private static Clearance ask(
            UnitOfWork work,
            String deviceSerial,
            String sourceSerial,
            LocalDate day,
            List<Person> crew,
            String leadBadge,
            Optional<Yard> yard) {
        return new Clearance(
                deviceSerial,
                sourceSerial,
                day,
                sourceReasons(work, sourceSerial, day, yard),
                deviceReasons(work, deviceSerial, day, yard),
                crewReasons(crew, day, leadBadge));
    }

    private static List<String> sourceReasons(UnitOfWork work, String serial, LocalDate day, Optional<Yard> yard) {
        Optional<SourceRow> row = yard.isPresent()
                ? Optional.ofNullable(yard.get().sources().get(serial))
                : work.sources().find(serial);
        if (row.isEmpty()) {
            return List.of("source " + serial + " is not on the inventory");
        }
        List<String> reasons =
                new ArrayList<>(SourceMapper.rowToSource(row.get()).reasonsNotToUse(Moments.startOf(day)));
        boolean isOut = yard.isPresent()
                ? yard.get().out().contains(serial)
                : work.sources().openTrip(serial).isPresent();
        if (isOut) {
            reasons.add("source " + serial + " is already out");
        }
        return reasons.stream()
                .map(reason -> "source " + serial + ": " + reason)
                .toList();
    }

    private static List<String> deviceReasons(UnitOfWork work, String serial, LocalDate day, Optional<Yard> yard) {
        boolean known = yard.isPresent()
                ? yard.get().devices().contains(serial)
                : work.sources().findDevice(serial).isPresent();
        if (!known) {
            return List.of("device " + serial + " is not on the inventory");
        }
        MaintenanceRegister register =
                yard.isPresent() ? yard.get().register() : work.maintenance().registerFor(List.of(serial));
        return register.reasonsNotToUse(serial, day).stream()
                .map(reason -> "device " + serial + ": " + reason)
                .toList();
    }

    private static List<String> crewReasons(List<Person> crew, LocalDate day, String leadBadge) {
        if (crew.isEmpty()) {
            // A booking that carries only a badge is still a booking with somebody on it. The
            // credentials cannot be checked from a badge alone, and pretending otherwise would be a
            // check that always passes.
            return Text.isBlank(leadBadge) ? List.of(NOBODY_NAMED) : List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (Person person : crew) {
            List<String> lapsed = person.lapsedOn(day);
            if (!lapsed.isEmpty()) {
                reasons.add(person.badge() + ": " + String.join(", ", lapsed));
            }
        }
        return List.copyOf(reasons);
    }
}
