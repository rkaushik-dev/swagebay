package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.api.Schemas.CalibrationIn;
import io.github.rkaushikdev.swagebay.api.Schemas.EntryOut;
import io.github.rkaushikdev.swagebay.api.Schemas.HistoryOut;
import io.github.rkaushikdev.swagebay.api.Schemas.IndicationIn;
import io.github.rkaushikdev.swagebay.api.Schemas.IndicatorEvidenceIn;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ReadingIn;
import io.github.rkaushikdev.swagebay.api.Schemas.RereadIn;
import io.github.rkaushikdev.swagebay.api.Schemas.RereadOut;
import io.github.rkaushikdev.swagebay.api.Schemas.ShotIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ShotOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.audit.Action;
import io.github.rkaushikdev.swagebay.audit.Entry;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Calibration;
import io.github.rkaushikdev.swagebay.imaging.Density;
import io.github.rkaushikdev.swagebay.imaging.DensityVerdict;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.HoleIndicator;
import io.github.rkaushikdev.swagebay.imaging.HoleSize;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.imaging.IndicatorEvidence;
import io.github.rkaushikdev.swagebay.imaging.NoiseMeasurement;
import io.github.rkaushikdev.swagebay.imaging.Plates;
import io.github.rkaushikdev.swagebay.imaging.Reading;
import io.github.rkaushikdev.swagebay.imaging.WireIndicator;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.LicenceRole;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.services.ReadingRecord;
import io.github.rkaushikdev.swagebay.services.ReadingService;
import io.github.rkaushikdev.swagebay.services.RereadRequest;
import io.github.rkaushikdev.swagebay.services.ShootingService;
import io.github.rkaushikdev.swagebay.services.ShotRecord;
import io.github.rkaushikdev.swagebay.services.ShotResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Radiographs over HTTP: recording a shot, recording a reading and a second reading, and a film's
 * history.
 *
 * <p>Building the evidence is the bulk of this class and it is not glamorous. It is also the whole
 * point: the caller sends what was measured, in the units they measured it in, and this assembles it
 * into the shape the rules want. A caller that had to build the evidence itself would be a caller
 * that has to know which gate runs on which medium, and then there would be two copies of that rule.
 *
 * <p>The one decision made here is which reader the badge refers to. The service does not carry a
 * personnel directory, so the reader is taken as the caller asserts them: a Level II radiographer
 * certified on the day of the reading. That is the honest shape until there is a directory, and it is
 * written down so nobody mistakes it for authentication.
 *
 * <p>The routes that name a film take the job and weld in the path as every route under a weld does;
 * the film's number identifies it on its own.
 */
@RestController
@RequestMapping("/jobs/{number}/welds/{weld}")
@Tag(name = "radiographs")
public class RadiographRoutes {

    private static final String RADIOGRAPH = "radiograph";

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public RadiographRoutes(Work work) {
        this.work = work;
    }

    /** Record one exposure, numbered and graded. The same exposure twice is a 409. */
    @PostMapping("/shots")
    @ResponseStatus(HttpStatus.CREATED)
    public ShotOut record(@PathVariable String number, @PathVariable String weld, @Valid @RequestBody ShotIn body) {
        return work.apply(uow -> {
            ShotResult result = ShootingService.recordShot(
                    uow,
                    JobNumber.parse(number),
                    new ShotRecord(
                            WeldId.parse(weld),
                            body.exposureIndex(),
                            body.exposedAt(),
                            body.techniqueReference(),
                            evidence(body)));
            return new ShotOut(
                    result.number(),
                    result.admissibility().admissible(),
                    result.admissibility().reasons(),
                    result.admissibility().gatesRun());
        });
    }

    /** Record the reading of an admissible film. A film read already is a 409. */
    @PostMapping("/radiographs/{radiograph}/reading")
    public Map<String, String> read(
            @PathVariable String number,
            @PathVariable String weld,
            @PathVariable String radiograph,
            @Valid @RequestBody ReadingIn body) {
        return work.apply(uow -> {
            ReadingService.recordReading(
                    uow,
                    new ReadingRecord(
                            radiograph,
                            reader(body.readerBadge(), body.readAt()),
                            body.readAt(),
                            body.indications().stream()
                                    .map(RadiographRoutes::indication)
                                    .toList()));
            Map<String, String> answer = new LinkedHashMap<>();
            answer.put(RADIOGRAPH, radiograph);
            answer.put("read_by", body.readerBadge());
            return answer;
        });
    }

