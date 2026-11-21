package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Schemas.ChecklistOut;
import io.github.rkaushikdev.swagebay.api.Schemas.DoctorFindingOut;
import io.github.rkaushikdev.swagebay.api.Schemas.DoctorOut;
import io.github.rkaushikdev.swagebay.api.Schemas.MorningOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.services.Checklist;
import io.github.rkaushikdev.swagebay.services.CloseoutService;
import io.github.rkaushikdev.swagebay.services.DoctorService;
import io.github.rkaushikdev.swagebay.services.Examination;
import io.github.rkaushikdev.swagebay.services.Line;
import io.github.rkaushikdev.swagebay.services.MorningService;
import io.github.rkaushikdev.swagebay.services.Sheet;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operational routes: the morning, the checklist and the doctor.
 *
 * <p>None of these belong to one record, which is why they do not hang off another router. They
 * answer questions about the whole shop on a day, and a dispatcher's screen refreshing every few
 * minutes is the only caller any of them will ever have.
 *
 * <p>All three are gets and none of them writes. The doctor in particular reads a lot and changes
 * nothing, so it is safe to point a health check at it, and the response says which checks ran
 * rather than just whether it was happy.
 */
@RestController
@Tag(name = "shop")
public class ShopRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public ShopRoutes(Work work) {
        this.work = work;
    }

    /**
     * The morning sheet for a day, today unless another is asked about. The loads ready to leave are
     * listed only with {@code ?loads=true}, because working them out reads the whole yard.
     */
    @GetMapping("/morning")
    public MorningOut readMorning(
            @RequestParam Optional<LocalDate> on, @RequestParam(defaultValue = "false") boolean loads) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Sheet sheet = MorningService.sheetFor(uow, day);
            return new MorningOut(
                    day,
                    describe(sheet.stopsToday()),
                    describe(sheet.stopsThisWeek()),
                    describe(sheet.runningLate()),
                    MorningService.filmsWaiting(uow, day),
                    loads
                            ? MorningService.loadsReady(uow, day).stream()
                                    .map(load -> load.deviceSerial() + " with " + load.sourceSerial())
                                    .toList()
                            : List.of(),
                    sheet.isQuiet());
        });
    }

    /** What stands between a job and its close, kept apart by who has to fix it. */
    @GetMapping("/jobs/{number}/checklist")
    public ChecklistOut readChecklist(@PathVariable String number) {
        return work.apply(uow -> {
            Checklist found = CloseoutService.checklist(uow, JobNumber.parse(number));
            return new ChecklistOut(
                    number, found.isClear(), found.workReasons(), found.reportReasons(), found.imageReasons());
        });
    }

    /** Run the named checks, {@code ?check=self closed} once for each, or every check when none is named. */
    @GetMapping("/doctor")
    public DoctorOut readDoctor(@RequestParam Optional<List<String>> check) {
        return work.apply(uow -> {
            Examination found = DoctorService.examine(uow, check.orElse(List.of()));
            return new DoctorOut(
                    found.checksRun(),
                    DoctorService.checkNames(),
                    found.isHealthy(),
                    found.findings().stream()
                            .map(finding -> new DoctorFindingOut(finding.check(), finding.subject(), finding.account()))
                            .toList());
        });
    }

    private static List<String> describe(List<Line> lines) {
        return lines.stream().map(Line::describe).toList();
    }
}
