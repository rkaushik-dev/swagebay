package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every repair on a job, keyed on the weld as first made, and the number the client actually
 * watches.
 *
 * <p>A second repair of the same weld needs the client to say yes. Not because two repairs are
 * twice as bad as one, but because a weld that has failed twice is telling somebody something about
 * the procedure or the welder, and the client is entitled to be part of that conversation.
 */
public final class RepairRegister {

    /** How many repair rounds of one weld may be made without the client approving. */
    public static final int REPAIRS_WITHOUT_APPROVAL = 1;

    private final List<Repair> repairs = new ArrayList<>();

    /** An empty register. */
    public RepairRegister() {}

    /**
     * Record a repair.
     *
     * @throws ConflictError for a round already recorded, or a round past the first without the
     *     client's approval
     */
    public void record(Repair repair) {
        List<Integer> rounds = roundsFor(repair.weld().original());
        if (rounds.contains(repair.round())) {
            throw new ConflictError(
                    "that repair round is already recorded",
                    Context.of("weld", repair.weld().toString()));
        }
        if (repair.round() > REPAIRS_WITHOUT_APPROVAL && !repair.clientApproved()) {
            throw new ConflictError(
                    "a second repair of the same weld needs the client to approve it",
                    Context.of("weld", repair.weld().toString(), "round", repair.round()));
        }
        repairs.add(repair);
    }

    /** Every repair, in the order recorded. */
    public List<Repair> repairs() {
        return Collections.unmodifiableList(repairs);
    }

    /** The rounds recorded against a weld, whichever round of it is named, in order. */
    public List<Integer> roundsFor(WeldId weld) {
        WeldId original = weld.original();
        return repairs.stream()
                .filter(repair -> repair.weld().original().equals(original))
                .map(Repair::round)
                .sorted()
                .toList();
    }

    /** Every weld that has been repaired at least once, as first made. */
    public Set<WeldId> repairedWelds() {
        Set<WeldId> welds = new TreeSet<>();
        repairs.forEach(repair -> welds.add(repair.weld().original()));
        return Collections.unmodifiableSet(welds);
    }

    /**
     * Repaired welds over welds shot, which is the number on the report.
     *
     * @throws ValidationError for fewer than one weld shot
     */
    public BigDecimal rateOver(int weldsShot) {
        if (weldsShot <= 0) {
            throw new ValidationError("a repair rate needs a denominator", Context.of("welds_shot", weldsShot));
        }
        return Decimals.divide(repairedWelds().size(), weldsShot);
    }

    /** The welds repaired more than once, in the order of their identifiers' text. */
    public List<WeldId> repeatOffenders() {
        return repairedWelds().stream()
                .filter(weld -> roundsFor(weld).size() > 1)
                .sorted(Comparator.comparing(WeldId::toString))
                .toList();
    }
}
