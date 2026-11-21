package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.Numbering;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.CounterRow;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.NonconformanceRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Checks that look for records which cannot be true at the same time.
 *
 * <p>Every rule in this package is enforced on the way in. That is the right place for it and it is
 * not enough, because a database outlives the code that wrote it. Rows get in through a migration,
 * through an import, through somebody with a console, and through a version of this software that
 * did not have the rule yet. The constraint added on a Tuesday does not go back and check Monday.
 *
 * <p>So this reads what is there and says what cannot be true. It is not a repair tool and it does
 * not offer to fix anything, because every one of these has more than one possible cause and picking
 * one automatically is how a shop loses a radiograph rather than finds out about it.
 *
 * <p>Each check is separate and each returns its own findings, so a shop with one bad row does not
 * get a single line saying the database is unhealthy.
 */
public final class DoctorService {

    /** Every check by name, in the order {@link #examine} runs them. */
    private static final Map<String, Function<UnitOfWork, List<Finding>>> CHECKS = checks();

    private DoctorService() {}

    /** A source cannot be out on two jobs, and the trip table cannot say so. */
    public static List<Finding> doubleBookings(UnitOfWork work) {
        return work
                .entityManager()
                .createQuery(
                        "select s.serial, count(t) from TripRow t join t.source s where t.cameBack is null"
                                + " group by s.serial having count(t) > 1 order by s.serial",
                        Tuple.class)
                .getResultList()
                .stream()
                .map(row -> new Finding(
                        "double booking",
                        row.get(0, String.class),
                        "is out on " + row.get(1, Long.class) + " open trips"))
                .toList();
    }

    /**
     * A radiograph number at or above the counter of its job, or films on a job with no counter at
     * all.
     *
     * <p>Either one means a number was handed out by something other than the counter, which is the
     * failure the counter design exists to prevent and the only one that cannot be seen by looking at
     * a single row.
     */
    public static List<Finding> numberingGaps(UnitOfWork work) {
        EntityManager entityManager = work.entityManager();
        Map<List<String>, Integer> counters = new HashMap<>();
        for (CounterRow row : entityManager
                .createQuery("select c from CounterRow c", CounterRow.class)
                .getResultList()) {
            counters.put(List.of(row.getName(), row.getScope()), row.getNextValue());
        }
        List<Finding> findings = new ArrayList<>();
        for (JobRow job : entityManager
                .createQuery("select j from JobRow j order by j.id", JobRow.class)
                .getResultList()) {
            String number = new JobNumber(job.getYear(), job.getSerial()).toString();
            List<String> issued = entityManager
                    .createQuery("select r.number from RadiographRow r join r.weld w where w.job = :job", String.class)
                    .setParameter("job", job)
                    .getResultList();
            if (issued.isEmpty()) {
                continue;
            }
            int highest =
                    issued.stream().mapToInt(DoctorService::issuedSerial).max().orElseThrow();
            Integer held = counters.get(List.of(Numbering.RADIOGRAPH, number));
            if (held == null) {
                findings.add(new Finding("numbering", number, "has " + issued.size() + " films and no counter"));
            } else if (highest >= held) {
                findings.add(new Finding(
                        "numbering",
                        number,
                        String.format(Locale.ROOT, "issued R%04d past a counter at %d", highest, held)));
            }
        }
        return List.copyOf(findings);
    }

    /** A file in the store against an exposure that is not there any more. */
    public static List<Finding> orphanedImages(UnitOfWork work) {
        return work
                .entityManager()
                .createQuery(
                        "select s.filename from StoredImageRow s left join RadiographRow r on r.id = s.radiograph.id"
                                + " where r.id is null order by s.id",
                        String.class)
                .getResultList()
                .stream()
                .map(name -> new Finding("orphaned image", name, "has no radiograph behind it"))
                .toList();
    }

    /** An inspection against a device nobody put on the inventory. */
    public static List<Finding> unknownDevices(UnitOfWork work) {
        EntityManager entityManager = work.entityManager();
        Set<String> known = new TreeSet<>(entityManager
                .createQuery("select d.serial from DeviceRow d", String.class)
                .getResultList());
        Set<String> inspected = new TreeSet<>(entityManager
                .createQuery("select distinct i.deviceSerial from InspectionRow i", String.class)
                .getResultList());
        inspected.removeAll(known);
        return inspected.stream()
                .map(serial -> new Finding("unknown device", serial, "has inspections and is not on the inventory"))
                .toList();
    }

    /** A report closed by the person who raised it, from before that was refused. */
    public static List<Finding> selfClosedReports(UnitOfWork work) {
        return reports(
                        work,
                        "select n from NonconformanceRow n where n.closedBy is not null and n.closedBy = n.raisedBy"
                                + " order by n.id")
                .stream()
                .map(row -> new Finding(
                        "self closed",
                        row.getReference(),
                        "was closed by " + row.getClosedBy().orElseThrow() + " who raised it"))
                .toList();
    }

    /** A report closed without a disposition, which decides nothing. */
    public static List<Finding> undecidedClosures(UnitOfWork work) {
        return reports(
                        work,
                        "select n from NonconformanceRow n where n.closedOn is not null and n.disposition is null"
                                + " order by n.id")
                .stream()
                .map(row -> new Finding("undecided closure", row.getReference(), "is closed with nothing decided"))
                .toList();
    }

    /** Run every check, in order, and gather what they found. */
    public static Examination examine(UnitOfWork work) {
        return examine(work, List.of());
    }

    /**
     * Run the named checks, in the order they always run, and gather what they found. No names runs
     * every check; a name that is not a check runs nothing.
     */
    public static Examination examine(UnitOfWork work, List<String> only) {
        List<String> chosen = CHECKS.keySet().stream()
                .filter(name -> only.isEmpty() || only.contains(name))
                .toList();
        List<Finding> findings = new ArrayList<>();
        for (String name : chosen) {
            findings.addAll(CHECKS.get(name).apply(work));
        }
        return new Examination(findings, chosen);
    }

    /** The name of every check, in the order they run. */
    public static List<String> checkNames() {
        return List.copyOf(CHECKS.keySet());
    }

    private static Map<String, Function<UnitOfWork, List<Finding>>> checks() {
        Map<String, Function<UnitOfWork, List<Finding>>> checks = new LinkedHashMap<>();
        checks.put("double booking", DoctorService::doubleBookings);
        checks.put("numbering", DoctorService::numberingGaps);
        checks.put("orphaned image", DoctorService::orphanedImages);
        checks.put("unknown device", DoctorService::unknownDevices);
        checks.put("self closed", DoctorService::selfClosedReports);
        checks.put("undecided closure", DoctorService::undecidedClosures);
        return Collections.unmodifiableMap(checks);
    }

    /** The serial of an issued radiograph number: whatever follows its last {@code R}. */
    private static int issuedSerial(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('R') + 1));
    }

    private static List<NonconformanceRow> reports(UnitOfWork work, String query) {
        return work.entityManager().createQuery(query, NonconformanceRow.class).getResultList();
    }
}
