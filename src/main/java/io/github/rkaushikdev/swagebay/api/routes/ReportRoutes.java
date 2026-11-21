package io.github.rkaushikdev.swagebay.api.routes;

import io.github.rkaushikdev.swagebay.api.Schemas.PackageOut;
import io.github.rkaushikdev.swagebay.api.Schemas.WeldSummaryOut;
import io.github.rkaushikdev.swagebay.api.Work;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.reporting.Chain;
import io.github.rkaushikdev.swagebay.reporting.Exports;
import io.github.rkaushikdev.swagebay.reporting.Link;
import io.github.rkaushikdev.swagebay.reporting.Rate;
import io.github.rkaushikdev.swagebay.reporting.ReportPackage;
import io.github.rkaushikdev.swagebay.reporting.Traceability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports over HTTP, including the CSV a client's system pulls.
 *
 * <p>The CSV routes answer text rather than JSON and they say so in the media type. A client
 * integration that expects CSV and gets a JSON string containing CSV has to unwrap it, and every one
 * of them gets the unwrapping wrong the first time.
 *
 * <p>The package route returns the faults even when there are none, as an empty list rather than by
 * leaving the field out. A field that appears only when something is wrong is a field an
 * integration forgets to check.
 */
@RestController
@RequestMapping("/jobs/{number}/report")
@Tag(name = "reports")
public class ReportRoutes {

    /** What a CSV sheet is served as. */
    static final String CSV = "text/plain;charset=UTF-8";

    private final Work work;

    /** The routes, each in a unit of work of its own. */
    public ReportRoutes(Work work) {
        this.work = work;
    }

    /** The report package: the job, whether it can be sent and why not, and the weld summary. */
    @GetMapping
    public PackageOut readPackage(@PathVariable String number) {
        return work.apply(uow -> packageOut(ReportPackage.assemble(uow, JobNumber.parse(number))));
    }

    /** One sheet of the package as CSV text. A sheet nobody publishes is a 422 naming the ones that are. */
    @GetMapping(value = "/{sheet}.csv", produces = CSV)
    public String readCsv(@PathVariable String number, @PathVariable String sheet) {
        return work.apply(uow -> Exports.toText(ReportPackage.assemble(uow, JobNumber.parse(number)), sheet));
    }

    /** Every radiograph on the job whose chain does not hold, with each broken link as a line. */
    @GetMapping("/traceability")
    public LinkedHashMap<String, List<String>> readTraceability(@PathVariable String number) {
        return work.apply(uow -> {
            JobNumber job = JobNumber.parse(number);
            LinkedHashMap<String, List<String>> lines = new LinkedHashMap<>();
            for (Chain chain : Traceability.brokenChains(uow, job.year(), job.serial())) {
                lines.put(
                        chain.radiograph(),
                        chain.broken().stream().map(Link::describe).toList());
            }
            return lines;
        });
    }

    /** The name of every sheet the package is published as. */
    @GetMapping("/sheets")
    public List<String> listSheets() {
        return Exports.sheets();
    }

    private static PackageOut packageOut(ReportPackage assembled) {
        Rate repairs = assembled.repairRate();
        return new PackageOut(
                assembled.job().toString(),
                assembled.client(),
                assembled.site(),
                assembled.code().value(),
                assembled.procedureNumber(),
                assembled.openedOn(),
                assembled.closedOn(),
                assembled.isComplete(),
                assembled.faults(),
                assembled.weldsShot(),
                assembled.weldsRepaired(),
                repairs.value(),
                repairs.isReportable(),
                assembled.radiographs().size(),
                assembled.welds().stream()
                        .map(line -> new WeldSummaryOut(
                                line.weld(),
                                line.wall().mm(),
                                line.welder(),
                                line.radiographs(),
                                line.disposition(),
                                line.clauses()))
                        .toList());
    }
}
