package io.github.rkaushikdev.swagebay.safety;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every incident, and what is owed to the regulator right now. */
public final class IncidentRegister {

    private final Map<String, Incident> incidents = new LinkedHashMap<>();

    /** An empty register. */
    public IncidentRegister() {}

    /** Every incident by reference, in the order recorded. */
    public Map<String, Incident> incidents() {
        return Collections.unmodifiableMap(incidents);
    }

    /**
     * Put an incident on the register.
     *
     * @throws ConflictError for a reference already used
     */
    public void record(Incident incident) {
        if (incidents.containsKey(incident.reference())) {
            throw new ConflictError("that reference is already used", Context.of("reference", incident.reference()));
        }
        incidents.put(incident.reference(), incident);
    }

    /**
     * The incident with that reference.
     *
     * @throws ConflictError for a reference that is not on the register
     */
    public Incident get(String reference) {
        Incident incident = incidents.get(reference);
        if (incident == null) {
            throw new ConflictError("no incident with that reference", Context.of("reference", reference));
        }
        return incident;
    }

    /** Everything not yet reported, soonest deadline first, a shared deadline by reference. */
    public List<Incident> outstandingAt(LocalDateTime moment) {
        return incidents.values().stream()
                .filter(incident -> !incident.isNotified())
                .sorted(Comparator.comparing(Incident::dueAt).thenComparing(Incident::reference))
                .toList();
    }

    /** The references of everything late at that moment, sorted. */
    public List<String> lateAt(LocalDateTime moment) {
        return incidents.values().stream()
                .filter(incident -> incident.isLateAt(moment))
                .map(Incident::reference)
                .sorted()
                .toList();
    }

    /** The references, sorted, of the immediate calls not yet made, which somebody should be on the phone about. */
    public List<String> immediateAt(LocalDateTime moment) {
        return incidents.values().stream()
                .filter(incident -> incident.occurrence().isImmediate() && !incident.isNotified())
                .map(Incident::reference)
                .sorted()
                .toList();
    }
}
