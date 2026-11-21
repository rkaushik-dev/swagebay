package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Schemas.CloseIn;
import io.github.rkaushikdev.swagebay.api.Schemas.DecisionIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ReportIn;
import io.github.rkaushikdev.swagebay.api.Schemas.ReportOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
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
 * Non conformance over HTTP.
 *
 * <p>Raising is a post to the collection. Deciding and closing are posts to their own paths rather
 * than a patch on the report, because both are events with their own preconditions, and a patch that
 * accepts any subset of the fields would let a caller set the close date without ever deciding
 * anything.
 *
 * <p>How long a report has been open, and whether it is overdue, are answered for a day: the one
 * asked about with {@code ?on=}, the day it was raised or closed where the route is that event, and
 * today otherwise.
 */
@RestController
@RequestMapping("/nonconformances")
@Tag(name = "nonconformance")
public class NonconformanceRoutes {

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public NonconformanceRoutes(Work work) {
        this.work = work;
    }

    /** A report as it reads back on {@code on}. */
    static ReportOut out(Report report, LocalDate on) {
        return new ReportOut(
                report.reference(),
                report.job(),
                report.item(),
                report.requirement(),
                report.disposition().map(Disposition::toString),
                report.concession(),
                report.removal(),
                report.raisedOn(),
                report.raisedBy(),
                report.closedOn(),
                Optional.of(report.closedBy()).filter(badge -> !badge.isEmpty()),
                report.daysOpen(on),
                report.isOverdue(on));
    }

    /** Put a report on the register. A reference already on it is a 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportOut raiseReport(@Valid @RequestBody ReportIn body) {
        return work.apply(uow -> {
            Report report = new Report(
                    body.reference(),
                    body.job(),
                    body.item(),
                    body.requirement(),
                    body.account(),
                    body.raisedOn(),
                    body.raisedBy());
            uow.nonconformances().raiseReport(report);
            return out(report, body.raisedOn());
        });
    }

    /** Every report not yet closed, on any job, in the order raised. */
    @GetMapping
    public List<ReportOut> listOpen(@RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> uow.nonconformances().openReports().stream()
                .map(report -> out(report, day))
                .toList());
    }

    /** One report. A reference nobody raised is a 404. */
    @GetMapping("/{reference}")
    public ReportOut readReport(@PathVariable String reference, @RequestParam Optional<LocalDate> on) {
        return work.apply(uow -> out(uow.nonconformances().report(reference), on.orElseGet(LocalDate::now)));
    }

    /**
     * Decide a report. A disposition nobody publishes is a 422; one that arrives without its
     * paperwork is a 409.
     */
    @PostMapping("/{reference}/decision")
    public ReportOut decide(@PathVariable String reference, @Valid @RequestBody DecisionIn body) {
        return work.apply(uow -> {
            Report report = uow.nonconformances()
                    .decide(reference, Naming.dispositionNamed(body.disposition()), body.concession(), body.removal());
            return out(report, LocalDate.now());
        });
    }

    /** Close a report. Closing one undecided, or by the person who raised it, is a 409. */
    @PostMapping("/{reference}/closure")
    public ReportOut close(@PathVariable String reference, @Valid @RequestBody CloseIn body) {
        return work.apply(uow -> {
            Report report = uow.nonconformances().close(reference, body.closedOn(), body.closedBy());
            return out(report, body.closedOn());
        });
    }
}
