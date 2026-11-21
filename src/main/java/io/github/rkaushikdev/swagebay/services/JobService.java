package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.JobRepository;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.WeldRepository;
import io.github.rkaushikdev.swagebay.persistence.WorkMapper;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Opening a job, adding welds to it, and closing it.
 *
 * <p>A service is a use case with a transaction round it, and that is all it is. It does not
 * compute anything the domain can compute, and it does not query anything the repositories can
 * query. What it owns is the order things happen in and what happens when one of them fails. Every
 * function here works inside the caller's unit of work and flushes, and the caller commits.
 *
 * <p>Opening a job takes the next serial for the year and writes the job in the same transaction,
 * which is the only way the serial cannot be handed out twice. Adding a weld checks the job is open
 * first, because a weld added to a closed job is a weld nobody will look for again.
 *
 * <p>Closing a job is the one with a real rule in it. A job closes when every weld on it has a
 * disposition, and a weld with an unread radiograph does not have one. So closing reports what is
 * outstanding rather than refusing with a bare no, because the coordinator closing the job needs
 * the list.
 */
public final class JobService {

    private JobService() {}

    /** Take the next serial for the year the job opens in, and write the job with it. */
    public static JobNumber openJob(UnitOfWork work, NewJob request) {
        JobRepository jobs = work.jobs();
        int year = request.openedOn().getYear() % 100;
        int serial = jobs.nextSerial(year);
        JobNumber number = new JobNumber(year, serial);
        JobRow job = new JobRow();
        job.setYear(number.year());
        job.setSerial(number.serial());
        job.setClient(request.client());
        job.setSite(request.site());
        job.setAcceptanceCode(request.code().value());
        job.setProcedureNumber(request.procedureNumber());
        job.setOpenedOn(request.openedOn());
        jobs.add(job);
        work.flush();
        return number;
    }

    /**
     * Add a weld to an open job.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     * @throws ConflictError when the job is closed, or already has that weld in that repair round
     */
    public static void addWeld(UnitOfWork work, JobNumber number, NewWeld request) {
        WeldRepository welds = work.welds();
        JobRow job = work.jobs().get(number);
        if (job.getClosedOn().isPresent()) {
            throw new ConflictError("that job is closed", Context.of("job", number.toString()));
        }
        if (welds.find(job, request.weld()).isPresent()) {
            throw new ConflictError(
                    "that weld is already on the job",
                    Context.of("weld", request.weld().toString()));
        }
        WeldRow row = WorkMapper.jointToRow(request.joint(), request.weld(), new WeldRow());
        row.setWeldLength(request.weldLength());
        row.setWelder(request.welder());
        row.setWeldedOn(request.weldedOn());
        job.addWeld(row);
        work.flush();
    }

    /**
     * Everything about the welds and films standing between the job and being closed: a job with no
     * welds, every film that is not admissible, every film not yet read, and every weld nobody has
     * shot.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static List<String> outstanding(UnitOfWork work, JobNumber number) {
        WeldRepository welds = work.welds();
        JobRow job = work.jobs().get(number);
        List<String> out = new ArrayList<>();
        if (job.getWelds().isEmpty()) {
            out.add("the job has no welds on it");
        }
        for (RadiographRow radiograph : welds.inadmissible(job)) {
            out.add(radiograph.getNumber() + " is not admissible");
        }
        for (RadiographRow radiograph : welds.unread(job)) {
            out.add(radiograph.getNumber() + " has not been read");
        }
        for (WeldRow weld : job.getWelds()) {
            if (weld.getRadiographs().isEmpty()) {
                out.add("GW-" + weld.getNumber() + " has no radiographs");
            }
        }
        return List.copyOf(out);
    }

    /**
     * Close a job, refusing while anything on the checklist is outstanding.
     *
     * <p>The list of things that count is in {@link CloseoutService} rather than here, because it
     * has grown past welds and films and every place that prints it needs the same list this refuses
     * on.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     * @throws ConflictError when the job is already closed, or anything on the checklist is
     *     outstanding, with how many and every reason
     * @throws ValidationError for a day before the job opened
     */
    public static void closeJob(UnitOfWork work, JobNumber number, LocalDate on) {
        JobRow job = work.jobs().get(number);
        if (job.getClosedOn().isPresent()) {
            throw new ConflictError("that job is already closed", Context.of("job", number.toString()));
        }
        if (on.isBefore(job.getOpenedOn())) {
            throw new ValidationError("a job cannot close before it opened", Context.of("job", number.toString()));
        }
        Checklist found = CloseoutService.checklist(work, number);
        if (!found.isClear()) {
            throw new ConflictError(
                    "the job is not finished",
                    Context.of(
                            "job",
                            number.toString(),
                            "outstanding",
                            found.reasons().size(),
                            "reasons",
                            String.join("; ", found.reasons())));
        }
        job.setClosedOn(on);
        work.flush();
    }
}
