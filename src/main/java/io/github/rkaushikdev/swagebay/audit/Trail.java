package io.github.rkaushikdev.swagebay.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The audit trail, which answers who changed what and when.
 *
 * <p>Everything else records what happened in the world. This records what happened to the record,
 * which is the thing a dispute turns on. A weld disposition that says accept is worth very little on
 * its own; one that says accept, entered by this badge at this time, having previously said repair
 * and been changed by that badge, is worth the argument. Entries are appended and never edited, so
 * the trail cannot be tidied.
 */
public final class Trail {

    private final List<Entry> entries = new ArrayList<>();

    /** An empty trail. */
    public Trail() {}

    /** A trail holding those entries, in that order. */
    public Trail(List<Entry> entries) {
        this.entries.addAll(entries);
    }

    /** Every entry, in the order appended. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Add an entry to the end. */
    public void append(Entry entry) {
        entries.add(entry);
    }

    /** Record that a record was created. */
    public void created(LocalDateTime at, String actor, String record, String identity) {
        append(new Entry(at, actor, Action.CREATED, record, identity));
    }

    /** Record that a record was signed. */
    public void signed(LocalDateTime at, String actor, String record, String identity) {
        append(new Entry(at, actor, Action.SIGNED, record, identity));
    }

    /** Record a change to one field, with no reason given. */
    public void changed(
            LocalDateTime at,
            String actor,
            String record,
            String identity,
            String fieldName,
            String was,
            String became) {
        changed(at, actor, record, identity, fieldName, was, became, "");
    }

    /** Record a change to one field, and why. */
    public void changed(
            LocalDateTime at,
            String actor,
            String record,
            String identity,
            String fieldName,
            String was,
            String became,
            String reason) {
        append(new Entry(at, actor, Action.CHANGED, record, identity, fieldName, was, became, reason));
    }

    /** One record's history, oldest first, which is how it reads; entries at one moment keep their order. */
    public List<Entry> forRecord(String record, String identity) {
        return entries.stream()
                .filter(entry ->
                        entry.record().equals(record) && entry.identity().equals(identity))
                .sorted(Comparator.comparing(Entry::at))
                .toList();
    }

    /** Everything one badge did, in the order appended. */
    public List<Entry> byActor(String actor) {
        return entries.stream().filter(entry -> entry.actor().equals(actor)).toList();
    }

    /**
     * What one field has been, in order, without replaying the whole trail: the value before the
     * first change, then each value it became. Empty for a field nobody changed.
     */
    public List<String> fieldHistory(String record, String identity, String fieldName) {
        List<Entry> changes = forRecord(record, identity).stream()
                .filter(entry ->
                        entry.action() == Action.CHANGED && entry.fieldName().equals(fieldName))
                .toList();
        if (changes.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        values.add(changes.get(0).was());
        changes.forEach(entry -> values.add(entry.became()));
        return List.copyOf(values);
    }

    /** What one field holds now, if anybody ever changed it. */
    public Optional<String> current(String record, String identity, String fieldName) {
        List<String> history = fieldHistory(record, identity, fieldName);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.get(history.size() - 1));
    }

    /**
     * The entries made after the first signature on a record, signatures aside, which is what a
     * dispute is about. Empty when nothing was signed.
     */
    public List<Entry> changedAfterSigning(String record, String identity) {
        List<Entry> history = forRecord(record, identity);
        Optional<LocalDateTime> signedAt = history.stream()
                .filter(entry -> entry.action() == Action.SIGNED)
                .map(Entry::at)
                .findFirst();
        if (signedAt.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .filter(entry -> entry.at().isAfter(signedAt.get()) && entry.action() != Action.SIGNED)
                .toList();
    }
}
