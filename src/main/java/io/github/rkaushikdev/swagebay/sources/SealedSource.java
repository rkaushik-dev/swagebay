package io.github.rkaushikdev.swagebay.sources;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A sealed source the shop owns, with the history that decides whether it can go out.
 *
 * <p>Fitness to go out is three separate questions and they fail differently. Is there enough
 * activity left to make the shot in a reasonable time. Is the leak test current, which is a
 * regulatory question with a hard date. Is the source still in service rather than in transit or
 * waiting on disposal. A source can pass all three and still be the wrong source for the weld,
 * which is a planning question and lives elsewhere.
 *
 * <p>The state, the working floor and the wipe history change over the source's life, so this is
 * a mutable record of one physical source rather than a value.
 */
public final class SealedSource {

    /** The working floor a source gets unless the office sets another. */
    public static final Activity DEFAULT_WORKING_FLOOR = Activity.fromCurie(15);

    private final SourceCertificate certificate;
    private final List<LeakTest> leakTests;
    private SourceState state;
    private Activity workingFloor;

    /** A source in service, with the default working floor and no wipe on record. */
    public SealedSource(SourceCertificate certificate) {
        this(certificate, SourceState.IN_SERVICE);
    }

    /** A source in that state, with the default working floor and no wipe on record. */
    public SealedSource(SourceCertificate certificate, SourceState state) {
        this(certificate, state, DEFAULT_WORKING_FLOOR, List.of());
    }

    /** A source in service below which the office will not send it out, with no wipe on record. */
    public SealedSource(SourceCertificate certificate, Activity workingFloor) {
        this(certificate, SourceState.IN_SERVICE, workingFloor, List.of());
    }

    /**
     * A source with its whole history, as it is read back from the records. The wipes are taken as
     * they stand: none of them moves the source to another state.
     */
    public SealedSource(
            SourceCertificate certificate, SourceState state, Activity workingFloor, List<LeakTest> leakTests) {
        this.certificate = Objects.requireNonNull(certificate, "certificate");
        this.state = Objects.requireNonNull(state, "state");
        this.workingFloor = Objects.requireNonNull(workingFloor, "workingFloor");
        this.leakTests = new ArrayList<>(leakTests);
    }

    /** What came in the box. */
    public SourceCertificate certificate() {
        return certificate;
    }

    /** Where the source is in its life. */
    public SourceState state() {
        return state;
    }

    /** Move the source to another state. */
    public void setState(SourceState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    /** Below this activity the source stays in the pig. */
    public Activity workingFloor() {
        return workingFloor;
    }

    /** Set the activity below which the source stays in the pig. */
    public void setWorkingFloor(Activity workingFloor) {
        this.workingFloor = Objects.requireNonNull(workingFloor, "workingFloor");
    }

    /** Every wipe on record, in the order they were recorded. */
    public List<LeakTest> leakTests() {
        return Collections.unmodifiableList(leakTests);
    }

    /** The serial number on the certificate. */
    public String serial() {
        return certificate.serial();
    }

    /** The isotope on the certificate. */
    public Isotope isotope() {
        return certificate.isotope();
    }

    /** The activity at that moment, decayed from the certificate. */
    public Activity activityAt(LocalDateTime at) {
        return Decay.activityOn(certificate.assayActivity(), certificate.assayAt(), isotope(), at);
    }

    /** The most recent wipe, the first recorded of any taken at the same moment. */
    public Optional<LeakTest> latestLeakTest() {
        return leakTests.stream().max(Comparator.comparing(LeakTest::performedAt));
    }

    /** Whether the most recent wipe passed and has not yet expired at that moment. */
    public boolean leakTestCurrentAt(LocalDateTime at) {
        return latestLeakTest()
                .filter(LeakTest::passed)
                .map(latest -> at.isBefore(latest.expiresAt()))
                .orElse(false);
    }

    /** Everything wrong with taking this source to a job at that moment, every reason and not just the first. */
    public List<String> reasonsNotToUse(LocalDateTime at) {
        List<String> reasons = new ArrayList<>();
        if (!state.canBeUsed()) {
            reasons.add("source is " + state.value());
        }
        if (!leakTestCurrentAt(at)) {
            reasons.add("leak test is not current");
        }
        if (activityAt(at).isSpent(workingFloor)) {
            reasons.add("activity is below the working floor");
        }
        return List.copyOf(reasons);
    }

    /** Whether nothing stands in the way of taking the source to a job at that moment. */
    public boolean isFitAt(LocalDateTime at) {
        return reasonsNotToUse(at).isEmpty();
    }

    /** Add a wipe to the history; a failed one sends the source straight to disposal. */
    public void recordLeakTest(LeakTest test) {
        leakTests.add(Objects.requireNonNull(test, "test"));
        if (!test.passed()) {
            state = SourceState.AWAITING_DISPOSAL;
        }
    }

    /** The isotope and the serial: {@code Ir-192 IR-4471}. */
    @Override
    public String toString() {
        return isotope() + " " + serial();
    }
}
