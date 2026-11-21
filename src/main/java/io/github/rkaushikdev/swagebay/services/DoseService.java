package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.DoseEntryRow;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.safety.WorkerClass;
import io.github.rkaushikdev.swagebay.util.Decimals;
import io.github.rkaushikdev.swagebay.util.Text;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

/**
 * Dose reporting, which is a monthly job and an annual argument.
 *
 * <p>The ledger holds entries. Everything anybody wants to see is a sum over it, and the sums are
 * all slightly different, which is why they are here rather than in the ledger.
 *
 * <p>The monthly report is per person for one month and it is what gets posted on the board. The
 * annual position is per person for the year to date and it is what the investigation levels are
 * read against. The two disagree constantly and neither is wrong: somebody can have a quiet year and
 * a bad month.
 *
 * <p>Entries are never edited. A correction is another entry with a reason rather than a change to
 * the first one. So the ledger only ever grows, and the sum is always the truth as of the moment it
 * was taken.
 */
public final class DoseService {

    private DoseService() {}

    /**
     * Append an entry to the ledger. Nothing here ever edits one.
     *
     * @param year the dose year the entry counts towards
     * @param sourceOfEntry where the figure came from: the badge laboratory, a direct reading
     * @throws ValidationError for an entry with no badge, or that does not say where it came from
     */
    public static void recordDose(
            UnitOfWork work, String badge, int year, Dose taken, LocalDateTime recordedAt, String sourceOfEntry) {
        if (Text.isBlank(badge)) {
            throw new ValidationError("a dose is recorded against a badge");
        }
        if (Text.isBlank(sourceOfEntry)) {
            throw new ValidationError("a dose entry says where it came from");
        }
        DoseEntryRow row = new DoseEntryRow();
        row.setBadge(badge);
        row.setYear(year);
        row.setTaken(taken);
        row.setRecordedAt(recordedAt);
        row.setSourceOfEntry(sourceOfEntry);
        work.entityManager().persist(row);
        work.flush();
    }

    /** Everything one person has on the ledger for the dose year. */
    public static Dose takenInYear(UnitOfWork work, String badge, int year) {
        return sum(work.entityManager()
                .createQuery(
                        "select sum(d.taken) from DoseEntryRow d where d.badge = :badge and d.year = :year", Long.class)
                .setParameter("badge", badge)
                .setParameter("year", year));
    }

    /**
     * Everything one person had recorded in one calendar month, whichever dose year it counts towards.
     *
     * @throws ValidationError for a month outside one to twelve
     */
    public static Dose takenInMonth(UnitOfWork work, String badge, int year, int month) {
        if (month < 1 || month > 12) {
            throw new ValidationError("a month is one to twelve", Context.of("month", month));
        }
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1);
        return sum(work.entityManager()
                .createQuery(
                        "select sum(d.taken) from DoseEntryRow d where d.badge = :badge"
                                + " and d.recordedAt >= :start and d.recordedAt < :end",
                        Long.class)
                .setParameter("badge", badge)
                .setParameter("start", start)
                .setParameter("end", end));
    }

    /** {@link #position(UnitOfWork, String, int, WorkerClass)} for an adult worker. */
    public static DosePosition position(UnitOfWork work, String badge, int year) {
        return position(work, badge, year, WorkerClass.ADULT);
    }

    /** One person's position for the year, against the limit of their class of worker. */
    public static DosePosition position(UnitOfWork work, String badge, int year, WorkerClass workerClass) {
        return new DosePosition(badge, year, takenInYear(work, badge, year), workerClass.limit(), workerClass);
    }

    /** Every badge with an entry in the dose year, sorted. */
    public static List<String> badgesInYear(UnitOfWork work, int year) {
        return work
                .entityManager()
                .createQuery("select distinct d.badge from DoseEntryRow d where d.year = :year", String.class)
                .setParameter("year", year)
                .getResultList()
                .stream()
                .sorted()
                .toList();
    }

    /** {@link #overInvestigationLevel(UnitOfWork, int, int)} at the first level. */
    public static List<DosePosition> overInvestigationLevel(UnitOfWork work, int year) {
        return overInvestigationLevel(work, year, 1);
    }

    /**
     * Everybody at or over an investigation level for the year, read against the adult limit, the
     * most dose first.
     *
     * @throws ValidationError for a level other than one or two
     */
    public static List<DosePosition> overInvestigationLevel(UnitOfWork work, int year, int level) {
        if (level != 1 && level != 2) {
            throw new ValidationError("there are two investigation levels", Context.of("level", level));
        }
        return badgesInYear(work, year).stream()
                .map(badge -> position(work, badge, year))
                .filter(found -> found.investigationLevel() >= level)
                .sorted(Comparator.comparingLong(
                                (DosePosition found) -> found.taken().nsv())
                        .reversed())
                .toList();
    }

    /** What goes on the board for the month: every badge with an entry in the year, in badge order. */
    public static List<BadgeDose> monthlySheet(UnitOfWork work, int year, int month) {
        return badgesInYear(work, year).stream()
                .map(badge -> new BadgeDose(badge, takenInMonth(work, badge, year, month)))
                .toList();
    }

    /** The whole shop for the dose year, which is the number the licence reports. */
    public static Dose collectiveDose(UnitOfWork work, int year) {
        return sum(work.entityManager()
                .createQuery("select sum(d.taken) from DoseEntryRow d where d.year = :year", Long.class)
                .setParameter("year", year));
    }

    /**
     * A crude burn down, which is what a supervisor actually wants in June: the whole days of the
     * year left before the limit is reached at the rate so far, or nothing for somebody who has taken
     * nothing.
     *
     * @throws ValidationError when none of the year has elapsed
     */
    public static OptionalLong daysLeftAtThisRate(DosePosition position, int daysElapsed) {
        if (daysElapsed <= 0) {
            throw new ValidationError("some of the year has to have elapsed", Context.of("days", daysElapsed));
        }
        if (position.taken().nsv() == 0) {
            return OptionalLong.empty();
        }
        BigDecimal perDay = Decimals.divide(Decimals.of(position.taken().nsv()), daysElapsed);
        return OptionalLong.of(Decimals.truncate(
                Decimals.divide(Decimals.of(position.remaining().nsv()), perDay)));
    }

    private static Dose sum(TypedQuery<Long> query) {
        Long total = query.getSingleResult();
        return total == null ? Dose.ZERO : new Dose(total);
    }
}
