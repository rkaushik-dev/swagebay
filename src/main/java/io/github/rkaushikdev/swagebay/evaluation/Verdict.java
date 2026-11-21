package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What one acceptance code says about one weld, with the code on the record.
 *
 * <p>Every finding keeps its clause, so a report reads the same whichever code was used and an
 * auditor can always tell which one was.
 *
 * @param code the code the weld was judged under
 * @param disposition what should happen to the weld
 * @param findings every rule that was broken
 * @param wall the wall the weld was judged at
 */
public record Verdict(Code code, Disposition disposition, List<Finding> findings, Length wall) {

    /** A verdict; the findings are copied. */
    public Verdict {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(wall, "wall");
        findings = List.copyOf(findings);
    }

    /** Whether the weld is accepted. */
    public boolean accepted() {
        return disposition == Disposition.ACCEPT;
    }

    /** Every clause a finding quotes, once each, sorted. */
    public List<String> clauses() {
        return List.copyOf(findings.stream().map(Finding::clause).collect(Collectors.toCollection(TreeSet::new)));
    }

    /** {@code accept to API 1104}, or the code followed by every finding joined with semicolons. */
    public String describe() {
        if (accepted()) {
            return "accept to " + code;
        }
        return code + ": " + findings.stream().map(Finding::describe).collect(Collectors.joining("; "));
    }
}
