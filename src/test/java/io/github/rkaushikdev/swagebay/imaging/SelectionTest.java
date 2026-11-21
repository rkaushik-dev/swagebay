package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Selection.bandCeiling;
import static io.github.rkaushikdev.swagebay.imaging.Selection.bands;
import static io.github.rkaushikdev.swagebay.imaging.Selection.classBIsStricter;
import static io.github.rkaushikdev.swagebay.imaging.Selection.holeSelection;
import static io.github.rkaushikdev.swagebay.imaging.Selection.isBoundaryThickness;
import static io.github.rkaushikdev.swagebay.imaging.Selection.selectFor;
import static io.github.rkaushikdev.swagebay.imaging.Selection.tableBands;
import static io.github.rkaushikdev.swagebay.imaging.Selection.tableCeiling;
import static io.github.rkaushikdev.swagebay.imaging.Selection.wireSelection;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionTest {

    private static final IndicatorSide SOURCE = IndicatorSide.SOURCE;
    private static final IndicatorSide FILM = IndicatorSide.FILM;
    private static final TechniqueClass A = TechniqueClass.A;
    private static final TechniqueClass B = TechniqueClass.B;

    private static HoleSelection hole(IndicatorSelection selection) {
        assertThat(selection).isInstanceOf(HoleSelection.class);
        return (HoleSelection) selection;
    }

    private static WireSelection wire(IndicatorSelection selection) {
        assertThat(selection).isInstanceOf(WireSelection.class);
        return (WireSelection) selection;
    }

    @Test
    void aHalfInchWallTakesASeventeenOnTheSourceSide() {
        HoleSelection picked = holeSelection(inch("0.50"), SOURCE);
        assertThat(picked.indicator().identity()).isEqualTo(17);
        assertThat(picked.hole()).isSameAs(HoleSize.TWO_T);
    }

    @Test
    void theFilmSideColumnIsOneRowEasier() {
        for (String thickness : List.of("0.20", "0.30", "0.60", "1.20", "3.00")) {
            HoleSelection source = holeSelection(inch(thickness), SOURCE);
            HoleSelection film = holeSelection(inch(thickness), FILM);
            assertThat(film.indicator().identity())
                    .isLessThan(source.indicator().identity());
        }
    }

    @Test
    void aBandIsClosedAtTheTopSoABoundaryReadsDown() {
        assertThat(holeSelection(inch("0.25"), SOURCE).indicator().identity()).isEqualTo(12);
        assertThat(holeSelection(inch("0.2501"), SOURCE).indicator().identity()).isEqualTo(15);
        assertThat(holeSelection(inch("0.375"), SOURCE).indicator().identity()).isEqualTo(15);
        assertThat(holeSelection(inch("0.376"), SOURCE).indicator().identity()).isEqualTo(17);
    }

    @Test
    void aBoundaryThicknessIsRecognisedAsOne() {
        assertThat(isBoundaryThickness(inch("0.25"))).isTrue();
        assertThat(isBoundaryThickness(inch("2.00"))).isTrue();
        assertThat(isBoundaryThickness(inch("0.26"))).isFalse();
    }

    @Test
    void theBandCeilingShowsTheWorking() {
        assertThat(bandCeiling(inch("0.30"))).isEqualTo(inch("0.375"));
        assertThat(bandCeiling(inch("0.375"))).isEqualTo(inch("0.375"));
        assertThat(bandCeiling(inch("18"))).isEqualTo(inch("20.00"));
    }

    @Test
    void aThinWallTakesTheSmallestPlaqueInTheTable() {
        assertThat(holeSelection(mm(3), SOURCE).indicator().identity()).isEqualTo(12);
        assertThat(holeSelection(mm(3), FILM).indicator().identity()).isEqualTo(10);
        assertThat(holeSelection(new Length(1), SOURCE).indicator().identity()).isEqualTo(12);
    }

    @Test
    void theTableRunsOutAtTwentyInches() {
        assertThatThrownBy(() -> holeSelection(inch("20.01"), SOURCE))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("table_ends_at_in", "20.00"));
        assertThatThrownBy(() -> bandCeiling(inch(30))).isInstanceOf(TechniqueError.class);
    }

    @Test
    void aThicknessOfNothingIsRefused() {
        assertThatThrownBy(() -> holeSelection(Length.ZERO, SOURCE)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> holeSelection(new Length(-1), SOURCE)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theWholeTableRisesInBothColumns() {
        List<Selection.HoleBand> bands = tableBands();
        assertThat(bands).extracting(Selection.HoleBand::ceiling).isSorted();
        assertThat(bands).extracting(Selection.HoleBand::sourceSide).isSorted();
        assertThat(bands).extracting(Selection.HoleBand::filmSide).isSorted();
        assertThat(bands).hasSize(15);
    }

    @Test
    void theFilmSideColumnIsTheSourceColumnShiftedByOne() {
        List<Selection.HoleBand> bands = tableBands();
        for (int i = 1; i < bands.size(); i++) {
            assertThat(bands.get(i).filmSide()).isEqualTo(bands.get(i - 1).sourceSide());
        }
    }

    @Test
    void aFilmSideIndicatorHasToBeMarked() {
        assertThat(holeSelection(inch("0.50"), FILM).needsFMarker()).isTrue();
        assertThat(holeSelection(inch("0.50"), SOURCE).needsFMarker()).isFalse();
        assertThat(FILM.needsMarking()).isTrue();
        assertThat(SOURCE.needsMarking()).isFalse();
    }

    @Test
    void theSelectionPrintsTheWayTheReportReads() {
        assertThat(holeSelection(inch("0.50"), SOURCE).describe()).isEqualTo("17 2T on the source side");
        assertThat(holeSelection(inch("0.50"), FILM).describe()).isEqualTo("15 2T on the film side with an F marker");
    }

    @Test
    void theEssentialHoleIsTwoTAllTheWayDown() {
        for (Selection.HoleBand band : tableBands()) {
            assertThat(holeSelection(band.ceiling(), SOURCE).hole()).isSameAs(HoleSize.TWO_T);
            assertThat(holeSelection(band.ceiling(), FILM).hole()).isSameAs(HoleSize.TWO_T);
        }
    }

    @Test
    void aTenMillimetrePenetratedThicknessTakesW13InClassA() {
        WireSelection picked = wireSelection(mm(10), A);
        assertThat(picked.wire().number()).isEqualTo(13);
        assertThat(picked.wire().diameter()).isEqualTo(mm("0.20"));
    }

    @Test
    void classBAsksForAFinerWireAtTheSameThickness() {
        WireSelection basic = wireSelection(mm(10), A);
        WireSelection improved = wireSelection(mm(10), B);
        assertThat(improved.wire().number()).isGreaterThan(basic.wire().number());
        assertThat(improved.wire().diameter()).isLessThan(basic.wire().diameter());
        assertThat(classBIsStricter(mm(10))).isTrue();
    }

    @Test
    void theBandsAreClosedAtTheTop() {
        assertThat(wireSelection(mm("3.5"), A).wire().number()).isEqualTo(16);
        assertThat(wireSelection(mm("3.51"), A).wire().number()).isEqualTo(15);
        assertThat(wireSelection(mm(25), A).wire().number()).isEqualTo(11);
        assertThat(wireSelection(mm("25.1"), A).wire().number()).isEqualTo(10);
    }

    @Test
    void aThickerPenetrationNeedsACoarserWire() {
        List<Integer> numbers = List.of(1, 5, 20, 50, 120).stream()
                .map(t -> wireSelection(mm(t), A).wire().number())
                .toList();
        assertThat(numbers).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void theDoubleWallPenaltyLandsBandsFurtherDown() {
        WireSelection single = wireSelection(mm(10), A);
        WireSelection doubled = wireSelection(mm(20), A);
        assertThat(doubled.wire().number()).isLessThan(single.wire().number());
        assertThat(doubled.wire().diameter()).isGreaterThan(single.wire().diameter());
    }

    @Test
    void theSelectionSaysWhichStockSetCarriesTheWire() {
        WireSelection picked = wireSelection(mm(10), A);
        assertThat(picked.availableSets()).containsExactly("C", "D");
        assertThat(picked.describe()).contains("set C/D");
        assertThat(picked.describe()).contains("class A");
    }

    @Test
    void theFinestWireInClassBIsOnlyInTheLastSet() {
        WireSelection finest = wireSelection(mm(1), B);
        assertThat(finest.wire().number()).isEqualTo(19);
        assertThat(finest.availableSets()).containsExactly("D");
    }

    @Test
    void theTablesRunOutAtDifferentThicknesses() {
        assertThat(tableCeiling(A)).isEqualTo(mm(250));
        assertThat(tableCeiling(B)).isEqualTo(mm(350));
        assertThatThrownBy(() -> wireSelection(mm(300), A))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("technique_class", "A"));
        assertThat(wireSelection(mm(300), B).wire().number()).isEqualTo(6);
    }

    @Test
    void aPenetratedThicknessOfNothingIsRefused() {
        assertThatThrownBy(() -> wireSelection(Length.ZERO, A)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> wireSelection(new Length(-1), B)).isInstanceOf(ValidationError.class);
    }

    @Test
    void bothTablesRiseInThicknessAndFallInWireNumber() {
        for (TechniqueClass techniqueClass : List.of(A, B)) {
            List<Selection.WireBand> table = bands(techniqueClass);
            assertThat(table).extracting(Selection.WireBand::ceiling).isSorted();
            assertThat(table).extracting(Selection.WireBand::wire).isSortedAccordingTo(Comparator.reverseOrder());
        }
    }

    @Test
    void classBIsStricterOrEqualEverywhereTheTablesOverlap() {
        for (int thickness : List.of(1, 2, 5, 10, 20, 40, 80, 150, 200, 250)) {
            WireSelection basic = wireSelection(mm(thickness), A);
            WireSelection improved = wireSelection(mm(thickness), B);
            assertThat(improved.wire().diameter())
                    .isLessThanOrEqualTo(basic.wire().diameter());
        }
    }

    @Test
    void theClassesPrintTheWayASpecificationNamesThem() {
        assertThat(A).hasToString("class A");
        assertThat(B).hasToString("class B");
        assertThat(B.isImproved()).isTrue();
        assertThat(A.isImproved()).isFalse();
    }

    @Test
    void theFamilyDecidesWhichTableIsRead() {
        IndicatorSelection holeType = selectFor(Family.HOLE, inch("0.50"), mm(26));
        IndicatorSelection wireType = selectFor(Family.WIRE, inch("0.50"), mm(26));
        assertThat(hole(holeType).indicator().identity()).isEqualTo(17);
        assertThat(wire(wireType).wire().number()).isEqualTo(10);
    }

    @Test
    void theWireFamilyReadsThePenetratedThicknessAndTheHoleFamilyTheWall() {
        assertThat(Family.WIRE.measuredOnPenetratedThickness()).isTrue();
        assertThat(Family.HOLE.measuredOnPenetratedThickness()).isFalse();
        WireSelection single = wire(selectFor(Family.WIRE, inch("0.50"), mm(13)));
        WireSelection doubled = wire(selectFor(Family.WIRE, inch("0.50"), mm(26)));
        assertThat(single.wire().number()).isGreaterThan(doubled.wire().number());
    }

    @Test
    void theHoleFamilyIgnoresThePenetratedThicknessEntirely() {
        HoleSelection near = hole(selectFor(Family.HOLE, inch("0.50"), mm(13)));
        HoleSelection far = hole(selectFor(Family.HOLE, inch("0.50"), mm(90)));
        assertThat(near.indicator().identity()).isEqualTo(far.indicator().identity());
    }

    @Test
    void theEntryPointStillTakesASideAndAClass() {
        HoleSelection marked = hole(selectFor(Family.HOLE, inch("0.50"), mm(26), IndicatorSide.FILM, A));
        assertThat(marked.needsFMarker()).isTrue();
        WireSelection strict = wire(selectFor(Family.WIRE, inch("0.50"), mm(26), SOURCE, TechniqueClass.B));
        assertThat(strict.wire().number())
                .isGreaterThan(wireSelection(mm(26), TechniqueClass.A).wire().number());
    }

    @Test
    void theFamilyPrintsTheWayASpecificationNamesIt() {
        assertThat(Family.HOLE).hasToString("hole type");
        assertThat(Family.WIRE).hasToString("wire type");
    }
}