    /**
     * A second reader's call on a film somebody has already read.
     *
     * <p>This is a post to its own path rather than a put on the reading, because it is not an edit
     * of the reading. The first reading stays exactly where it was. Putting it behind a put would tell
     * every caller the opposite.
     */
    @PostMapping("/radiographs/{radiograph}/reread")
    public RereadOut reread(
            @PathVariable String number,
            @PathVariable String weld,
            @PathVariable String radiograph,
            @Valid @RequestBody RereadIn body) {
        return work.apply(uow -> {
            ReadingService.recordReread(
                    uow,
                    new RereadRequest(
                            radiograph,
                            reader(body.readerBadge(), body.readAt()),
                            body.readAt(),
                            body.reason(),
                            body.indications().stream()
                                    .map(RadiographRoutes::indication)
                                    .toList()));
            List<Entry> history = uow.audit().forRecord(RADIOGRAPH, radiograph);
            return new RereadOut(
                    radiograph,
                    ReadingService.readersOf(uow, radiograph),
                    history.stream().anyMatch(entry -> entry.action() == Action.CHANGED));
        });
    }

    /** Everything the trail holds about one film, oldest first. A film that does not exist is a 404. */
    @GetMapping("/radiographs/{radiograph}/history")
    public HistoryOut history(@PathVariable String number, @PathVariable String weld, @PathVariable String radiograph) {
        return work.apply(uow -> {
            List<Entry> entries = uow.audit().forRecord(RADIOGRAPH, radiograph);
            if (entries.isEmpty()) {
                uow.welds().radiograph(radiograph);
            }
            return new HistoryOut(
                    RADIOGRAPH,
                    radiograph,
                    entries.stream()
                            .map(entry -> new EntryOut(
                                    entry.at(),
                                    entry.actor(),
                                    entry.action().toString(),
                                    entry.fieldName(),
                                    entry.was(),
                                    entry.became(),
                                    entry.reason()))
                            .toList(),
                    uow.audit().changedAfterSigning(RADIOGRAPH, radiograph).size());
        });
    }

    /** Film by its key, or a computed radiography plate by its key for any other medium. */
    private static ImagingSystem system(ShotIn body) {
        if (body.medium().equals("film")) {
            return ImagingSystem.fromFilm(Films.film(body.film()));
        }
        return ImagingSystem.fromPlate(Plates.plate(body.film()));
    }

    private static Evidence evidence(ShotIn body) {
        return Evidence.builder(
                        system(body),
                        body.penetrated().toLength(),
                        Naming.techniqueClassNamed(body.techniqueClass()),
                        indicator(body.indicator()))
                .density(density(body))
                .noise(noise(body))
                .build();
    }

    /**
     * The hole indicator when the shot names one, otherwise the wire indicator.
     *
     * @throws ValidationError for a shot that names neither
     */
    private static IndicatorEvidence indicator(IndicatorEvidenceIn seen) {
        if (seen.holeIdentity().isPresent()) {
            return IndicatorEvidence.hole(
                    new HoleIndicator(seen.holeIdentity().get()), HoleSize.TWO_T, seen.holeVisible());
        }
        if (seen.wireNumber().isEmpty()) {
            throw new ValidationError("a shot names a hole indicator or a wire one");
        }
        return IndicatorEvidence.wire(new WireIndicator(seen.wireNumber().get()), seen.finestWireVisible());
    }

    /** The density verdict, when there are readings, against the densitometer check when there is one. */
    private static Optional<DensityVerdict> density(ShotIn body) {
        if (body.densities().isEmpty()) {
            return Optional.empty();
        }
        List<Reading> readings = body.densities().stream()
                .map(item -> new Reading(item.label(), item.density(), item.throughIndicator()))
                .toList();
        if (body.calibration().isEmpty()) {
            return Optional.of(Density.grade(readings, body.gamma()));
        }
        CalibrationIn check = body.calibration().get();
        return Optional.of(Density.grade(
                readings,
                body.gamma(),
                new Calibration(check.checkedAt(), check.stepNominal(), check.stepRead(), check.instrument()),
                body.exposedAt()));
    }

    /** The noise measurement of a digital image, when all three of its readings are given. */
    private static Optional<NoiseMeasurement> noise(ShotIn body) {
        if (body.meanGrey().isEmpty()
                || body.noise().isEmpty()
                || body.resolution().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NoiseMeasurement(
                "parent metal",
                body.meanGrey().get(),
                body.noise().get(),
                body.resolution().get().toLength()));
    }

    private static Indication indication(IndicationIn item) {
        return new Indication(
                Naming.kindNamed(item.kind()),
                item.startsAt().toLength(),
                item.length().toLength(),
                item.width().map(LengthIn::toLength).orElse(Length.ZERO),
                item.depthFraction(),
                item.clock().map(ClockPosition::parse),
                "");
    }

    /** The reader as the caller asserts them. Not authentication, and it says so. */
    private static Person reader(String badge, LocalDateTime readAt) {
        LocalDate day = readAt.toLocalDate();
        return new Person(badge, badge, Level.LEVEL_II, LicenceRole.RADIOGRAPHER, day, day, day);
    }
}
