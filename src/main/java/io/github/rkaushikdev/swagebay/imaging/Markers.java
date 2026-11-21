package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Planning films along a weld, and proving with location markers that the whole weld got shot.
 *
 * <p>Consecutive films have to overlap and the overlap has to be visible. A butt joint between two
 * films with no overlap leaves a strip of weld that is on neither, and there is no way to tell
 * afterwards how wide that strip was. So the plan works in effective coverage, the film length less
 * the overlap, and the film count comes out of that rather than out of the raw length.
 *
 * <p>Coverage is proved by markers, not by arithmetic. The plan says which markers should appear on
 * which film, the reader says which ones did, and the difference is the gap.
 */
public final class Markers {

    /** An inch, near enough: what the written practice asks for and what fits. */
    public static final Length MINIMUM_OVERLAP = Length.fromMm(25);

    private Markers() {}

    /** What one film adds to the total at the minimum overlap. */
    public static Length effectiveCoverage(Length filmLength) {
        return effectiveCoverage(filmLength, MINIMUM_OVERLAP);
    }

    /**
     * What one film adds to the total once the overlap is given back.
     *
     * @throws ValidationError for a film with no length or a negative overlap
     * @throws TechniqueError when the overlap is as long as the film
     */
    public static Length effectiveCoverage(Length filmLength, Length overlap) {
        if (filmLength.um() <= 0) {
            throw new ValidationError("a film has a length");
        }
        if (overlap.um() < 0) {
            throw new ValidationError("an overlap cannot be negative");
        }
        if (overlap.compareTo(filmLength) >= 0) {
            throw new TechniqueError(
                    "the overlap swallows the film",
                    Context.of(
                            "film_mm",
                            filmLength.mm().toString(),
                            "overlap_mm",
                            overlap.mm().toString()));
        }
        return filmLength.minus(overlap);
    }

    /** How many films it takes at the minimum overlap. */
    public static int filmsRequired(Length weldLength, Length filmLength) {
        return filmsRequired(weldLength, filmLength, MINIMUM_OVERLAP);
    }

    /**
     * How many films it takes, counting the overlap that is given back. A weld no longer than one
     * film takes that one film.
     *
     * @throws ValidationError for a weld with no length
     */
    public static int filmsRequired(Length weldLength, Length filmLength, Length overlap) {
        if (weldLength.um() <= 0) {
            throw new ValidationError("a weld has a length");
        }
        if (weldLength.compareTo(filmLength) <= 0) {
            return 1;
        }
        Length step = effectiveCoverage(filmLength, overlap);
        BigDecimal remaining = Decimals.divide(weldLength.um() - filmLength.um(), step.um());
        return Math.toIntExact(1 + Decimals.ceiling(remaining));
    }

    /** Where each film starts and ends at the minimum overlap. */
    public static List<FilmSpan> planFilms(Length weldLength, Length filmLength) {
        return planFilms(weldLength, filmLength, MINIMUM_OVERLAP);
    }

    /** Where each film starts and ends, with the last one pulled back to finish at the end of the weld. */
    public static List<FilmSpan> planFilms(Length weldLength, Length filmLength, Length overlap) {
        int count = filmsRequired(weldLength, filmLength, overlap);
        Length step = effectiveCoverage(filmLength, overlap);
        List<FilmSpan> spans = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Length start = new Length(step.um() * index);
            Length end = start.plus(filmLength);
            if (end.compareTo(weldLength) > 0) {
                end = weldLength;
                start = new Length(Math.max(0, end.um() - filmLength.um()));
            }
            spans.add(new FilmSpan(index + 1, start, end));
        }
        return List.copyOf(spans);
    }

    /**
     * A marker at the start of every film, numbered from one, and one more at the far end.
     *
     * @throws ValidationError when there are no films
     */
    public static List<Marker> planMarkers(List<FilmSpan> spans) {
        if (spans.isEmpty()) {
            throw new ValidationError("there are no films to mark");
        }
        List<Marker> markers = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            markers.add(new Marker(Integer.toString(i + 1), spans.get(i).startsAt()));
        }
        markers.add(new Marker(
                Integer.toString(spans.size() + 1), spans.get(spans.size() - 1).endsAt()));
        return List.copyOf(markers);
    }

    /** Anywhere along the weld that no film reaches, from the start of the weld to its end. */
    public static List<String> coverageGaps(List<FilmSpan> spans, Length weldLength) {
        if (spans.isEmpty()) {
            return List.of("no films are planned");
        }
        List<FilmSpan> ordered =
                spans.stream().sorted(Comparator.comparing(FilmSpan::startsAt)).toList();
        List<String> gaps = new ArrayList<>();
        FilmSpan first = ordered.get(0);
        if (first.startsAt().um() > 0) {
            gaps.add("nothing covers the first " + first.startsAt().formatMm(0));
        }
        Length reach = first.endsAt();
        for (FilmSpan span : ordered.subList(1, ordered.size())) {
            if (span.startsAt().compareTo(reach) > 0) {
                gaps.add("a gap from " + reach.formatMm(0) + " to "
                        + span.startsAt().formatMm(0));
            }
            if (span.endsAt().compareTo(reach) > 0) {
                reach = span.endsAt();
            }
        }
        if (reach.compareTo(weldLength) < 0) {
            gaps.add("nothing covers past " + reach.formatMm(0));
        }
        return List.copyOf(gaps);
    }

    /** Planned markers whose images did not turn up on any radiograph, in plan order. */
    public static List<Marker> missingMarkers(List<Marker> expected, Set<String> seenLabels) {
        return expected.stream().filter(m -> !seenLabels.contains(m.label())).toList();
    }
}
