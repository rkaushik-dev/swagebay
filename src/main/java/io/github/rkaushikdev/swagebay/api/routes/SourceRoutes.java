package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Schemas.ActivityOut;
import io.github.rkaushikdev.swagebay.api.Schemas.BookingIn;
import io.github.rkaushikdev.swagebay.api.Schemas.FitnessOut;
import io.github.rkaushikdev.swagebay.api.Schemas.LengthOut;
import io.github.rkaushikdev.swagebay.api.Schemas.NewSourceIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ReturnIn;
import io.github.rkaushikdev.swagebay.api.Schemas.SourceOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.services.Booking;
import io.github.rkaushikdev.swagebay.services.Fitness;
import io.github.rkaushikdev.swagebay.services.SourceService;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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
 * Sources over HTTP.
 *
 * <p>The fitness route takes the moment it is being asked about rather than assuming now. A
 * coordinator planning tomorrow night's work needs to know whether the source is fit tomorrow night,
 * and a source whose leak test expires at midnight is fit when asked at four in the afternoon and not
 * fit for the shot. Defaulting to now when a moment is given would give the wrong answer confidently.
 */
@RestController
@RequestMapping("/sources")
@Tag(name = "sources")
public class SourceRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public SourceRoutes(Work work) {
        this.work = work;
    }

    /** Put a source on the inventory as its certificate reads. The same serial twice is a 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceOut createSource(@Valid @RequestBody NewSourceIn body) {
        return work.apply(uow -> {
            SourceService.registerSource(
                    uow,
                    new SealedSource(
                            new SourceCertificate(
                                    body.serial(),
                                    Isotopes.isotope(body.isotope()),
                                    Activity.fromCurie(body.assayCuries()),
                                    body.assayAt(),
                                    body.manufacturer(),
                                    body.model(),
                                    body.activeDiameter().toLength(),
                                    body.activeLength().toLength()),
                            Activity.fromCurie(body.workingFloorCuries())));
            return sourceOut(uow, uow.sources().get(body.serial()));
        });
    }

    /** Every source in service, by serial. */
    @GetMapping
    public List<SourceOut> listSources() {
        return work.apply(uow -> uow.sources().inService().stream()
                .map(row -> sourceOut(uow, row))
                .toList());
    }

    /** One source. A serial not on the inventory is a 404. */
    @GetMapping("/{serial}")
    public SourceOut readSource(@PathVariable String serial) {
        return work.apply(uow -> sourceOut(uow, uow.sources().get(serial)));
    }

    /** Whether the source can go out at a moment, now unless another is asked about, and why not. */
    @GetMapping("/{serial}/fitness")
    public FitnessOut readFitness(@PathVariable String serial, @RequestParam Optional<LocalDateTime> at) {
        return work.apply(uow -> {
            Fitness report = SourceService.fitness(uow, serial, at.orElseGet(LocalDateTime::now));
            return new FitnessOut(report.serial(), report.on(), report.fit(), report.reasons());
        });
    }

    /** Book the source out to a site in a device. A source already out is a 409. */
    @PostMapping("/{serial}/send-out")
    public SourceOut bookOut(@PathVariable String serial, @Valid @RequestBody BookingIn body) {
        return work.apply(uow -> {
            SourceService.sendOut(
                    uow, new Booking(serial, body.deviceSerial(), body.crewBadge(), body.site(), body.wentOut()));
            return sourceOut(uow, uow.sources().get(serial));
        });
    }

    /** Bring the source back to the shop with the exposures it made. */
    @PostMapping("/{serial}/bring-back")
    public SourceOut bookIn(@PathVariable String serial, @Valid @RequestBody ReturnIn body) {
        return work.apply(uow -> {
            SourceService.bringBack(uow, serial, body.cameBack(), body.exposures());
            return sourceOut(uow, uow.sources().get(serial));
        });
    }

    /** Take the source out of service. A source out on a job is a 409. */
    @PostMapping("/{serial}/retire")
    public SourceOut standDown(@PathVariable String serial) {
        return work.apply(uow -> {
            SourceService.retire(uow, serial);
            return sourceOut(uow, uow.sources().get(serial));
        });
    }

    private static SourceOut sourceOut(UnitOfWork uow, SourceRow row) {
        SealedSource source = SourceMapper.rowToSource(row);
        return new SourceOut(
                row.getSerial(),
                row.getIsotopeKey(),
                ActivityOut.of(row.getAssayActivity()),
                row.getAssayAt(),
                row.getState(),
                SourceService.location(uow, row.getSerial()),
                LengthOut.of(source.certificate().focalSize()));
    }
}
