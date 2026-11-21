package io.github.rkaushikdev.swagebay.technique;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.exposure.Calculation;
import io.github.rkaushikdev.swagebay.exposure.Catalogue;
import io.github.rkaushikdev.swagebay.exposure.Correction;
import io.github.rkaushikdev.swagebay.exposure.CorrectionChain;
import io.github.rkaushikdev.swagebay.exposure.Corrections;
import io.github.rkaushikdev.swagebay.exposure.ExposureChart;
import io.github.rkaushikdev.swagebay.exposure.ExposureResult;
import io.github.rkaushikdev.swagebay.geometry.BeamGeometry;
import io.github.rkaushikdev.swagebay.geometry.Coverage;
import io.github.rkaushikdev.swagebay.geometry.CoveragePlan;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.geometry.Shots;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.HoleSelection;
import io.github.rkaushikdev.swagebay.imaging.Screens;
import io.github.rkaushikdev.swagebay.imaging.Selection;
import io.github.rkaushikdev.swagebay.materials.Quality;
import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.sources.Collimator;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Isotope;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The technique sheet: everything decided before the source comes out.
 *
 * <p>This is the spine. A weld, a source and an imaging system go in, and what comes out is the
 * piece of paper the crew works off: which shot, how many, how far back, which indicator, how long,
 * and everything that is wrong with the answer.
 *
 * <p>The order matters and it is not arbitrary. Geometry first, because the shot type decides the
 * penetrated thickness and the penetrated thickness decides everything downstream. Then the
 * distance, which is bounded from below by unsharpness and from above by exposure time. Then the
 * indicator, which is read off the penetrated thickness. Then coverage, which is read off the
 * distance. Then the exposure, which needs all of it plus the source decayed to the day.
 *
 * <p>Nothing here throws on a plan that is merely bad. A sheet with warnings on it is a real thing
 * that a Level III looks at and either signs or sends back. What does throw is a plan that cannot
 * exist: no shot type available, a thickness off the end of a chart, a source with nothing left in
 * it.
 */
public final class Techniques {

    /** The clearance a projector head needs all round to go down the bore. */
    public static final Length GUIDE_CLEARANCE = Length.fromMm(5);

    private Techniques() {}

    /** A panoramic shot takes the whole circumference at once and nothing else does. */
    public static CoveragePlan coverageFor(Joint joint, ShotType shot, BeamGeometry geometry) {
        if (shot == ShotType.PANORAMIC || !joint.form().isCircumferential()) {
            return new CoveragePlan(1, BigDecimal.valueOf(180), BigDecimal.valueOf(360), BigDecimal.ZERO);
        }
        Length standoff = geometry.sourceToObject();
        return Coverage.planCoverage(joint, standoff, Shots.exposuresRequired(joint, shot));
    }

    /**
     * Where the source ends up, which the shot type decides rather than the planner.
     *
     * <p>An inside shot has no free distance in it. A panoramic source sits on the axis, so the
     * distance is the pipe radius and nothing can be done about it. A single wall single image source
     * lies against the far bore, so the beam crosses the whole inside diameter and then one wall.
     *
     * <p>An outside shot is the other case entirely. The standoff is free, so it is set to the
     * closest the unsharpness rule allows, and the exposure time comes out of that rather than the
     * other way round.
     */
    public static BeamGeometry geometryFor(Joint joint, ShotType shot, Length focal) {
        if (shot == ShotType.PANORAMIC) {
            return new BeamGeometry(joint.insideDiameter().dividedBy(2), joint.wall(), focal);
        }
        if (shot == ShotType.SWSI) {
            return new BeamGeometry(joint.insideDiameter(), joint.wall(), focal);
        }
        Length gap = shot == ShotType.CONTACT ? joint.wall() : joint.od();
        Length sfd = BeamGeometry.minimumSourceToFilm(focal, gap, joint.wall());
        return BeamGeometry.fromSfd(sfd, gap, focal);
    }

