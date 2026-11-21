package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.clock.Moments;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.SurveyRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The daily log, which is the one document that gets read after an incident.
 *
 * <p>Everything else in this package is a record of a thing. The daily log is a record of a shift:
 * who was on it, what device went out, what was shot, what the surveys said and what went wrong. It
 * is written at the time and it is the first thing anybody asks for when something has gone wrong,
 * precisely because it was written before anybody knew that.
 *
 * <p>So it is assembled from the record rather than typed, and the parts that cannot be assembled
 * are the parts that have to be typed, which the {@link DayLog} holds. Mixing those two is what
 * produces a log that contradicts the records it sits beside.
 *
 * <p>A day is the wall clock day from {@link Moments#startOf} to {@link Moments#endOf}, both
 * included, the same slice every other day-based listing uses.
 */
public final class DayLogService {

    private DayLogService() {}

    /** Every exposure made that day, whatever job it was on, in time order and then by number. */
    public static List<RadiographRow> shotsOn(UnitOfWork work, LocalDate day) {
        return work.entityManager()
                .createQuery(
                        "select r from RadiographRow r where r.exposedAt >= :start and r.exposedAt <= :end"
                                + " order by r.exposedAt, r.number",
                        RadiographRow.class)
                .setParameter("start", Moments.startOf(day))
                .setParameter("end", Moments.endOf(day))
                .getResultList();
    }

    /** Every survey made that day, in time order. */
    public static List<SurveyRow> surveysOn(UnitOfWork work, LocalDate day) {
        return work.entityManager()
                .createQuery(
                        "select s from SurveyRow s where s.performedAt >= :start and s.performedAt <= :end"
                                + " order by s.performedAt",
                        SurveyRow.class)
                .setParameter("start", Moments.startOf(day))
                .setParameter("end", Moments.endOf(day))
                .getResultList();
    }

    /** Every weld shot that day, once each, in the order first shot: {@code GW-104}, {@code GW-104R1}. */
    public static List<String> weldsShotOn(UnitOfWork work, LocalDate day) {
        List<String> welds = new ArrayList<>();
        for (RadiographRow row : shotsOn(work, day)) {
            WeldRow weld = row.getWeld().orElseThrow();
            String label = weld.getPrefix() + "-" + weld.getNumber();
            if (weld.getRepairRound() != 0) {
                label = label + "R" + weld.getRepairRound();
            }
            if (!welds.contains(label)) {
                welds.add(label);
            }
        }
        return List.copyOf(welds);
    }

    /**
     * What the record says is missing for that day, which the log has to explain: every exposure
     * with no survey against it, and every one that was not admissible.
     */
    public static List<String> gapsOn(UnitOfWork work, LocalDate day) {
        Set<String> surveyed = surveysOn(work, day).stream()
                .flatMap(survey -> survey.getRadiographNumber().stream())
                .collect(Collectors.toSet());
        List<String> out = new ArrayList<>();
        for (RadiographRow row : shotsOn(work, day)) {
            if (!surveyed.contains(row.getNumber())) {
                out.add(row.getNumber() + " has no survey");
            }
            if (!row.isAdmissible()) {
                out.add(row.getNumber() + " was not admissible");
            }
        }
        return List.copyOf(out);
    }

    /**
     * The assembled half of the log, which nobody types, by name in the order the log prints them:
     * {@code day}, {@code site}, {@code crew}, {@code device}, {@code source}, the number of
     * {@code exposures}, the {@code welds} shot, the number of {@code surveys}, the {@code gaps}, and
     * whether the log is {@code closed}.
     */
    public static Map<String, Object> summary(UnitOfWork work, DayLog log) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("day", log.day());
        body.put("site", log.site());
        body.put("crew", log.crew());
        body.put("device", log.deviceSerial());
        body.put("source", log.sourceSerial());
        body.put("exposures", shotsOn(work, log.day()).size());
        body.put("welds", weldsShotOn(work, log.day()));
        body.put("surveys", surveysOn(work, log.day()).size());
        body.put("gaps", gapsOn(work, log.day()));
        body.put("closed", log.isClosed());
        return Collections.unmodifiableMap(body);
    }
}
