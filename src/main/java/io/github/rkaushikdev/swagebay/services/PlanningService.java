package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.FilmSystem;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.persistence.SourceMapper;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.WorkMapper;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.SourceRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.technique.TechniqueRequest;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.github.rkaushikdev.swagebay.technique.Techniques;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Planning a shot from what is on the record rather than from what is typed.
 *
 * <p>The value of doing this against the database instead of against arguments is that the wall,
 * the diameter and the reinforcement come off the weld row, and the source activity comes off the
 * certificate decayed to the day. Nobody retypes any of it, so nobody mistypes any of it, and a sheet
 * planned in the office matches the weld the crew is standing in front of.
 *
 * <p>What still has to be said is the part that is a choice: which source, which film, which shot,
 * and what night. Those are decisions and they are the {@link PlanRequest}.
 *
 * <p>A plan is returned whether or not it is clean. A sheet with warnings on it is what a Level III
 * reads and signs off, and hiding it behind an exception would mean the only way to see the warnings
 * is to trigger them, which is backwards.
 */
public final class PlanningService {

    private PlanningService() {}

    /**
     * Read the weld and the source off the record, then plan the shot.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job, a weld or a source that is not
     *     there
     * @throws io.github.rkaushikdev.swagebay.TechniqueError when no technique can be worked for the
     *     weld at all
     */
    public static TechniqueSheet plan(UnitOfWork work, JobNumber job, PlanRequest request) {
        JobRow jobRow = work.jobs().get(job);
        WeldRow weldRow = work.welds().get(jobRow, request.weld());
        SourceRow sourceRow = work.sources().get(request.sourceSerial());

        FilmSystem chosen = Films.film(request.filmKey());
        return Techniques.planTechnique(TechniqueRequest.builder(
                        WorkMapper.rowToJoint(weldRow),
                        SourceMapper.rowToSource(sourceRow),
                        ImagingSystem.fromFilm(chosen),
                        chosen,
                        request.shotAt())
                .shot(request.shot())
                .indicatorSide(request.indicatorSide())
                .window(request.window())
                .density(request.density())
                .distanceRule(request.distanceRule())
                .build());
    }

    /**
     * The film keys in a comma separated list, each without its surrounding white space, the empty
     * ones left out, in the order given.
     *
     * @throws ValidationError when that leaves none
     */
    public static List<String> filmKeys(String films) {
        List<String> keys = Arrays.stream(films.split(",", -1))
                .map(Text::strip)
                .filter(key -> !key.isEmpty())
                .toList();
        if (keys.isEmpty()) {
            throw new ValidationError("name at least one film to compare", Context.of("films", films));
        }
        return keys;
    }

    /**
     * The same shot on several films, by film in the order given, which is how a shooter picks one.
     *
     * @throws ValidationError when no film is given
     */
    public static Map<String, TechniqueSheet> compareFilms(
            UnitOfWork work, JobNumber job, PlanRequest request, List<String> films) {
        if (films.isEmpty()) {
            throw new ValidationError("there is nothing to compare");
        }
        Map<String, TechniqueSheet> sheets = new LinkedHashMap<>();
        for (String key : films) {
            sheets.put(key, plan(work, job, request.onFilm(key)));
        }
        return Collections.unmodifiableMap(sheets);
    }

    /**
     * The film whose clean sheet keeps the source out the shortest time, the first given on a tie,
     * or nothing if no sheet is clean.
     *
     * @throws ValidationError when no film is given
     */
    public static Optional<String> fastestWorkable(
            UnitOfWork work, JobNumber job, PlanRequest request, List<String> films) {
        return compareFilms(work, job, request, films).entrySet().stream()
                .filter(entry -> entry.getValue().isClean())
                .min(Comparator.comparingLong(
                        entry -> entry.getValue().totalSourceOut().ms()))
                .map(Map.Entry::getKey);
    }
}
