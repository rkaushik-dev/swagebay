package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.services.DoctorService;
import io.github.rkaushikdev.swagebay.services.Examination;
import io.github.rkaushikdev.swagebay.services.Finding;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The doctor command, which reads and never writes.
 *
 * <p>It opens the database for reading only, so it cannot change what it is examining, and it leaves
 * the database's file exactly as it found it.
 *
 * <p>It exits non zero when it finds anything, so it can run nightly and wake somebody up. The
 * findings are grouped by check rather than listed flat, because one bad import produces forty
 * findings from one check and a flat list of forty makes that look like forty problems.
 */
@Command(name = "doctor", description = "Look for records that cannot be true at the same time.")
final class DoctorCommand extends Leaf {

    /** The checks the doctor knows, in the order they run. */
    static final class Checks extends Choice {

        @Override
        List<String> choices() {
            return DoctorService.checkNames();
        }
    }

    @Option(
            names = "--check",
            paramLabel = "CHOICE",
            converter = Checks.class,
            completionCandidates = Checks.class,
            description = {"run just these, rather than all of them", "One of: ${COMPLETION-CANDIDATES}."})
    private List<String> only = List.of();

    @Override
    public Integer call() {
        Examination found = fromReading(work -> DoctorService.examine(work, only));
        Map<String, List<Finding>> grouped = found.byCheck();
        List<Map.Entry<String, List<String>>> groups = found.checksRun().stream()
                .map(name -> Map.entry(
                        name,
                        grouped.getOrDefault(name, List.of()).stream()
                                .map(finding -> finding.subject() + " " + finding.account())
                                .toList()))
                .toList();
        return Gates.report(out(), found.describe(), groups, found.describe());
    }
}
