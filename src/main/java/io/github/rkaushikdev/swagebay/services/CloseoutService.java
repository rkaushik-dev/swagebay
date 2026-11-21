package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.Discrepancy;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What has to be true before a job is signed off.
 *
 * <p>A job can have every film read and still be nowhere near finished: an open non conformance, a
 * rejected spool with no removal reference, an image store that does not match its manifest. So the
 * check is here, in one place, with the categories kept apart. The order is what somebody can act
 * on soonest, because the list is read top down by a coordinator with a phone in their hand.
 *
 * <p>Nothing here writes. It is asked, and {@link JobService#closeJob} refuses on the answer, which
 * is what lets the same list be printed as a checklist before anybody tries to close anything.
 */
public final class CloseoutService {

    private CloseoutService() {}

    /**
     * Ask every question at once, without looking at a disk.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static Checklist checklist(UnitOfWork work, JobNumber number) {
        return checklist(work, number, Optional.empty());
    }

    /**
     * Ask every question at once, the way the gate for a truck does, and compare the store against
     * the files found on the disk.
     *
     * <p>The expected file digests are passed in rather than read off a disk here, for the same
     * reason {@link Manifest#verify} takes them: walking a directory is the caller's business and
     * this has to be answerable without one.
     *
     * @param expectedFiles every file found on the disk, by name, with the digest of its bytes
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static Checklist checklist(UnitOfWork work, JobNumber number, Map<String, String> expectedFiles) {
        return checklist(work, number, Optional.of(expectedFiles));
    }

    private static Checklist checklist(UnitOfWork work, JobNumber number, Optional<Map<String, String>> expected) {
        return new Checklist(
                number,
                JobService.outstanding(work, number),
                reportReasons(work, number),
                imageReasons(work, number, expected));
    }

    private static List<String> reportReasons(UnitOfWork work, JobNumber number) {
        String job = number.toString();
        return work.nonconformances().registerFor(job).blocksClosing(job);
    }

    private static List<String> imageReasons(
            UnitOfWork work, JobNumber number, Optional<Map<String, String>> expectedFiles) {
        Manifest manifest = work.store().manifestFor(number);
        if (manifest.entries().isEmpty()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>(manifest.faults());
        for (String radiograph : manifest.radiographsWithoutARecordCopy()) {
            reasons.add(radiograph + " has no record copy in the store");
        }
        if (expectedFiles.isPresent()) {
            Discrepancy found = manifest.verify(expectedFiles.get());
            for (String name : found.missing()) {
                reasons.add(name + " is missing from the store");
            }
            for (String name : found.altered()) {
                reasons.add(name + " has changed since ingest");
            }
            for (String name : found.unexpected()) {
                reasons.add(name + " is on the disk and not in the manifest");
            }
        }
        return List.copyOf(reasons);
    }
}