    /**
     * Work the sheet through in the order the numbers depend on each other.
     *
     * @throws TechniqueError when no shot is available or the thickness is off the end of the chart
     * @throws io.github.rkaushikdev.swagebay.ConfigurationError for a density with no chart, or a
     *     collimator nobody knows about
     */
    public static TechniqueSheet planTechnique(TechniqueRequest request) {
        Joint joint = request.joint();
        Isotope isotope = request.source().isotope();
        Length focal = request.source().certificate().focalSize();

        ShotType shot = request.shot().orElseGet(() -> Shots.chooseShot(joint, focal, GUIDE_CLEARANCE));
        Length penetrated = Shots.penetratedThickness(joint, shot);

        BeamGeometry geometry = geometryFor(joint, shot, focal);
        Length sfd = geometry.sourceToFilm();

        HoleSelection indicator = Selection.holeSelection(penetrated, request.indicatorSide());

        CoveragePlan coverage = coverageFor(joint, shot, geometry);

        ExposureChart chart = Catalogue.chartFor(
                isotope.key(), joint.material().key(), Films.REFERENCE_FILM.key(), request.density());
        CorrectionChain chain = new CorrectionChain()
                .withCorrection(Corrections.distanceCorrection(chart.referenceSfd(), sfd))
                .withCorrection(Corrections.filmCorrection(Films.REFERENCE_FILM, request.film()))
                .withCorrection(screenCorrection(request, isotope));
        if (request.collimatorKey().isPresent()) {
            Collimator cup = Devices.collimator(request.collimatorKey().get());
            chain = chain.withCorrection(Corrections.collimatorCorrection(beamFraction(cup)));
        }
        Activity activity = request.source().activityAt(request.shotAt());
        ExposureResult exposure = Calculation.planExposure(chart, penetrated, chain, activity, request.window());

        Duration total = new Duration(Math.multiplyExact(exposure.exposure().ms(), coverage.exposures()));
        List<String> warnings = warnings(request, geometry, penetrated, exposure, total);
        warnings.addAll(screenFaults(request, isotope));

        return new TechniqueSheet(
                joint, shot, penetrated, geometry, sfd, coverage, indicator, exposure, total, warnings);
    }

    /**
     * Plan, and refuse outright if the sheet cannot be worked at all.
     *
     * @throws TechniqueError naming the joint when no exposure comes out of the sheet
     */
    public static TechniqueSheet sheetOrRefuse(TechniqueRequest request) {
        TechniqueSheet sheet = planTechnique(request);
        if (sheet.exposure().correctedReading().signum() == 0) {
            throw new TechniqueError(
                    "the exposure could not be worked out",
                    Context.of("joint", request.joint().describe()));
        }
        return sheet;
    }

    /**
     * Whether the pair on the cassette belongs on this shot: nothing when no screens are fitted or
     * the isotope has no screen pair specified, otherwise the note on a pair that does not suit the
     * beam.
     */
    public static List<String> screenFaults(TechniqueRequest request, Isotope source) {
        if (!request.screensFitted()) {
            return List.of();
        }
        Optional<Quality> quality = source.equivalenceQuality();
        if (quality.isEmpty()) {
            return List.of();
        }
        return Screens.wrongScreens(Screens.screensFor(quality.get()), quality.get()).stream()
                .toList();
    }

    private static List<String> warnings(
            TechniqueRequest request,
            BeamGeometry geometry,
            Length penetrated,
            ExposureResult exposure,
            Duration total) {
        List<String> out = new ArrayList<>(exposure.warnings());
        for (String reason : request.source().reasonsNotToUse(request.shotAt())) {
            out.add("source is not fit: " + reason);
        }
        if (!geometry.meets(request.joint().wall())) {
            out.add("geometric unsharpness " + geometry.geometricUnsharpness().formatMm(3) + " is over "
                    + BeamGeometry.unsharpnessLimit(request.joint().wall()).formatMm(3));
        }
        if (!request.source().isotope().covers(penetrated)) {
            out.add(request.source().isotope() + " is outside its practical range at " + penetrated.formatMm());
        }
        if (request.window().isPresent() && total.compareTo(request.window().get()) > 0) {
            out.add("the joint needs " + total.asTimer() + " of source out and the window is "
                    + request.window().get().asTimer());
        }
        return out;
    }

    /** Lead screens shorten the shot, and the pair depends on the beam. */
    private static Correction screenCorrection(TechniqueRequest request, Isotope source) {
        if (!request.screensFitted()) {
            return new Correction("screens", BigDecimal.ONE, "no screens");
        }
        Optional<Quality> quality = source.equivalenceQuality();
        if (quality.isEmpty()) {
            return new Correction("screens", BigDecimal.ONE, "no screen pair specified for this isotope");
        }
        Screens pair = Screens.screensFor(quality.get());
        return new Correction("screens", pair.timeFactor(), pair.describe());
    }

    /**
     * A collimator clips the beam edge, so a little exposure is lost.
     *
     * <p>The transmission on the cup is what leaks through the body of it, which is not the same
     * number. What is lost down the beam is the edge of the cone, and the shop measures it at a few
     * percent per cup rather than deriving it.
     */
    private static BigDecimal beamFraction(Collimator cup) {
        BigDecimal lost = Decimals.divide(BigDecimal.valueOf(cup.apertureDegrees()), BigDecimal.valueOf(3600));
        return Decimals.subtract(BigDecimal.ONE, lost);
    }
}
