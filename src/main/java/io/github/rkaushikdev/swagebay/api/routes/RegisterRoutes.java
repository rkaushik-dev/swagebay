package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.api.Schemas.CalibrationOut;
import io.github.rkaushikdev.swagebay.api.Schemas.IncidentIn;
import io.github.rkaushikdev.swagebay.api.Schemas.IncidentOut;
import io.github.rkaushikdev.swagebay.api.Schemas.InstrumentCalibrationIn;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.calibration.Certificate;
import io.github.rkaushikdev.swagebay.calibration.Instrument;
import io.github.rkaushikdev.swagebay.persistence.models.CalibrationRow;
import io.github.rkaushikdev.swagebay.persistence.models.IncidentRow;
import io.github.rkaushikdev.swagebay.safety.Incident;
import io.github.rkaushikdev.swagebay.safety.Occurrence;
import io.github.rkaushikdev.swagebay.services.RegisterService;
import io.github.rkaushikdev.swagebay.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Incidents and calibrations over HTTP.
 *
 * <p>The incident route returns the deadline it computed rather than taking one. A client that could
 * supply the deadline could supply the wrong one, and the whole reason the occurrence class exists is
 * that the deadline falls out of what happened rather than out of anybody's judgement.
 *
 * <p>An occurrence and an instrument are named by their member name in any case, {@code
 * SOURCE_DISCONNECTED}, or by their words exactly, {@code dose over a limit}.
 */
@RestController
@RequestMapping("/registers")
@Tag(name = "registers")
public class RegisterRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public RegisterRoutes(Work work) {
        this.work = work;
    }

    /** Put an incident on the register, with the deadline its occurrence sets. A reference used twice is a 409. */
    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentOut createIncident(@Valid @RequestBody IncidentIn body) {
        return work.apply(uow -> {
            Incident incident = new Incident(
                    body.reference(),
                    occurrence(body.occurrence()),
                    body.discoveredAt(),
                    body.site(),
                    body.reportedBy(),
                    body.account());
            RegisterService.recordIncident(uow, incident);
            return incidentOut(uow.incidents().get(body.reference()), LocalDateTime.now());
        });
    }

    /** Record when an incident was reported to the regulator. */
    @PostMapping("/incidents/{reference}/notify")
    public IncidentOut reportIncident(@PathVariable String reference, @RequestParam LocalDateTime at) {
        return work.apply(uow -> {
            RegisterService.notifyIncident(uow, reference, at);
            return incidentOut(uow.incidents().get(reference), at);
        });
    }

    /** Every incident not yet reported, late or not as of now. */
    @GetMapping("/incidents")
    public List<IncidentOut> listIncidents() {
        LocalDateTime now = LocalDateTime.now();
        return work.apply(uow -> uow.incidents().unreported().stream()
                .map(row -> incidentOut(row, now))
                .toList());
    }

    /** One incident. A reference nobody used is a 404. */
    @GetMapping("/incidents/{reference}")
    public IncidentOut readIncident(@PathVariable String reference) {
        return work.apply(uow -> incidentOut(uow.incidents().get(reference), LocalDateTime.now()));
    }

    /**
     * Put a calibration certificate on the register against an instrument. A certificate with no
     * reference of its own is filed under the instrument's serial.
     */
    @PostMapping("/calibrations")
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationOut createCalibration(@Valid @RequestBody InstrumentCalibrationIn body) {
        return work.apply(uow -> {
            RegisterService.recordCalibration(
                    uow,
                    body.serial(),
                    instrument(body.instrument()),
                    new Certificate(
                            body.calibratedOn(),
                            body.dueOn(),
                            body.laboratory(),
                            body.certificate().isEmpty() ? body.serial() : body.certificate()));
            return calibrationOut(uow.calibrations().latestFor(body.serial()).orElseThrow());
        });
    }

    /** The serials whose calibration has run out on a day, today unless another is asked about. */
    @GetMapping("/calibrations/expired")
    public List<String> listExpired(@RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> uow.calibrations().expiredOn(day));
    }

    /** An instrument's latest calibration, or null for one never calibrated. */
    @GetMapping("/calibrations/{serial}")
    public Optional<CalibrationOut> readCalibration(@PathVariable String serial) {
        return work.apply(uow -> uow.calibrations().latestFor(serial).map(RegisterRoutes::calibrationOut));
    }

    /**
     * The occurrence by its name or its words.
     *
     * @throws ValidationError naming every occurrence there is
     */
    private static Occurrence occurrence(String value) {
        return member(Occurrence.values(), Occurrence::value, value, "occurrence");
    }

    /**
     * The instrument by its name or its words.
     *
     * @throws ValidationError naming every instrument there is
     */
    private static Instrument instrument(String value) {
        return member(Instrument.values(), Instrument::value, value, "instrument");
    }

    private static <E extends Enum<E>> E member(E[] members, Function<E, String> words, String value, String what) {
        String wanted = Text.strip(value);
        String lowered = wanted.toLowerCase(Locale.ROOT);
        for (E member : members) {
            if (member.name().toLowerCase(Locale.ROOT).equals(lowered)
                    || words.apply(member).equals(wanted)) {
                return member;
            }
        }
        String known = Arrays.stream(members).map(Enum::name).collect(Collectors.joining(", "));
        throw new ValidationError("no " + what + " by that name", Context.of("given", value, "known", known));
    }

    /** An incident as of {@code at}: late when reported after its deadline, or still unreported past it. */
    private static IncidentOut incidentOut(IncidentRow row, LocalDateTime at) {
        Occurrence occurrence = occurrence(row.getOccurrence());
        LocalDateTime due = row.getDiscoveredAt().plus(occurrence.deadline());
        boolean late =
                row.getNotifiedAt().map(notified -> notified.isAfter(due)).orElse(at.isAfter(due));
        return new IncidentOut(
                row.getReference(),
                row.getOccurrence(),
                row.getDiscoveredAt(),
                due,
                row.getSite(),
                row.getNotifiedAt(),
                late);
    }

    private static CalibrationOut calibrationOut(CalibrationRow row) {
        return new CalibrationOut(
                row.getSerial(), row.getInstrumentKind(), row.getCalibratedOn(), row.getDueOn(), row.getLaboratory());
    }
}
