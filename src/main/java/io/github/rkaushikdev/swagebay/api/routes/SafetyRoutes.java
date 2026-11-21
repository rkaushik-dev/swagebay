package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.api.Schemas.BoundaryIn;
import io.github.rkaushikdev.swagebay.api.Schemas.BoundaryOut;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthOut;
import io.github.rkaushikdev.swagebay.api.Schemas.RopeOut;
import io.github.rkaushikdev.swagebay.api.Schemas.SurveyIn;
import io.github.rkaushikdev.swagebay.api.Schemas.SurveyOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.safety.Barrier;
import io.github.rkaushikdev.swagebay.safety.BoundaryKind;
import io.github.rkaushikdev.swagebay.safety.RadiationField;
import io.github.rkaushikdev.swagebay.safety.SurveyStage;
import io.github.rkaushikdev.swagebay.services.Point;
import io.github.rkaushikdev.swagebay.services.SurveyOutcome;
import io.github.rkaushikdev.swagebay.services.SurveyService;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safety over HTTP: where the rope goes, and what the meter said.
 *
 * <p>The boundary route takes the source and what is round it in the request rather than reading it
 * off the inventory, because the crew asking is standing next to a projector with a collimator they
 * may or may not have fitted, and the inventory does not know that. The route answers the question
 * as asked, and touches no record.
 *
 * <p>The survey route stores what the meter read and hands back the faults. It does not refuse a
 * bad survey, for the reason the survey service gives: a survey the software will not accept is a
 * survey that ends up on paper.
 */
@RestController
@RequestMapping("/safety")
@Tag(name = "safety")
public class SafetyRoutes {

    /** Straight down the beam. */
    private static final BigDecimal DOWN = BigDecimal.ZERO;

    /** Behind the source, where a collimator shields most. */
    private static final BigDecimal SIDE = BigDecimal.valueOf(180);

    private final Work work;

    /** The routes; the survey route runs in a unit of work of its own. */
    public SafetyRoutes(Work work) {
        this.work = work;
    }

    /** Every posting's boundary, down the beam and behind the source. */
    @PostMapping("/boundaries")
    public RopeOut boundaries(@Valid @RequestBody BoundaryIn body) {
        RadiationField field = field(body);
        List<BoundaryOut> out = Arrays.stream(BoundaryKind.values())
                .map(kind -> new BoundaryOut(
                        kind.value(),
                        kind.rate().mrPerHour(),
                        LengthOut.of(field.boundary(kind, DOWN)),
                        LengthOut.of(field.boundary(kind, SIDE))))
                .toList();
        return new RopeOut(
                out,
                out.stream()
                        .anyMatch(boundary ->
                                boundary.downBeam().um() != boundary.offBeam().um()));
    }

    /** Store a survey taken before or after an exposure, with what is wrong with it. */
    @PostMapping("/radiographs/{radiograph}/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    public SurveyOut survey(@PathVariable String radiograph, @Valid @RequestBody SurveyIn body) {
        return work.apply(uow -> {
            SurveyOutcome outcome = SurveyService.recordSurvey(
                    uow,
                    radiograph,
                    body.stage().equals("before") ? SurveyStage.BEFORE : SurveyStage.AFTER,
                    body.performedAt(),
                    body.performedBy(),
                    body.instrument(),
                    body.calibrationDue(),
                    body.points().stream()
                            .map(point -> new Point(
                                    point.label(),
                                    point.distance().toLength(),
                                    point.bearingDegrees(),
                                    DoseRate.fromMrPerHour(point.mrPerHour()),
                                    point.atDeviceSurface()))
                            .toList());
            return new SurveyOut(
                    radiograph,
                    body.stage(),
                    outcome.isClear(),
                    outcome.faults(),
                    outcome.hottestLabel(),
                    outcome.hottestReading().mrPerHour());
        });
    }

    /**
     * The field round the source as described: its isotope and activity, the collimator when one is
     * fitted, and the barrier when one is named.
     *
     * @throws ValidationError for a barrier named without its thickness
     */
    private static RadiationField field(BoundaryIn body) {
        List<Barrier> barriers = List.of();
        if (body.barrierMaterial().isPresent()) {
            if (body.barrierThickness().isEmpty()) {
                throw new ValidationError("a barrier needs a thickness");
            }
            barriers = List.of(new Barrier(
                    body.barrierMaterial().get(), body.barrierThickness().get().toLength()));
        }
        return new RadiationField(
                Isotopes.isotope(body.isotope()),
                Activity.fromCurie(body.curies()),
                body.collimator().map(Devices::collimator),
                barriers);
    }
}
