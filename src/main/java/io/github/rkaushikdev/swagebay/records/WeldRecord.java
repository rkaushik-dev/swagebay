package io.github.rkaushikdev.swagebay.records;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Acceptance;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Disposition;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.IndicationSet;
import io.github.rkaushikdev.swagebay.evaluation.SpotExamination;
import io.github.rkaushikdev.swagebay.evaluation.Verdict;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every radiograph covering one weld, and where that leaves the weld.
 *
 * <p>The weld is judged as a whole, and the whole is not the sum: a weld with four sound
 * radiographs and one inadmissible one is not four fifths examined, it is not examined, because the
 * fifth of it nobody could read might be where the crack is. So there is no verdict until every
 * expected exposure is on the record, admissible and read.
 */
public final class WeldRecord {

    private final JobNumber job;
    private final WeldId weld;
    private final Length weldLength;
    private final Length wall;
    private final Code code;
    private final List<Radiograph> radiographs = new ArrayList<>();

    /** A weld with no radiographs yet, to be judged under that code at that wall. */
    public WeldRecord(JobNumber job, WeldId weld, Length weldLength, Length wall, Code code) {
        this.job = Objects.requireNonNull(job, "job");
        this.weld = Objects.requireNonNull(weld, "weld");
        this.weldLength = Objects.requireNonNull(weldLength, "weldLength");
        this.wall = Objects.requireNonNull(wall, "wall");
        this.code = Objects.requireNonNull(code, "code");
    }

    /** The job the weld is on. */
    public JobNumber job() {
        return job;
    }

    /** The weld. */
    public WeldId weld() {
        return weld;
    }

    /** The length of the weld. */
    public Length weldLength() {
        return weldLength;
    }

    /** The wall it is judged at. */
    public Length wall() {
        return wall;
    }

    /** The acceptance code it is judged under. */
    public Code code() {
        return code;
    }

    /** Every radiograph on the weld, in the order added. */
    public List<Radiograph> radiographs() {
        return Collections.unmodifiableList(radiographs);
    }

    /**
     * Put a radiograph on the weld.
     *
     * @throws ValidationError for a radiograph of another weld
     * @throws ConflictError for a radiograph number or an exposure already on the weld
     */
    public void add(Radiograph radiograph) {
        if (!radiograph.weld().equals(weld)) {
            throw new ValidationError(
                    "that radiograph belongs to another weld",
                    Context.of(
                            "expected",
                            weld.toString(),
                            "given",
                            radiograph.weld().toString()));
        }
        if (radiographs.stream().anyMatch(existing -> existing.number().equals(radiograph.number()))) {
            throw new ConflictError("that radiograph number is already on the weld");
        }
        if (radiographs.stream().anyMatch(existing -> existing.exposureIndex() == radiograph.exposureIndex())) {
            throw new ConflictError(
                    "that exposure is already on the weld", Context.of("index", radiograph.exposureIndex()));
        }
        radiographs.add(radiograph);
    }

    /** The radiographs that are not admissible and have to be reshot. */
    public List<Radiograph> inadmissible() {
        return radiographs.stream()
                .filter(radiograph -> !radiograph.isAdmissible())
                .toList();
    }

    /** The admissible radiographs nobody has read yet. */
    public List<Radiograph> unread() {
        return radiographs.stream()
                .filter(radiograph -> radiograph.isAdmissible() && !radiograph.hasBeenRead())
                .toList();
    }

    /** Everything read off the admissible radiographs, gathered along the weld. */
    public IndicationSet allIndications() {
        List<Indication> found = new ArrayList<>();
        for (Radiograph radiograph : radiographs) {
            if (radiograph.isAdmissible()) {
                found.addAll(radiograph.indications());
            }
        }
        return new IndicationSet(weldLength, found);
    }

    /**
     * The weld level verdict, or nothing while the coverage is incomplete.
     *
     * @throws ValidationError for fewer than one expected exposure
     */
    public Optional<Verdict> verdict(int expectedExposures) {
        if (isIncomplete(expectedExposures)) {
            return Optional.empty();
        }
        return Optional.of(Acceptance.judge(code, allIndications(), wall));
    }

    /**
     * Whether an expected exposure is missing, inadmissible or unread.
     *
     * @throws ValidationError for fewer than one expected exposure
     */
    public boolean isIncomplete(int expectedExposures) {
        return !blockers(expectedExposures).isEmpty();
    }

    /**
     * Why there is no verdict yet, in the words a coordinator would use.
     *
     * @throws ValidationError for fewer than one expected exposure
     */
    public List<String> blockers(int expectedExposures) {
        if (expectedExposures < 1) {
            throw new ValidationError("a weld needs at least one exposure");
        }
        List<String> out = new ArrayList<>();
        int missing = expectedExposures - radiographs.size();
        if (missing > 0) {
            out.add(missing + " of " + expectedExposures + " exposures are not shot");
        }
        for (Radiograph radiograph : inadmissible()) {
            out.add(radiograph.number() + " is not admissible and has to be reshot");
        }
        for (Radiograph radiograph : unread()) {
            out.add(radiograph.number() + " has not been read");
        }
        return List.copyOf(out);
    }

    /**
     * Where a spot examined increment stands under UW-52(d), judging each spot on its own in the
     * order it was shot. A spot that is not admissible or not yet read stops the count there, since
     * nobody can say what it would have set off.
     *
     * @throws ValidationError for a weld that is not worked to spot radiography
     */
    public SpotExamination.Standing spotStanding() {
        if (code != Code.ASME_VIII_SPOT) {
            throw new ValidationError(
                    "progressive examination is for a spot examined weld", Context.of("code", code.value()));
        }
        List<Disposition> spots = new ArrayList<>();
        for (Radiograph spot : radiographs.stream()
                .sorted(Comparator.comparingInt(Radiograph::exposureIndex))
                .toList()) {
            if (!spot.isAdmissible() || !spot.hasBeenRead()) {
                break;
            }
            spots.add(Acceptance.judge(code, new IndicationSet(weldLength, spot.indications()), wall)
                    .disposition());
        }
        return SpotExamination.standing(spots);
    }

    /**
     * The weld level disposition, or nothing while the coverage is incomplete.
     *
     * @throws ValidationError for fewer than one expected exposure
     */
    public Optional<Disposition> disposition(int expectedExposures) {
        return verdict(expectedExposures).map(Verdict::disposition);
    }
}
