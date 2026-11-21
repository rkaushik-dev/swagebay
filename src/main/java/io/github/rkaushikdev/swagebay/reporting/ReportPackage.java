package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.evaluation.Acceptance;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.IndicationSet;
import io.github.rkaushikdev.swagebay.evaluation.Verdict;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.WorkMapper;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The report package, which is the thing the client actually pays for.
 *
 * <p>A job produces a stack of paper and the stack has to be complete, internally consistent and
 * defensible on its own without anybody being available to explain it. That is a higher bar than
 * the software needs to work, and the gap between the two is where most disputes live.
 *
 * <p>Complete means every weld on the job has a disposition, every disposition is supported by
 * radiographs that were admissible, and every radiograph names the technique it was shot to. Missing
 * any one of those makes the package unfinishable rather than merely incomplete, and the faults say
 * which.
 *
 * <p>Consistent means the numbers agree with each other. The count of radiographs on the summary
 * equals the number of radiograph lines. The repair rate equals the repaired welds over the welds
 * shot. Somebody will check, and finding it wrong costs more than the whole job.
 *
 * <p>The package is built by {@link #assemble} rather than stored. Anything stored can drift from the
 * record it summarises, and a summary that disagrees with its own detail is worse than no summary.
 *
 * @param job the job
 * @param client who the work is for
 * @param site where it was done
 * @param code the acceptance code the welds were judged to
 * @param procedureNumber the procedure the work was done to
 * @param openedOn the day the job opened
 * @param closedOn the day it closed, while it is open nothing
 * @param welds the weld summary, by weld number and then repair round
 * @param radiographs the radiograph schedule, weld by weld and exposure by exposure
 * @param faults everything that stops the package being sent, in the order found
 * @param reports the non conformance reports raised against the job
 * @param files the job's files in the image store, by file name
 */
public record ReportPackage(
        JobNumber job,
        String client,
        String site,
        Code code,
        String procedureNumber,
        LocalDate openedOn,
        Optional<LocalDate> closedOn,
        List<WeldLine> welds,
        List<RadiographLine> radiographs,
        List<String> faults,
        List<Report> reports,
        List<StoredImage> files) {

    private static final Comparator<WeldRow> WELD_ORDER =
            Comparator.comparingInt(WeldRow::getNumber).thenComparingInt(WeldRow::getRepairRound);

    /** A package; the lists are copied. */
    public ReportPackage {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(procedureNumber, "procedureNumber");
        Objects.requireNonNull(openedOn, "openedOn");
        Objects.requireNonNull(closedOn, "closedOn");
        welds = List.copyOf(welds);
        radiographs = List.copyOf(radiographs);
        faults = List.copyOf(faults);
        reports = List.copyOf(reports);
        files = List.copyOf(files);
    }

    /** A package with no reports raised against the job and no files in the store. */
    public ReportPackage(
            JobNumber job,
            String client,
            String site,
            Code code,
            String procedureNumber,
            LocalDate openedOn,
            Optional<LocalDate> closedOn,
            List<WeldLine> welds,
            List<RadiographLine> radiographs,
            List<String> faults) {
        this(
                job,
                client,
                site,
                code,
                procedureNumber,
                openedOn,
                closedOn,
                welds,
                radiographs,
                faults,
                List.of(),
                List.of());
    }

    /**
     * Build the package from the record, and say what is missing.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static ReportPackage assemble(UnitOfWork work, JobNumber number) {
        JobRow row = work.jobs().get(number);
        Code code = Naming.codeNamed(row.getAcceptanceCode());

        List<WeldLine> weldLines = new ArrayList<>();
        List<RadiographLine> radiographLines = new ArrayList<>();
        List<String> faults = new ArrayList<>();

        for (WeldRow weld : row.getWelds().stream().sorted(WELD_ORDER).toList()) {
            String identity = new WeldId(weld.getPrefix(), weld.getNumber(), weld.getRepairRound()).toString();
            Optional<Verdict> verdict = verdict(weld, code);
            if (verdict.isEmpty()) {
                faults.add(identity + " has no disposition");
            }
            List<RadiographRow> films = weld.getRadiographs().stream()
                    .sorted(Comparator.comparingInt(RadiographRow::getExposureIndex))
                    .toList();
            for (RadiographRow film : films) {
                if (!film.isAdmissible()) {
                    faults.add(film.getNumber() + " is not admissible");
                }
                if (film.getReadAt().isEmpty()) {
                    faults.add(film.getNumber() + " has not been read");
                }
                if (Text.isBlank(film.getTechniqueReference())) {
                    faults.add(film.getNumber() + " names no technique");
                }
                radiographLines.add(new RadiographLine(
                        film.getNumber(),
                        identity,
                        film.getExposureIndex(),
                        film.getExposedAt().toLocalDate(),
                        film.getTechniqueReference(),
                        film.getMedium(),
                        film.isAdmissible(),
                        film.getReadBy(),
                        film.getIndications().size()));
            }
            if (films.isEmpty()) {
                faults.add(identity + " has no radiographs");
            }
            weldLines.add(new WeldLine(
                    identity,
                    weld.getWall(),
                    weld.getWelder(),
                    films.size(),
                    verdict.map(found -> found.disposition().value()).orElse("none"),
                    verdict.map(Verdict::clauses).orElse(List.of())));
        }

        if (row.getWelds().isEmpty()) {
            faults.add("the job has no welds on it");
        }

        // The reports and the files come from their own registers rather than off the job row, and
        // they are gathered here so that everything the client is sent is assembled in one place and
        // can be counted once.
        List<Report> reports = work.nonconformances().forJob(number.toString());
        for (Report report : reports) {
            if (report.isOpen()) {
                faults.add(report.reference() + " is still open");
            }
        }
        Manifest manifest = work.store().manifestFor(number);
        List<StoredImage> files = manifest.entries().keySet().stream()
                .sorted()
                .map(manifest.entries()::get)
                .toList();
        faults.addAll(manifest.faults());

        return new ReportPackage(
                number,
                row.getClient(),
                row.getSite(),
                code,
                row.getProcedureNumber(),
                row.getOpenedOn(),
                row.getClosedOn(),
                weldLines,
                radiographLines,
                faults,
                reports,
                files);
    }

    /** Whether nothing stops the package being sent. */
    public boolean isComplete() {
        return faults.isEmpty();
    }

    /** How many welds have at least one film. */
    public int weldsShot() {
        return (int) welds.stream().filter(line -> line.radiographs() > 0).count();
    }

    /** How many welds were repaired: every weld with a repair round, counted once however many rounds it took. */
    public int weldsRepaired() {
        return (int) welds.stream()
                .map(WeldLine::weld)
                .filter(weld -> weld.contains("R"))
                .map(weld -> weld.substring(0, weld.indexOf('R')))
                .distinct()
                .count();
    }

    /**
     * The repair rate with the count it came from, never the bare number.
     *
     * <p>A bare number went out on the summary sheet as four places whatever the denominator was, and
     * a client read 0.5000 off a job with two welds on it and asked why the repair rate had gone up by
     * a factor of ten. The {@link Rate} carries the count, so the sheet can decline to state a
     * percentage that means nothing.
     */
    public Rate repairRate() {
        return new Rate(weldsRepaired(), weldsShot());
    }

    /** Whether the summary and the detail add up to the same thing: the films per weld against the schedule. */
    public boolean countsAgree() {
        int summed = welds.stream().mapToInt(WeldLine::radiographs).sum();
        return summed == radiographs.size();
    }

    /** The disposition of a weld, or nothing while it has no films or any is unread or inadmissible. */
    private static Optional<Verdict> verdict(WeldRow weld, Code code) {
        List<RadiographRow> films = weld.getRadiographs();
        if (films.isEmpty()) {
            return Optional.empty();
        }
        if (films.stream()
                .anyMatch(film -> !film.isAdmissible() || film.getReadAt().isEmpty())) {
            return Optional.empty();
        }
        List<Indication> found = films.stream()
                .flatMap(film -> film.getIndications().stream())
                .map(WorkMapper::rowToIndication)
                .toList();
        return Optional.of(Acceptance.judge(code, new IndicationSet(weld.getWeldLength(), found), weld.getWall()));
    }
}
