package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The morning sheet, which is one piece of paper the coordinator works from.
 *
 * <p>Everything on it already exists somewhere and that is the problem. The devices are in the
 * maintenance register, the sources are on the inventory, the films waiting to be read are on the
 * jobs, the reports are on the non conformance register, and the wipe tests are due on their own
 * schedule. Somebody was opening five listings at seven in the morning and holding the answers in
 * their head.
 *
 * <p>The sheet has one rule about ordering that is not obvious. It leads with what stops work today,
 * then what stops work this week, then what is merely late. A sheet sorted by record type puts an
 * expired wipe test under W and a truck that cannot leave under D, and the truck is the thing.
 *
 * <p>Nothing here decides anything. Every line is a question already answered elsewhere, gathered.
 */
public final class MorningService {

    /** How many days ahead counts as this week. */
    public static final int THIS_WEEK = 7;

    /** The urgency of what stops work today. */
    public static final String TODAY = "today";

    /** The urgency of what stops work within {@link #THIS_WEEK} days. */
    public static final String WEEK = "this week";

    /** The urgency of what is merely late. */
    public static final String LATE = "late";

    private MorningService() {}

    /**
     * Build the morning sheet from every register at once: each device's quarterly, each source's
     * wipe test, and each open non conformance report.
     */
    public static Sheet sheetFor(UnitOfWork work, LocalDate day) {
        List<Line> today = new ArrayList<>();
        List<Line> week = new ArrayList<>();
        List<Line> late = new ArrayList<>();

        Yard yard = Yard.load(work);
        for (String serial : yard.devices()) {
            Optional<LocalDate> due = yard.register().quarterlyDueBy(serial, day);
            if (due.isEmpty()) {
                today.add(new Line(TODAY, serial, "no quarterly inspection on record"));
            } else if (!day.isBefore(due.get())) {
                today.add(new Line(TODAY, serial, "quarterly ran out on " + due.get()));
            } else if (withinTheWeek(day, due.get())) {
                week.add(new Line(WEEK, serial, "quarterly due by " + due.get()));
            }
        }

        for (SourceRow row : work.sources().inService()) {
            Optional<LeakTest> latest = SourceMapper.rowToSource(row).latestLeakTest();
            if (latest.isEmpty()) {
                today.add(new Line(TODAY, row.getSerial(), "no wipe test on record"));
                continue;
            }
            LocalDate expires = latest.get().expiresAt().toLocalDate();
            if (!day.isBefore(expires)) {
                today.add(new Line(TODAY, row.getSerial(), "wipe test ran out on " + expires));
            } else if (withinTheWeek(day, expires)) {
                week.add(new Line(WEEK, row.getSerial(), "wipe test due by " + expires));
            }
        }

        for (Report report : work.nonconformances().openReports()) {
            if (report.isOverdue(day)) {
                late.add(new Line(
                        LATE, report.reference(), "open " + report.daysOpen(day) + " days on " + report.job()));
            } else if (report.disposition().isEmpty()) {
                week.add(new Line(WEEK, report.reference(), "undecided on " + report.job()));
            }
        }

        return new Sheet(day, today, week, late);
    }

    /** Every camera and source pairing that could actually leave that day, sorted, reading the yard once. */
    public static List<Load> loadsReady(UnitOfWork work, LocalDate day) {
        Yard yard = Yard.load(work);
        List<Load> ready = new ArrayList<>();
        for (String device : yard.devices()) {
            for (String serial : yard.sources().keySet().stream().sorted().toList()) {
                if (DispatchService.clearance(work, device, serial, day, List.of(), "yard", yard)
                        .cleared()) {
                    ready.add(new Load(device, serial));
                }
            }
        }
        return ready.stream().sorted().toList();
    }

    /** Films shot on the jobs open that day and not yet read, sorted, which is what the reader starts on. */
    public static List<String> filmsWaiting(UnitOfWork work, LocalDate day) {
        List<String> waiting = new ArrayList<>();
        for (JobRow job : work.jobs().openOn(day)) {
            work.welds().unread(job).stream().map(RadiographRow::getNumber).forEach(waiting::add);
        }
        return waiting.stream().sorted().toList();
    }

    private static boolean withinTheWeek(LocalDate day, LocalDate due) {
        return !due.isAfter(day.plusDays(THIS_WEEK));
    }
}
