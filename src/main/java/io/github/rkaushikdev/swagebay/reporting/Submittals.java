package io.github.rkaushikdev.swagebay.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Checking a submittal against its package, and sending it again.
 *
 * <p>The package and the parcel are checked together, which is the only useful check: a client
 * receiving a stack of radiographs with no procedure revision attached cannot check the work against
 * anything, and a client who finds one certificate expired assumes the rest are too.
 */
public final class Submittals {

    private Submittals() {}

    /** {@link #check(ReportPackage, Submittal, List)} against {@link Submittal#DEFAULT_REQUIRED}. */
    public static Readiness check(ReportPackage reportPackage, Submittal submittal) {
        return check(reportPackage, submittal, Submittal.DEFAULT_REQUIRED);
    }

    /** The package's faults and the parcel's missing enclosures, checked together. */
    public static Readiness check(ReportPackage reportPackage, Submittal submittal, List<Enclosure> required) {
        return new Readiness(reportPackage.faults(), submittal.missing(required));
    }

    /** A resubmittal, which is a new revision prepared that day rather than an edit of the first. */
    public static Submittal revise(Submittal submittal, Set<Enclosure> enclosures, LocalDate on) {
        return new Submittal(
                submittal.job(), submittal.client(), on, submittal.preparedBy(), enclosures, submittal.revision() + 1);
    }
}
