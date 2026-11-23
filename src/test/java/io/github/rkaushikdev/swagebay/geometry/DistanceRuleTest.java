package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.technique.Techniques;
import io.github.rkaushikdev.swagebay.testing.GeometryFixtures;
import org.junit.jupiter.api.Test;

class DistanceRuleTest {

    private static final Length PELLET = mm(3);

    @Test
    void classAIsSevenAndAHalfSourceSizesTimesTheGapToTheTwoThirds() {
        assertThat(DistanceRule.ISO_17636_CLASS_A.minimumSourceToObject(mm(2), mm(8), mm(8)))
                .isEqualTo(mm(60));
        assertThat(DistanceRule.ISO_17636_CLASS_A.minimumSourceToObject(PELLET, mm(20), mm(10)))
                .isEqualTo(new Length(165_782));
    }

    @Test
    void classBDoublesTheMultiple() {
        assertThat(DistanceRule.ISO_17636_CLASS_B.minimumSourceToObject(PELLET, mm(20), mm(10)))
                .isEqualTo(new Length(331_563));
        assertThat(DistanceRule.ISO_17636_CLASS_B.minimumSourceToObject(mm(2), mm(8), mm(8)))
                .isEqualTo(mm(120));
    }

    @Test
    void theAsmeRuleIsTheUnsharpnessFormulaRearranged() {
        Length asme = DistanceRule.ASME_V.minimumSourceToObject(PELLET, mm(20), mm(10));
        assertThat(asme)
                .isEqualTo(BeamGeometry.minimumSourceToObject(PELLET, mm(20), BeamGeometry.unsharpnessLimit(mm(10))));
        assertThat(asme).isLessThan(DistanceRule.ISO_17636_CLASS_A.minimumSourceToObject(PELLET, mm(20), mm(10)));
        assertThat(BeamGeometry.minimumSourceToFilm(PELLET, mm(20), mm(10)))
                .isEqualTo(DistanceRule.ASME_V.minimumSourceToFilm(PELLET, mm(20), mm(10)));
    }

    @Test
    void aFilmAgainstThePartNeedsNoDistanceUnderEitherStandard() {
        for (DistanceRule rule : DistanceRule.values()) {
            assertThat(rule.minimumSourceToObject(PELLET, Length.ZERO, mm(10)))
                    .as(rule.value())
                    .isEqualTo(new Length(1));
        }
    }

    @Test
    void theIsoRulesAreToldFromTheAsmeOne() {
        assertThat(DistanceRule.ASME_V.isIso()).isFalse();
        assertThat(DistanceRule.ISO_17636_CLASS_A.isIso()).isTrue();
        assertThat(DistanceRule.ISO_17636_CLASS_B).hasToString("ISO 17636-1 class B");
    }

    @Test
    void aGeometryPlannedToClassAFailsClassB() {
        Length sod = DistanceRule.ISO_17636_CLASS_A.minimumSourceToObject(PELLET, mm(20), mm(10));
        BeamGeometry geometry = new BeamGeometry(sod, mm(20), PELLET);
        assertThat(geometry.meets(DistanceRule.ISO_17636_CLASS_A, mm(10))).isTrue();
        assertThat(geometry.meets(DistanceRule.ISO_17636_CLASS_B, mm(10))).isFalse();
        assertThat(geometry.meets(DistanceRule.ASME_V, mm(10))).isTrue();
        BeamGeometry oneShort = new BeamGeometry(new Length(sod.um() - 1), mm(20), PELLET);
        assertThat(oneShort.meets(DistanceRule.ISO_17636_CLASS_A, mm(10))).isFalse();
    }

    @Test
    void anOutsideShotStandsOffByTheRulesMinimum() {
        Joint joint = GeometryFixtures.sixInchWeld();
        BeamGeometry asme = Techniques.geometryFor(joint, ShotType.DWSI, PELLET);
        BeamGeometry iso = Techniques.geometryFor(joint, ShotType.DWSI, PELLET, DistanceRule.ISO_17636_CLASS_B);
        assertThat(asme).isEqualTo(Techniques.geometryFor(joint, ShotType.DWSI, PELLET, DistanceRule.ASME_V));
        assertThat(iso.objectToFilm()).isEqualTo(joint.od());
        assertThat(iso.sourceToObject())
                .isEqualTo(DistanceRule.ISO_17636_CLASS_B.minimumSourceToObject(PELLET, joint.od(), joint.wall()));
        assertThat(iso.sourceToObject()).isGreaterThan(asme.sourceToObject());
    }

    @Test
    void anInsideShotIsPlacedTheSameWhateverTheRule() {
        Joint joint = GeometryFixtures.twelveInchWeld();
        for (ShotType inside : new ShotType[] {ShotType.PANORAMIC, ShotType.SWSI}) {
            assertThat(Techniques.geometryFor(joint, inside, PELLET, DistanceRule.ISO_17636_CLASS_B))
                    .isEqualTo(Techniques.geometryFor(joint, inside, PELLET));
        }
        BeamGeometry contact = Techniques.geometryFor(joint, ShotType.CONTACT, PELLET, DistanceRule.ISO_17636_CLASS_A);
        assertThat(contact.objectToFilm()).isEqualTo(joint.wall());
    }

    @Test
    void nonsenseIsRefused() {
        DistanceRule rule = DistanceRule.ISO_17636_CLASS_A;
        assertThatThrownBy(() -> rule.minimumSourceToObject(Length.ZERO, mm(10), mm(10)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> rule.minimumSourceToObject(PELLET, mm(-1), mm(10)))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> rule.minimumSourceToObject(PELLET, mm(10), Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }
}
