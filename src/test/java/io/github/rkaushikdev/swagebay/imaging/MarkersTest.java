package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Markers.MINIMUM_OVERLAP;
import static io.github.rkaushikdev.swagebay.imaging.Markers.coverageGaps;
import static io.github.rkaushikdev.swagebay.imaging.Markers.effectiveCoverage;
import static io.github.rkaushikdev.swagebay.imaging.Markers.filmsRequired;
import static io.github.rkaushikdev.swagebay.imaging.Markers.missingMarkers;
import static io.github.rkaushikdev.swagebay.imaging.Markers.planFilms;
import static io.github.rkaushikdev.swagebay.imaging.Markers.planMarkers;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarkersTest {

    @Test
    void theOverlapIsGivenBackOutOfEveryFilm() {
        assertThat(effectiveCoverage(mm(350))).isEqualTo(mm(325));
        assertThat(effectiveCoverage(mm(350), mm(50))).isEqualTo(mm(300));
        assertThat(mm(25)).isEqualTo(MINIMUM_OVERLAP);
    }

    @Test
    void anOverlapThatSwallowsTheFilmIsRefused() {
        assertThatThrownBy(() -> effectiveCoverage(mm(100), mm(100)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("film_mm", "100"));
        assertThatThrownBy(() -> effectiveCoverage(mm(100), mm(150))).isInstanceOf(TechniqueError.class);
    }

    @Test
    void aFilmAndAnOverlapBothNeedRealLengths() {
        assertThatThrownBy(() -> effectiveCoverage(Length.ZERO)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> effectiveCoverage(mm(350), new Length(-1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void aShortWeldTakesOneFilm() {
        assertThat(filmsRequired(mm(300), mm(350))).isEqualTo(1);
        assertThat(filmsRequired(mm(350), mm(350))).isEqualTo(1);
    }

    @Test
    void aLongWeldTakesMoreThanTheRawDivisionSuggests() {
        assertThat(filmsRequired(mm(1000), mm(350))).isEqualTo(3);
        assertThat(filmsRequired(mm(1000), mm(350), mm(100))).isEqualTo(4);
        assertThat(mm(1000).ratio(mm(350))).isLessThan(BigDecimal.valueOf(3));
    }

    @Test
    void aWeldNeedsALengthToBePlanned() {
        assertThatThrownBy(() -> filmsRequired(Length.ZERO, mm(350))).isInstanceOf(ValidationError.class);
    }

    @Test
    void thePlanLaysTheFilmsEndToEndWithTheOverlap() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        assertThat(spans).hasSize(3);
        assertThat(spans.get(0).startsAt()).isEqualTo(Length.ZERO);
        assertThat(spans.get(0).endsAt()).isEqualTo(mm(350));
        assertThat(spans.get(1).startsAt()).isEqualTo(mm(325));
        assertThat(spans.get(2).endsAt()).isEqualTo(mm(1000));
    }

    @Test
    void theLastFilmIsPulledBackSoItDoesNotRunOffTheEnd() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        FilmSpan last = spans.get(spans.size() - 1);
        assertThat(last.endsAt()).isEqualTo(mm(1000));
        assertThat(last.length()).isEqualTo(mm(350));
    }

    @Test
    void consecutiveFilmsReallyDoOverlap() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        for (int i = 1; i < spans.size(); i++) {
            assertThat(spans.get(i).startsAt()).isLessThan(spans.get(i - 1).endsAt());
        }
    }

    @Test
    void aSingleFilmPlanStartsAtZeroAndEndsAtTheWeld() {
        List<FilmSpan> spans = planFilms(mm(300), mm(350));
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).startsAt()).isEqualTo(Length.ZERO);
        assertThat(spans.get(0).endsAt()).isEqualTo(mm(300));
    }

    @Test
    void aMarkerGoesAtTheStartOfEveryFilmAndOneAtTheFarEnd() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        List<Marker> markers = planMarkers(spans);
        assertThat(markers).extracting(Marker::label).containsExactly("1", "2", "3", "4");
        assertThat(markers.get(0).at()).isEqualTo(Length.ZERO);
        assertThat(markers.get(markers.size() - 1).at()).isEqualTo(mm(1000));
    }

    @Test
    void markersCanBeAskedWhichFilmTheyLandOn() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        List<Marker> markers = planMarkers(spans);
        List<Marker> onFirst = spans.get(0).markersWithin(markers);
        assertThat(onFirst).extracting(Marker::label).containsExactly("1", "2");
    }

    @Test
    void aMarkerThatDidNotShowUpIsTheFinding() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        List<Marker> markers = planMarkers(spans);
        List<Marker> absent = missingMarkers(markers, Set.of("1", "2", "4"));
        assertThat(absent).extracting(Marker::label).containsExactly("3");
        assertThat(missingMarkers(markers, Set.of("1", "2", "3", "4"))).isEmpty();
    }

    @Test
    void aSoundPlanLeavesNoGaps() {
        List<FilmSpan> spans = planFilms(mm(1000), mm(350));
        assertThat(coverageGaps(spans, mm(1000))).isEmpty();
    }

    @Test
    void aGapInTheMiddleIsFoundAndMeasured() {
        List<FilmSpan> spans = List.of(new FilmSpan(1, Length.ZERO, mm(300)), new FilmSpan(2, mm(400), mm(700)));
        List<String> gaps = coverageGaps(spans, mm(700));
        assertThat(gaps).containsExactly("a gap from 300 mm to 400 mm");
    }

    @Test
    void aGapAtEitherEndIsFoundToo() {
        List<FilmSpan> spans = List.of(new FilmSpan(1, mm(50), mm(300)));
        List<String> gaps = coverageGaps(spans, mm(700));
        assertThat(gaps.get(0)).isEqualTo("nothing covers the first 50 mm");
        assertThat(gaps.get(1)).isEqualTo("nothing covers past 300 mm");
    }

    @Test
    void noFilmsAtAllIsReportedAsSuch() {
        assertThat(coverageGaps(List.of(), mm(700))).containsExactly("no films are planned");
        assertThatThrownBy(() -> planMarkers(List.of())).isInstanceOf(ValidationError.class);
    }

    @Test
    void aFilmSpanAndAMarkerBothNeedToMakeSense() {
        assertThatThrownBy(() -> new FilmSpan(1, mm(300), mm(300))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Marker("  ", mm(10))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Marker("1", new Length(-1))).isInstanceOf(ValidationError.class);
        assertThat(new Marker("3", mm(650))).hasToString("3 at 650 mm");
    }
}
