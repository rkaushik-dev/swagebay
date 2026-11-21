package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Placements.checkShimCovers;
import static io.github.rkaushikdev.swagebay.imaging.Placements.indicatorsRequired;
import static io.github.rkaushikdev.swagebay.imaging.Placements.minimumShimSize;
import static io.github.rkaushikdev.swagebay.imaging.Placements.panoramicPositions;
import static io.github.rkaushikdev.swagebay.imaging.Placements.planPlacements;
import static io.github.rkaushikdev.swagebay.imaging.Placements.shimFor;
import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.JointForm;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacementTest {

    private static Joint capped() {
        return capped("2.5");
    }

    private static Joint capped(String reinforcement) {
        return PipeSchedule.girthWeld("6", "40", STEEL, mm(reinforcement));
    }

    private static List<String> positions(List<Placement> placements) {
        return placements.stream()
                .map(placement ->
                        placement.position().map(ClockPosition::toString).orElse("none"))
                .toList();
    }

    @Test
    void aCappedWeldNeedsAShimTheHeightOfTheCap() {
        assertThat(shimFor(capped("2.5"))).hasValueSatisfying(shim -> {
            assertThat(shim.thickness()).isEqualTo(mm("2.5"));
            assertThat(shim.materialKey()).isEqualTo("carbon_steel");
        });
    }

    @Test
    void aGroundFlushWeldNeedsNoShim() {
        assertThat(shimFor(capped("0"))).isEmpty();
    }

    @Test
    void aShimWithNoThicknessIsNotAShim() {
        assertThatThrownBy(() -> new Shim(Length.ZERO, "carbon_steel")).isInstanceOf(ValidationError.class);
    }

    @Test
    void theShimHasToStandProudOnEverySide() {
        assertThat(minimumShimSize(mm(25))).isEqualTo(mm(31));
        assertThatCode(() -> checkShimCovers(mm(25), mm(31))).doesNotThrowAnyException();
        assertThatThrownBy(() -> checkShimCovers(mm(25), mm(28)))
                .isInstanceOfSatisfying(TechniqueError.class, caught -> assertThat(caught.context())
                        .containsEntry("needed_mm", "31"));
    }

    @Test
    void aPlaqueWithNoSizeIsRefused() {
        assertThatThrownBy(() -> minimumShimSize(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void oneIndicatorPerRadiographIsTheRule() {
        assertThat(indicatorsRequired(ShotType.DWSI, 3)).isEqualTo(3);
        assertThat(indicatorsRequired(ShotType.DWDI_ELLIPTICAL, 2)).isEqualTo(2);
        assertThat(indicatorsRequired(ShotType.CONTACT, 1)).isEqualTo(1);
    }

    @Test
    void aPanoramicShotTakesThreeHoweverManyExposuresItIs() {
        assertThat(indicatorsRequired(ShotType.PANORAMIC, 1)).isEqualTo(3);
        assertThat(indicatorsRequired(ShotType.PANORAMIC, 2)).isEqualTo(3);
    }

    @Test
    void aShotCountOfNothingIsRefused() {
        assertThatThrownBy(() -> indicatorsRequired(ShotType.DWSI, 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> panoramicPositions(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void theThreePanoramicIndicatorsSitAThirdOfTheWayRound() {
        List<ClockPosition> positions = panoramicPositions();
        assertThat(positions).extracting(ClockPosition::toString).containsExactly("12:00", "4:00", "8:00");
        assertThat(positions.get(0)).isEqualTo(ClockPosition.parse("12:00"));
    }

    @Test
    void aPanoramicPlanPutsAShimUnderEveryOne() {
        List<Placement> placements = planPlacements(capped(), ShotType.PANORAMIC, 1);
        assertThat(placements).hasSize(3);
        assertThat(placements)
                .allSatisfy(placement -> assertThat(placement.shim()).isPresent());
        assertThat(positions(placements)).containsExactly("12:00", "4:00", "8:00");
    }

    @Test
    void aDoubleWallPlanSpacesThemTheWayTheShotsAreSpaced() {
        List<Placement> placements = planPlacements(capped(), ShotType.DWSI, 3);
        assertThat(positions(placements)).containsExactly("12:00", "4:00", "8:00");
    }

    @Test
    void anEllipticalPlanPutsTwoAtRightAngles() {
        Joint small = PipeSchedule.girthWeld("1", "80", STEEL);
        List<Placement> placements = planPlacements(small, ShotType.DWDI_ELLIPTICAL, 2);
        assertThat(positions(placements)).containsExactly("12:00", "6:00");
    }

    @Test
    void aPlateWeldGetsNoClockPositionsAtAll() {
        Joint plate = new Joint(JointForm.PLATE_BUTT, mm(12), STEEL);
        List<Placement> placements = planPlacements(plate, ShotType.CONTACT, 2);
        assertThat(placements).hasSize(2);
        assertThat(placements)
                .allSatisfy(placement -> assertThat(placement.position()).isEmpty());
    }

    @Test
    void aFilmSidePlacementCarriesTheFMarker() {
        List<Placement> placements = planPlacements(capped(), ShotType.DWSI, 3, IndicatorSide.FILM);
        assertThat(placements)
                .allSatisfy(placement -> assertThat(placement.needsFMarker()).isTrue());
        assertThat(placements.get(0).describe()).contains("F marker");
    }

    @Test
    void theDefaultSideIsTheSourceSide() {
        Placement placement = planPlacements(capped(), ShotType.DWSI, 3).get(0);
        assertThat(placement.side()).isSameAs(IndicatorSide.SOURCE);
        assertThat(placement.needsFMarker()).isFalse();
    }

    @Test
    void thePlacementPrintsTheWayTheSheetReads() {
        Placement placement = planPlacements(capped("2.5"), ShotType.DWSI, 3).get(0);
        assertThat(placement.describe()).isEqualTo("source side at 12:00 on a 2.50 mm carbon_steel shim");
        Placement flush = planPlacements(capped("0"), ShotType.DWSI, 3).get(0);
        assertThat(flush.describe()).isEqualTo("source side at 12:00");
    }
}
