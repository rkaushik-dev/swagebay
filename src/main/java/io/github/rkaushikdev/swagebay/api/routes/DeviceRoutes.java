package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Schemas.ClearanceOut;
import io.github.rkaushikdev.swagebay.api.Schemas.InspectionIn;
import io.github.rkaushikdev.swagebay.api.Schemas.InspectionOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.safety.Defect;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import io.github.rkaushikdev.swagebay.services.Clearance;
import io.github.rkaushikdev.swagebay.services.DispatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
 * Devices over HTTP: recording an inspection and asking the gate.
 *
 * <p>An inspection goes in as one post with the defects on the body, for the same reason the command
 * line takes them on one line. A form that needs three requests is a form that gets filled in later.
 *
 * <p>The clearance route is a get rather than a post because it changes nothing, and it answers
 * with the reasons rather than with a status code alone, because a dispatcher who is told no needs to
 * know which of four things to go and fix.
 */
@RestController
@RequestMapping("/devices")
@Tag(name = "devices")
public class DeviceRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public DeviceRoutes(Work work) {
        this.work = work;
    }

    /** Record a quarterly strip down or a pre use check. An item nobody inspects is a 422. */
    @PostMapping("/{serial}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionOut recordInspection(@PathVariable String serial, @Valid @RequestBody InspectionIn body) {
        return work.apply(uow -> {
            List<Defect> defects = body.defects().stream()
                    .map(defect -> new Defect(Naming.itemNamed(defect.item()), defect.account()))
                    .toList();
            Inspection inspection = body.isQuarterly()
                    ? Inspection.quarterly(serial, body.performedOn(), body.performedBy(), defects, body.notes())
                    : Inspection.preUse(serial, body.performedOn(), body.performedBy(), defects, body.notes());
            uow.maintenance().record(inspection);
            return inspectionOut(serial, inspection);
        });
    }

    /** A device's inspections, oldest first. */
    @GetMapping("/{serial}/inspections")
    public List<InspectionOut> listInspections(@PathVariable String serial) {
        return work.apply(uow -> uow.maintenance().forDevice(serial).stream()
                .map(inspection -> inspectionOut(serial, inspection))
                .toList());
    }

    /**
     * Whether the device may go out with that source on a day, today unless another is asked about,
     * and every reason it may not, kept apart by what has to be fixed.
     */
    @GetMapping("/{serial}/clearance")
    public ClearanceOut readClearance(
            @PathVariable String serial,
            @RequestParam String source,
            @RequestParam Optional<LocalDate> on,
            @RequestParam(defaultValue = "") String crew) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Clearance found = DispatchService.clearance(uow, serial, source, day, List.of(), crew);
            return new ClearanceOut(
                    serial,
                    source,
                    day,
                    found.cleared(),
                    found.sourceReasons(),
                    found.deviceReasons(),
                    found.crewReasons());
        });
    }

    private static InspectionOut inspectionOut(String serial, Inspection inspection) {
        return new InspectionOut(
                serial,
                inspection.performedOn(),
                inspection.isQuarterly(),
                inspection.passed(),
                inspection.blockingDefects().stream().map(Defect::describe).toList(),
                inspection.notedDefects().stream().map(Defect::describe).toList(),
                inspection.expiresOn());
    }
}
