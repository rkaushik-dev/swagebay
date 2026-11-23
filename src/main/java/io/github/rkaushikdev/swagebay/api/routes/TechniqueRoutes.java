package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.api.Schemas.ActivityOut;
import io.github.rkaushikdev.swagebay.api.Schemas.CoverageOut;
import io.github.rkaushikdev.swagebay.api.Schemas.DurationOut;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthOut;
import io.github.rkaushikdev.swagebay.api.Schemas.PlanIn;
import io.github.rkaushikdev.swagebay.api.Schemas.SheetOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.services.PlanRequest;
import io.github.rkaushikdev.swagebay.services.PlanningService;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Technique sheets over HTTP.
 *
 * <p>The shot type is optional in the request and that is the useful shape. Left out, the planner
 * picks the best shot the geometry allows, which is what a coordinator wants when they are laying out
 * a job. Named, the planner uses it, which is what a shooter wants when the site has already decided
 * that the source is not going inside anything.
 *
 * <p>The comparison route exists because picking a film is a real decision made several times a
 * night, and doing it by calling the planner four times from a phone is how it gets done wrong.
 * Both it and the fastest film route take the films as {@code ?films=d7,d5,d4}.
 */
@RestController
@RequestMapping("/jobs/{number}/welds/{weld}")
@Tag(name = "technique")
public class TechniqueRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public TechniqueRoutes(Work work) {
        this.work = work;
    }

    /** The technique sheet for one shot on the weld. */
    @PostMapping("/technique")
    public SheetOut planSheet(@PathVariable String number, @PathVariable String weld, @Valid @RequestBody PlanIn body) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            WeldId identity = WeldId.parse(weld);
            TechniqueSheet sheet = PlanningService.plan(uow, job, request(identity, body));
            return sheetOut(identity, sheet);
        });
    }

    /** The same shot on each film named, in the order named. Naming no film is a 422. */
    @PostMapping("/technique/compare")
    public List<SheetOut> compare(
            @PathVariable String number,
            @PathVariable String weld,
            @Valid @RequestBody PlanIn body,
            @RequestParam String films) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            WeldId identity = WeldId.parse(weld);
            List<String> keys = films(films);
            Map<String, TechniqueSheet> sheets = PlanningService.compareFilms(uow, job, request(identity, body), keys);
            return keys.stream().map(key -> sheetOut(identity, sheets.get(key))).toList();
        });
    }

    /**
     * {@code {"film": "d7"}}: the film among those named whose clean sheet keeps the source out the
     * shortest time, or null when no sheet is clean.
     */
    @PostMapping("/technique/fastest")
    public Map<String, String> fastest(
            @PathVariable String number,
            @PathVariable String weld,
            @Valid @RequestBody PlanIn body,
            @RequestParam String films) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            WeldId identity = WeldId.parse(weld);
            Map<String, String> answer = new LinkedHashMap<>();
            answer.put(
                    "film",
                    PlanningService.fastestWorkable(uow, job, request(identity, body), films(films))
                            .orElse(null));
            return answer;
        });
    }

    /**
     * The film keys in a comma separated list, each without its surrounding white space, the empty
     * ones left out.
     *
     * @throws ValidationError when that leaves none
     */
    private static List<String> films(String films) {
        return PlanningService.filmKeys(films);
    }

    private static PlanRequest request(WeldId weld, PlanIn body) {
        return new PlanRequest(
                weld,
                body.sourceSerial(),
                body.film(),
                body.shotAt(),
                Naming.optionalShotNamed(body.shot()),
                Naming.sideNamed(body.indicatorSide()),
                body.windowMinutes().map(Duration::fromMinutes),
                body.density(),
                Naming.distanceRuleNamed(body.distanceRule()));
    }

    private static SheetOut sheetOut(WeldId weld, TechniqueSheet sheet) {
        return new SheetOut(
                weld.toString(),
                sheet.shot().value(),
                LengthOut.of(sheet.penetrated()),
                LengthOut.of(sheet.sourceToFilm()),
                LengthOut.of(sheet.geometry().geometricUnsharpness()),
                sheet.indicator().describe(),
                new CoverageOut(
                        sheet.coverage().exposures(),
                        sheet.coverage().separationDegrees(),
                        sheet.coverage().overlapDegrees()),
                DurationOut.of(sheet.exposure().exposure()),
                DurationOut.of(sheet.totalSourceOut()),
                ActivityOut.of(sheet.exposure().activity()),
                sheet.warnings(),
                sheet.isClean());
    }
}
