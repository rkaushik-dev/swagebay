package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Page;
import io.github.rkaushikdev.swagebay.api.Paging;
import io.github.rkaushikdev.swagebay.api.Schemas.JobOut;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthIn;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthOut;
import io.github.rkaushikdev.swagebay.api.Schemas.NewJobIn;
import io.github.rkaushikdev.swagebay.api.Schemas.NewWeldIn;
import io.github.rkaushikdev.swagebay.api.Schemas.OutstandingOut;
import io.github.rkaushikdev.swagebay.api.Schemas.RadiographOut;
import io.github.rkaushikdev.swagebay.api.Schemas.RadiographPageOut;
import io.github.rkaushikdev.swagebay.api.Schemas.WeldOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jobs over HTTP.
 *
 * <p>Every handler is a few lines and that is the target. Parse the path value into the domain type,
 * call the service, shape the result. Anything longer than that means a decision has leaked out of
 * the service and into the transport, and the first sign of it is always a handler with an if in it.
 *
 * <p>The job number arrives as text in the path and is parsed by the same grammar the rest of the
 * package uses, so a malformed number is refused with the same message a malformed number in a
 * spreadsheet import gets. That is worth more than a neat 404, because the caller learns what shape
 * the thing should have been.
 */
@RestController
@RequestMapping("/jobs")
@Tag(name = "jobs")
public class JobRoutes {

    private static final Comparator<WeldRow> WELD_ORDER =
            Comparator.comparingInt(WeldRow::getNumber).thenComparingInt(WeldRow::getRepairRound);

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public JobRoutes(Work work) {
        this.work = work;
    }

    /** Open a job under the next number of the year it opens in. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobOut createJob(@Valid @RequestBody NewJobIn body) {
        return work.apply(uow -> {
            JobNumber number = JobService.openJob(
                    uow,
                    new NewJob(
                            body.client(),
                            body.site(),
                            Naming.codeNamed(body.acceptanceCode()),
                            body.procedureNumber(),
                            body.openedOn()));
            return jobOut(uow.jobs().get(number));
        });
    }

    /** One job. A number nobody opened is a 404, and a number that is not one is a 422. */
    @GetMapping("/{number}")
    public JobOut readJob(@PathVariable String number) {
        return work.apply(uow -> jobOut(uow.jobs().get(JobNumber.parse(number))));
    }

    /** The jobs open on a day, today unless asked about another. */
    @GetMapping
    public List<JobOut> listJobs(@RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(
                uow -> uow.jobs().openOn(day).stream().map(JobRoutes::jobOut).toList());
    }

    /** Add a weld to an open job. The same weld twice is a 409. */
    @PostMapping("/{number}/welds")
    @ResponseStatus(HttpStatus.CREATED)
    public WeldOut createWeld(@PathVariable String number, @Valid @RequestBody NewWeldIn body) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            JobService.addWeld(
                    uow,
                    job,
                    new NewWeld(
                            WeldId.parse(body.weld()),
                            joint(body),
                            body.weldLength().toLength(),
                            body.welder(),
                            body.weldedOn()));
            return weldOut(uow.welds().get(uow.jobs().get(job), WeldId.parse(body.weld())));
        });
    }

    /** The welds on a job, by number and then repair round. */
    @GetMapping("/{number}/welds")
    public List<WeldOut> listWelds(@PathVariable String number) {
        return work.apply(uow -> uow.jobs().get(JobNumber.parse(number)).getWelds().stream()
                .sorted(WELD_ORDER)
                .map(JobRoutes::weldOut)
                .toList());
    }

    /** One page of a job's radiographs in number order, starting after the cursor. */
    @GetMapping("/{number}/radiographs")
    public RadiographPageOut listRadiographs(
            @PathVariable String number, @RequestParam Optional<String> cursor, @RequestParam Optional<Integer> size) {
        return work.apply(uow -> {
            JobRow job = uow.jobs().get(JobNumber.parse(number));
            List<RadiographRow> rows = uow.welds().radiographsAfter(job, Paging.after(cursor), Paging.fetchSize(size));
            Page<RadiographRow> page = Paging.paginate(rows, RadiographRow::getNumber, size);
            return new RadiographPageOut(
                    page.items().stream().map(JobRoutes::radiographOut).toList(), page.nextCursor(), page.isLast());
        });
    }

    /** What still stands between the job and being closed. */
    @GetMapping("/{number}/outstanding")
    public OutstandingOut readOutstanding(@PathVariable String number) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            return new OutstandingOut(job.toString(), JobService.outstanding(uow, job));
        });
    }

    /** Close a job on a day, today unless another is given. A job with anything outstanding is a 409. */
    @PostMapping("/{number}/close")
    public JobOut close(@PathVariable String number, @RequestParam Optional<LocalDate> on) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            JobService.closeJob(uow, job, on.orElseGet(LocalDate::now));
            return jobOut(uow.jobs().get(job));
        });
    }

    /** The joint a weld request describes: pipe when it gives a diameter, plate when it does not. */
    private static Joint joint(NewWeldIn body) {
        Optional<Length> diameter = body.joint().outsideDiameter().map(LengthIn::toLength);
        return new Joint(
                diameter.isEmpty() ? JointForm.PLATE_BUTT : JointForm.PIPE_GIRTH,
                body.joint().wall().toLength(),
                Materials.material(body.joint().material()),
                diameter,
                body.joint().reinforcement().map(LengthIn::toLength).orElse(Length.ZERO),
                Optional.empty());
    }

    private static JobOut jobOut(JobRow row) {
        return new JobOut(
                new JobNumber(row.getYear(), row.getSerial()).toString(),
                row.getClient(),
                row.getSite(),
                row.getAcceptanceCode(),
                row.getProcedureNumber(),
                row.getOpenedOn(),
                row.getClosedOn(),
                row.getWelds().size());
    }

    private static WeldOut weldOut(WeldRow row) {
        return new WeldOut(
                new WeldId(row.getPrefix(), row.getNumber(), row.getRepairRound()).toString(),
                row.getMaterialKey(),
                LengthOut.of(row.getWall()),
                row.getOutsideDiameter().map(LengthOut::of),
                LengthOut.of(row.getReinforcement()),
                LengthOut.of(row.getWeldLength()),
                row.getWelder(),
                row.getWeldedOn(),
                row.getRadiographs().size());
    }

    private static RadiographOut radiographOut(RadiographRow row) {
        WeldRow weld = row.getWeld().orElseThrow();
        return new RadiographOut(
                row.getNumber(),
                new WeldId(weld.getPrefix(), weld.getNumber(), weld.getRepairRound()).toString(),
                row.getExposureIndex(),
                row.getExposedAt(),
                row.getTechniqueReference(),
                row.getMedium(),
                row.isAdmissible(),
                row.getInadmissibleReason(),
                row.getReadBy(),
                row.getReadAt(),
                row.getIndications().size());
    }
}
