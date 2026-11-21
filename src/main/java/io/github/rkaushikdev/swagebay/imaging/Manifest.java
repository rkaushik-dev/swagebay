package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every file the store is supposed to hold for one job, with what each hashed to at ingest.
 *
 * <p>Entries are keyed by file name and kept in the order they were first added. Adding the same
 * file twice is harmless; adding a different file under a name already held is refused.
 */
public final class Manifest {

    private final String job;
    private final Map<String, StoredImage> entries = new LinkedHashMap<>();

    /** An empty manifest for a job. */
    public Manifest(String job) {
        this.job = job;
    }

    /** The job the manifest is for. */
    public String job() {
        return job;
    }

    /** The entries by file name, in the order they were first added; a read-only view. */
    public Map<String, StoredImage> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Add a file, or replace the entry for the same name and the same bytes.
     *
     * @throws ValidationError when the name is already held against a different digest
     */
    public void add(StoredImage image) {
        StoredImage held = entries.get(image.filename());
        if (held != null && !held.digest().equals(image.digest())) {
            throw new ValidationError(
                    "that name is already in the manifest against different bytes",
                    Context.of("filename", image.filename()));
        }
        entries.put(image.filename(), image);
    }

    /** How many files the manifest holds. */
    public int size() {
        return entries.size();
    }

    /** Whether the manifest holds a file of that name. */
    public boolean contains(String filename) {
        return entries.containsKey(filename);
    }

    /** Every file for one exposure, in either case and trimmed, first revision first. */
    public List<StoredImage> forRadiograph(String radiographNumber) {
        String wanted = Text.strip(radiographNumber).toUpperCase(Locale.ROOT);
        return entries.values().stream()
                .filter(image ->
                        image.radiographNumber().toUpperCase(Locale.ROOT).equals(wanted))
                .sorted(Comparator.comparingInt(StoredImage::revision))
                .toList();
    }

    /** The record copy of an exposure, the first file for it that is not a viewing copy. */
    public Optional<StoredImage> recordCopy(String radiographNumber) {
        return forRadiograph(radiographNumber).stream()
                .filter(image -> image.purpose() == Purpose.RECORD)
                .findFirst();
    }

    /**
     * Compare the manifest against what is actually on the disk.
     *
     * <p>The argument maps file name to digest rather than naming a directory, because the thing
     * being checked is a set of bytes and walking the disk belongs to the caller.
     *
     * @param seen every file found, by name, with the digest of its bytes now
     */
    public Discrepancy verify(Map<String, String> seen) {
        List<String> missing = entries.keySet().stream()
                .filter(name -> !seen.containsKey(name))
                .sorted()
                .toList();
        List<String> unexpected = seen.keySet().stream()
                .filter(name -> !entries.containsKey(name))
                .sorted()
                .toList();
        List<String> altered = entries.entrySet().stream()
                .filter(entry -> seen.containsKey(entry.getKey())
                        && !seen.get(entry.getKey()).equals(entry.getValue().digest()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        return new Discrepancy(missing, unexpected, altered);
    }

    /** Every file level fault across the manifest, by file name in order, each prefixed with the name. */
    public List<String> faults() {
        List<String> found = new ArrayList<>();
        for (String name : entries.keySet().stream().sorted().toList()) {
            for (String reason : ImageStore.faults(entries.get(name))) {
                found.add(name + ": " + reason);
            }
        }
        return List.copyOf(found);
    }

    /** The exposures, in capitals and sorted, that have files but no record copy among them. */
    public List<String> radiographsWithoutARecordCopy() {
        Set<String> numbers = entries.values().stream()
                .map(image -> image.radiographNumber().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return numbers.stream()
                .filter(number -> recordCopy(number).isEmpty())
                .sorted()
                .toList();
    }

    /** The bytes the store holds for the job. */
    public long totalBytes() {
        return entries.values().stream().mapToLong(StoredImage::byteSize).sum();
    }
}
