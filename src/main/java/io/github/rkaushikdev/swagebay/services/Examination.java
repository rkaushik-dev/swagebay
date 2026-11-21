package io.github.rkaushikdev.swagebay.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the database checks found.
 *
 * @param findings every finding, check by check in the order the checks ran
 * @param checksRun the names of the checks that ran, in order
 */
public record Examination(List<Finding> findings, List<String> checksRun) {

    /** An examination; the lists are copied. */
    public Examination {
        findings = List.copyOf(findings);
        checksRun = List.copyOf(checksRun);
    }

    /** Whether no check found anything. */
    public boolean isHealthy() {
        return findings.isEmpty();
    }

    /** The findings grouped by the check that found them, the checks in the order they first found something. */
    public Map<String, List<Finding>> byCheck() {
        Map<String, List<Finding>> grouped = new LinkedHashMap<>();
        for (Finding finding : findings) {
            grouped.computeIfAbsent(finding.check(), name -> new ArrayList<>()).add(finding);
        }
        Map<String, List<Finding>> frozen = new LinkedHashMap<>();
        grouped.forEach((name, found) -> frozen.put(name, List.copyOf(found)));
        return Collections.unmodifiableMap(frozen);
    }

    /** {@code 6 checks, nothing found}, or {@code 6 checks, 2 findings}. */
    public String describe() {
        if (isHealthy()) {
            return checksRun.size() + " checks, nothing found";
        }
        return checksRun.size() + " checks, " + findings.size() + " findings";
    }
}
