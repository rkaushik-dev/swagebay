package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.audit.Action;
import io.github.rkaushikdev.swagebay.audit.Entry;
import io.github.rkaushikdev.swagebay.persistence.models.AuditRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Appending to the audit trail, and the queries the trail is actually asked.
 *
 * <p>The write side has one way in and it takes an {@link Entry}, so there is no way to append a row
 * the domain would have refused. The read side is deliberately narrow: one record's history, one
 * person's day, and the entries that landed after a signature. Anything wider than that is a report,
 * and reports are built from these.
 *
 * <p>Ordering is by time and then by key. Two entries written inside one transaction can carry the
 * same moment, and without the tie break they come back in whatever order the storage feels like,
 * which makes a field history read backwards about one time in fifty. That is the kind of defect
 * that is found by a client and not by a test.
 */
public final class AuditRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public AuditRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** Append {@code entry} to the trail. */
    public void append(Entry entry) {
        AuditRow row = new AuditRow();
        row.setAt(entry.at());
        row.setActor(entry.actor());
        row.setAction(entry.action().value());
        row.setRecord(entry.record());
        row.setIdentity(entry.identity());
        row.setFieldName(entry.fieldName());
        row.setWas(entry.was());
        row.setBecame(entry.became());
        row.setReason(entry.reason());
        entityManager.persist(row);
    }

    /** Record that {@code actor} created the record. */
    public void created(LocalDateTime at, String actor, String record, String identity) {
        append(new Entry(at, actor, Action.CREATED, record, identity));
    }

    /** Record that {@code actor} signed the record. */
    public void signed(LocalDateTime at, String actor, String record, String identity) {
        append(new Entry(at, actor, Action.SIGNED, record, identity));
    }

    /** Record that {@code actor} changed one field of the record, with no reason given. */
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

    /** Record that {@code actor} changed one field of the record, and why. */
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

    /** The whole history of one record, oldest first. */
    public List<Entry> forRecord(String record, String identity) {
        return entityManager
                .createQuery(
                        "select a from AuditRow a where a.record = :record and a.identity = :identity"
                                + " order by a.at, a.id",
                        AuditRow.class)
                .setParameter("record", record)
                .setParameter("identity", identity)
                .getResultList()
                .stream()
                .map(AuditRepository::toEntry)
                .toList();
    }

    /** What one person did from {@code since} up to, and not including, {@code until}, oldest first. */
    public List<Entry> byActor(String actor, LocalDateTime since, LocalDateTime until) {
        return entityManager
                .createQuery(
                        "select a from AuditRow a where a.actor = :actor and a.at >= :since and a.at < :until"
                                + " order by a.at, a.id",
                        AuditRow.class)
                .setParameter("actor", actor)
                .setParameter("since", since)
                .setParameter("until", until)
                .getResultList()
                .stream()
                .map(AuditRepository::toEntry)
                .toList();
    }

    /** What one field of the record last became, if anybody ever changed it. */
    public Optional<String> current(String record, String identity, String fieldName) {
        return Queries.first(entityManager
                        .createQuery(
                                "select a from AuditRow a where a.record = :record and a.identity = :identity"
                                        + " and a.fieldName = :field and a.action = :action"
                                        + " order by a.at desc, a.id desc",
                                AuditRow.class)
                        .setParameter("record", record)
                        .setParameter("identity", identity)
                        .setParameter("field", fieldName)
                        .setParameter("action", Action.CHANGED.value()))
                .map(AuditRow::getBecame);
    }

    /**
     * Everything that happened to the record after its first signature, the later signatures aside.
     * A record nobody signed has nothing after its signature.
     */
    public List<Entry> changedAfterSigning(String record, String identity) {
        List<Entry> entries = forRecord(record, identity);
        Optional<LocalDateTime> signedAt = entries.stream()
                .filter(entry -> entry.action() == Action.SIGNED)
                .map(Entry::at)
                .findFirst();
        if (signedAt.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> entry.at().isAfter(signedAt.get()) && entry.action() != Action.SIGNED)
                .toList();
    }

    private static Entry toEntry(AuditRow row) {
        return new Entry(
                row.getAt(),
                row.getActor(),
                Action.fromValue(row.getAction()),
                row.getRecord(),
                row.getIdentity(),
                row.getFieldName(),
                row.getWas(),
                row.getBecame(),
                row.getReason());
    }
}
