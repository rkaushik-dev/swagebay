package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.identity.JobNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything between a job and a signature, kept apart by kind.
 *
 * @param job the job asked about
 * @param workReasons what is outstanding on the welds and films
 * @param reportReasons the non conformance reports holding the job open
 * @param imageReasons what is wrong with the job's files in the image store
 */
public record Checklist(
        JobNumber job, List<String> workReasons, List<String> reportReasons, List<String> imageReasons) {

    /** A checklist; the lists are copied. */
    public Checklist {
        Objects.requireNonNull(job, "job");
        workReasons = List.copyOf(workReasons);
        reportReasons = List.copyOf(reportReasons);
        imageReasons = List.copyOf(imageReasons);
    }

    /** Every reason, the work first, then the reports, then the images. */
    public List<String> reasons() {
        List<String> all = new ArrayList<>(workReasons);
        all.addAll(reportReasons);
        all.addAll(imageReasons);
        return List.copyOf(all);
    }

    /** Whether nothing stands between the job and being closed. */
    public boolean isClear() {
        return reasons().isEmpty();
    }

    /** {@code 24-0001 is ready to close}, or every reason joined with semicolons. */
    public String describe() {
        if (isClear()) {
            return job + " is ready to close";
        }
        return String.join("; ", reasons());
    }
}
