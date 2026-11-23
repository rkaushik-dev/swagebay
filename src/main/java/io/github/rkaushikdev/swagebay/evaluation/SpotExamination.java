package io.github.rkaushikdev.swagebay.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * UW-52(d): what one failed spot sets off on the weld increment it stands for.
 *
 * <p>A spot is a sample, and a sample that fails says something about the weld nobody shot. So a
 * failed spot does not just get repaired. Two more spots go into the same increment, away from the
 * first. If both of them pass, the increment is accepted once the defect the first one found has
 * been cut out, rewelded and shot again. If either of them fails, the increment is rejected: it is
 * cut out and rewelded, or the whole of it is radiographed and every defect repaired.
 *
 * <p>The spots are taken in the order they were shot. The first is the spot, the next two are the
 * tracers, and anything after them is the repair of the first.
 */
public final class SpotExamination {

    /** How many tracer spots one failed spot calls for. */
    public static final int TRACERS = 2;

    private SpotExamination() {}

    /** Where an increment stands. */
    public sealed interface Standing permits NotShot, Accepted, TracersDue, RepairDue, Rejected {

        /** Whether the increment is finished with, accepted or not. */
        default boolean settled() {
            return this instanceof Accepted || this instanceof Rejected;
        }

        /** The standing as the progress sheet writes it. */
        String describe();
    }

    /** No spot has been shot on the increment yet. */
    public record NotShot() implements Standing {
        @Override
        public String describe() {
            return "the spot has not been shot";
        }
    }

    /**
     * The increment is accepted.
     *
     * @param afterRepair whether it took two tracers and a repair to get there
     */
    public record Accepted(boolean afterRepair) implements Standing {
        @Override
        public String describe() {
            return afterRepair ? "accepted after two tracers and a repair" : "accepted on the spot";
        }
    }

    /**
     * The spot failed and tracers are still to be shot.
     *
     * @param count how many are still to be shot
     */
    public record TracersDue(int count) implements Standing {
        @Override
        public String describe() {
            return count + (count == 1 ? " tracer spot is" : " tracer spots are") + " due";
        }
    }

    /**
     * Both tracers passed and the defect on the first spot has not been repaired and passed yet.
     *
     * @param attempts how many repair shots have failed so far
     */
    public record RepairDue(int attempts) implements Standing {
        @Override
        public String describe() {
            return attempts == 0
                    ? "the defect on the first spot is to be repaired and shot again"
                    : "the repair of the first spot failed " + attempts + (attempts == 1 ? " time" : " times");
        }
    }

    /**
     * A tracer failed and the increment is rejected.
     *
     * @param tracer which tracer failed, from one
     */
    public record Rejected(int tracer) implements Standing {
        @Override
        public String describe() {
            return "tracer " + tracer + " failed: reweld the increment or radiograph all of it";
        }
    }

    /** Where an increment stands after those spots, in the order they were shot. */
    public static Standing standing(List<Disposition> spots) {
        spots.forEach(spot -> Objects.requireNonNull(spot, "spot"));
        if (spots.isEmpty()) {
            return new NotShot();
        }
        if (spots.get(0) == Disposition.ACCEPT) {
            return new Accepted(false);
        }
        List<Disposition> tracers = spots.subList(1, Math.min(spots.size(), 1 + TRACERS));
        for (int i = 0; i < tracers.size(); i++) {
            if (tracers.get(i) != Disposition.ACCEPT) {
                return new Rejected(i + 1);
            }
        }
        if (tracers.size() < TRACERS) {
            return new TracersDue(TRACERS - tracers.size());
        }
        List<Disposition> repairs = spots.subList(1 + TRACERS, spots.size());
        if (!repairs.isEmpty() && repairs.get(repairs.size() - 1) == Disposition.ACCEPT) {
            return new Accepted(true);
        }
        return new RepairDue(repairs.size());
    }
}
