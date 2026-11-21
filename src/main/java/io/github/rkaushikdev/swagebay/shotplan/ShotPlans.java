package io.github.rkaushikdev.swagebay.shotplan;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.personnel.Personnel;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.safety.Assignment;
import io.github.rkaushikdev.swagebay.safety.BoundaryKind;
import io.github.rkaushikdev.swagebay.safety.RadiationField;
import io.github.rkaushikdev.swagebay.safety.RopeOff;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The pre job package: the technique sheet plus everything the site imposes.
 *
 * <p>A technique sheet says what would make a good radiograph. It says nothing about whether the
 * job can be done at all, and on a running plant that is the harder question. The weld is in a pipe
 * rack with forty metres of clear ground round it and the restricted boundary comes out at a hundred
 * and thirty. Something has to give, and the options are a collimator, a barrier, a smaller source,
 * or coming back at three in the morning when the unit is down.
 *
 * <p>So this takes the sheet and applies the site: how much room there is, who is on the crew, what
 * they are carrying, and how long the window is. What comes back is a plan with a list of blockers.
 * The order the blockers are collected in is the order a supervisor would find them walking the job,
 * and every one is reported rather than the first, because a crew sent back twice for two different
 * reasons has lost a night either way.
 */
public final class ShotPlans {

    private static final BigDecimal DOWN_THE_BEAM = BigDecimal.ZERO;
    private static final BigDecimal OFF_THE_BEAM = BigDecimal.valueOf(180);

    private ShotPlans() {}

    /**
     * Apply the site to the sheet and collect everything in the way: the room, then the crew, then
     * the window. Each kitted badge gets an expected dose at the rope down the beam.
     *
     * @throws io.github.rkaushikdev.swagebay.TechniqueError for a field with no source in it
     */
    public static ShotPlan planShot(
            TechniqueSheet sheet,
            RadiationField radiationField,
            SiteConstraints site,
            List<Person> crew,
            List<Assignment> assignments,
            LocalDate day) {
        RopeOff rope = RopeOff.of(radiationField);
        List<String> blockers = new ArrayList<>();

        blockers.addAll(roomBlockers(rope, site));
        blockers.addAll(crewBlockers(crew, assignments, day));
        blockers.addAll(windowBlockers(sheet, site));

        List<CrewDose> doses = assignments.stream()
                .map(assignment -> expectedDose(radiationField, sheet, assignment.badge(), rope.downBeam()))
                .toList();
        return new ShotPlan(sheet, radiationField, rope, site, blockers, sheet.warnings(), doses);
    }

    /**
     * What somebody standing there takes while the source is out for the whole joint.
     *
     * <p>It is a small number and it should be. A crew that expects a measurable dose from standing
     * at the boundary has the boundary in the wrong place, and this exists to make that visible
     * before the shot rather than on the badge report three months later.
     *
     * @throws ValidationError for a stand off that is not positive, before the sheet is read
     */
    public static CrewDose expectedDose(
            RadiationField radiationField, TechniqueSheet sheet, String badge, Length standOff) {
        if (standOff.um() <= 0) {
            throw new ValidationError("somebody has to be standing somewhere");
        }
        DoseRate rate = radiationField.rateAt(standOff, DOWN_THE_BEAM);
        return new CrewDose(badge, rate.over(sheet.totalSourceOut()), standOff);
    }

    /** The badges whose expected dose on this job is over a stated ceiling, in the plan's order. */
    public static List<String> overExpected(ShotPlan plan, Dose ceiling) {
        return plan.expectedDose().stream()
                .filter(entry -> entry.expected().compareTo(ceiling) > 0)
                .map(CrewDose::badge)
                .toList();
    }

    /** Whether cutting the side field alone brings the job inside the ground. */
    public static boolean collimatorWouldHelp(RadiationField radiationField, SiteConstraints site) {
        Length offBeam = radiationField.boundary(BoundaryKind.RESTRICTED, OFF_THE_BEAM);
        return offBeam.compareTo(site.clearRadius()) > 0;
    }

    /** How much more clear ground the job needs. Zero when it fits. */
    public static Length shortfall(RopeOff rope, SiteConstraints site) {
        if (rope.downBeam().compareTo(site.clearRadius()) <= 0) {
            return Length.ZERO;
        }
        return rope.downBeam().minus(site.clearRadius());
    }

    private static List<String> roomBlockers(RopeOff rope, SiteConstraints site) {
        if (rope.downBeam().compareTo(site.clearRadius()) <= 0) {
            return List.of();
        }
        return List.of("the " + rope.kind() + " boundary is " + rope.downBeam().formatM() + " and the site has "
                + site.clearRadius().formatM() + " of clear ground");
    }

    private static List<String> crewBlockers(List<Person> crew, List<Assignment> assignments, LocalDate day) {
        if (crew.isEmpty()) {
            return List.of("there is no crew");
        }
        List<String> out = new ArrayList<>();
        if (!Personnel.supervisionSatisfied(crew, day)) {
            out.add("no licensed radiographer is on the crew");
        }
        if (Personnel.interpreters(crew, day).isEmpty()) {
            out.add("nobody on the crew may interpret the film");
        }
        for (Assignment assignment : assignments) {
            for (String missing : assignment.missingOn(day)) {
                out.add(assignment.badge() + ": " + missing);
            }
        }
        return out;
    }

    private static List<String> windowBlockers(TechniqueSheet sheet, SiteConstraints site) {
        if (site.window().isEmpty()) {
            return List.of();
        }
        if (sheet.totalSourceOut().compareTo(site.window().get()) > 0) {
            return List.of("the joint needs " + sheet.totalSourceOut().asTimer() + " of source out and the window is "
                    + site.window().get().asTimer());
        }
        return List.of();
    }
}
