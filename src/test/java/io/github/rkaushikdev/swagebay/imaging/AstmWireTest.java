package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Selection.astmWireSelection;
import static io.github.rkaushikdev.swagebay.imaging.Selection.selectFor;
import static io.github.rkaushikdev.swagebay.imaging.Selection.tableBands;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AstmWireTest {

    @Test
    void theSeriesRunsFromThirtyTwoTenThousandthsToThreeHundredAndTwentyThousandths() {
        assertThat(new AstmWire(1).diameter()).isEqualTo(inch("0.0032"));
        assertThat(new AstmWire(8).diameter()).isEqualTo(inch("0.016"));
        assertThat(new AstmWire(21).diameter()).isEqualTo(inch("0.320"));
        assertThat(IntStream.rangeClosed(1, 21).mapToObj(n -> new AstmWire(n).diameter()))
                .isSorted();
    }

    @Test
    void aWireOutsideTheSeriesIsRefused() {
        assertThatThrownBy(() -> new AstmWire(0)).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> new AstmWire(22)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theSetsOverlapByOneWire() {
        assertThat(new AstmWire(1).sets()).containsExactly("A");
        assertThat(new AstmWire(6).sets()).containsExactly("A", "B");
        assertThat(new AstmWire(11).sets()).containsExactly("B", "C");
        assertThat(new AstmWire(16).sets()).containsExactly("C", "D");
        assertThat(new AstmWire(21).sets()).containsExactly("D");
        assertThat(new AstmWire(13)).hasToString("wire 13");
    }

    @Test
    void theWireColumnSitsBesideTheHoleColumnOnTheSameWall() {
        AstmWireSelection source = astmWireSelection(inch("0.50"), IndicatorSide.SOURCE);
        AstmWireSelection film = astmWireSelection(inch("0.50"), IndicatorSide.FILM);
        assertThat(source.wire().number()).isEqualTo(7);
        assertThat(film.wire().number()).isEqualTo(6);
        assertThat(astmWireSelection(inch("0.51"), IndicatorSide.SOURCE).wire().number())
                .isEqualTo(8);
        assertThat(astmWireSelection(inch(20), IndicatorSide.SOURCE).wire().number())
                .isEqualTo(21);
    }

    @Test
    void theFilmSideIsOneWireEasierAllTheWayDown() {
        for (Selection.HoleBand band : tableBands()) {
            assertThat(band.filmWire()).as(band.ceiling().toString()).isLessThan(band.sourceWire());
        }
        assertThat(tableBands()).extracting(Selection.HoleBand::sourceWire).isSorted();
    }

    @Test
    void aFilmSideWireCarriesAnFMarker() {
        AstmWireSelection film = astmWireSelection(inch("0.30"), IndicatorSide.FILM);
        assertThat(film.needsFMarker()).isTrue();
        assertThat(film.describe()).isEqualTo("wire 5 on the film side with an F marker, set A");
        AstmWireSelection source = astmWireSelection(inch("0.30"), IndicatorSide.SOURCE);
        assertThat(source.describe()).isEqualTo("wire 6 on the source side, set A/B");
    }

    @Test
    void theAstmFamilyReadsTheWallNotThePenetratedThickness() {
        assertThat(Family.ASTM_WIRE.measuredOnPenetratedThickness()).isFalse();
        IndicatorSelection near = selectFor(Family.ASTM_WIRE, inch("0.50"), mm(13));
        IndicatorSelection far = selectFor(Family.ASTM_WIRE, inch("0.50"), mm(90));
        assertThat(near).isInstanceOf(AstmWireSelection.class).isEqualTo(far);
        assertThat(Family.ASTM_WIRE).hasToString("wire type, ASTM E747");
    }

    @Test
    void theWireColumnEndsWhereTheHoleTableDoes() {
        assertThatThrownBy(() -> astmWireSelection(inch(21), IndicatorSide.SOURCE))
                .isInstanceOf(TechniqueError.class);
        assertThatThrownBy(() -> astmWireSelection(Length.ZERO, IndicatorSide.SOURCE))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> Selection.bandCeiling(Length.ZERO)).isInstanceOf(ValidationError.class);
    }
}
