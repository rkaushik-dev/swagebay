package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.LeakTestRow;
import io.github.rkaushikdev.swagebay.persistence.models.SightingRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.TripRow;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SourceState;
import io.github.rkaushikdev.swagebay.util.Text;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Leak tests, and the history sheet a regulator asks for.
 *
 * <p>A wipe test is two things at once and the software has to do both. It is a measurement, so it
 * goes on the record with its result and its date. And it is a gate, because a failing wipe takes
 * the source out of service immediately and the crew has to be told before they load it into a
 * truck.
 *
 * <p>The immediate part is the reason this is a service rather than a repository write. Recording a
 * failure changes the state of the source in the same transaction, so there is no window in which
 * the shop holds a failed wipe and a source still marked in service.
 *
 * <p>The history sheet is ordered oldest first, which is the opposite of most listings here and is
 * deliberate: a regulator reading a source history reads it forwards, from receipt to disposal, and
 * a sheet that starts at the end is a sheet somebody has to reverse by hand.
 */
public final class WipeService {

    private WipeService() {}

    /**
     * Store a wipe, and take the source out of service if it failed.
     *
     * @throws ValidationError for a wipe nobody signed, or a negative result
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static WipeResult recordWipe(
            UnitOfWork work,
            String serial,
            LocalDateTime performedAt,
            BigDecimal becquerelsRemoved,
            String technician) {
        if (Text.isBlank(technician)) {
            throw new ValidationError("a wipe test is signed");
        }
        if (becquerelsRemoved.signum() < 0) {
            throw new ValidationError(
                    "a wipe result is not negative", Context.of("removed", becquerelsRemoved.toString()));
        }
        SourceRow row = work.sources().get(serial);
        LeakTest test = new LeakTest(performedAt, becquerelsRemoved, technician);
        row.addLeakTest(SourceMapper.leakTestToRow(test));
        if (!test.passed()) {
            row.setState(SourceState.AWAITING_DISPOSAL.value());
        }
        work.flush();
        return new WipeResult(serial, test.passed(), currentUntil(performedAt), row.getState());
    }

    /**
     * When the latest passing wipe runs out, or nothing if no wipe ever passed.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static Optional<LocalDate> wipeDueOn(UnitOfWork work, String serial) {
        return work.sources().get(serial).getLeakTests().stream()
                .filter(WipeService::passed)
                .max(Comparator.comparing(LeakTestRow::getPerformedAt))
                .map(latest -> currentUntil(latest.getPerformedAt()));
    }

    /** Every source in service whose wipe has run out by that day, or that was never wiped, by serial. */
    public static List<String> wipesOverdue(UnitOfWork work, LocalDate on) {
        List<String> out = new ArrayList<>();
        for (SourceRow row : work.sources().inService()) {
            Optional<LocalDate> due = wipeDueOn(work, row.getSerial());
            if (due.isEmpty() || !due.get().isAfter(on)) {
                out.add(row.getSerial());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Everything that has happened to one source, oldest first: the assay, every wipe, every trip out
     * and back, and every sighting. Things on the same moment read in the order of what they were.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a source that is not on the inventory
     */
    public static List<HistoryEntry> history(UnitOfWork work, String serial) {
        SourceRow row = work.sources().get(serial);
        List<HistoryEntry> entries = new ArrayList<>();
        entries.add(new HistoryEntry(
                row.getAssayAt(), "assay", row.getAssayActivity().formatCurie() + " on the certificate"));
        for (LeakTestRow test : row.getLeakTests()) {
            String verdict = passed(test) ? "passed" : "failed";
            entries.add(new HistoryEntry(
                    test.getPerformedAt(),
                    "leak test",
                    verdict + ", " + test.getBecquerelsRemoved() + " Bq removed by " + test.getTechnician()));
        }
        for (TripRow trip : row.getTrips()) {
            entries.add(new HistoryEntry(
                    trip.getWentOut().atStartOfDay(),
                    "sent out",
                    "to " + trip.getSite() + " with " + trip.getCrewBadge()));
            trip.getCameBack()
                    .ifPresent(back -> entries.add(
                            new HistoryEntry(back.atStartOfDay(), "came back", trip.getExposures() + " exposures")));
        }
        for (SightingRow sighting : row.getSightings()) {
            entries.add(new HistoryEntry(
                    sighting.getSeenOn().atStartOfDay(),
                    "sighted",
                    "at " + sighting.getLocation() + " by " + sighting.getSeenBy()));
        }
        entries.sort(Comparator.comparing(HistoryEntry::at).thenComparing(HistoryEntry::what));
        return List.copyOf(entries);
    }

    private static boolean passed(LeakTestRow test) {
        return test.getBecquerelsRemoved().compareTo(LeakTest.LIMIT_BQ) < 0;
    }

    private static LocalDate currentUntil(LocalDateTime performedAt) {
        return performedAt.plusDays(LeakTest.INTERVAL_DAYS).toLocalDate();
    }
}
