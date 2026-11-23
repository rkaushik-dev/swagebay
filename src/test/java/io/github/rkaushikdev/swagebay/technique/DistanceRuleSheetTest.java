package io.github.rkaushikdev.swagebay.technique;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.testing.SourceFixtures.SHOT_DAY;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.geometry.BeamGeometry;
import io.github.rkaushikdev.swagebay.geometry.DistanceRule;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.testing.GeometryFixtures;
import io.github.rkaushikdev.swagebay.testing.SourceFixtures;
import org.junit.jupiter.api.Test;

class DistanceRuleSheetTest {

    private final Joint sixInchWeld = GeometryFixtures.sixInchWeld();

    private TechniqueRequest.Builder request() {
        return TechniqueRequest.builder(
                sixInchWeld, SourceFixtures.iridiumSource(), GeometryFixtures.onD7(), Films.film("d7"), SHOT_DAY);
    }

    @Test
    void aRequestSetsTheDistanceBySectionFiveUnlessTold() {
        assertThat(request().build().distanceRule()).isSameAs(DistanceRule.ASME_V);
    }

    @Test
    void anOutsideShotToClassBStandsFurtherOffAndTakesLonger() {
        TechniqueSheet asme =
                Techniques.planTechnique(request().shot(ShotType.DWSI).build());
        TechniqueSheet iso = Techniques.planTechnique(request()
                .shot(ShotType.DWSI)
                .distanceRule(DistanceRule.ISO_17636_CLASS_B)
                .build());
        assertThat(iso.sourceToFilm()).isGreaterThan(asme.sourceToFilm());
        assertThat(iso.exposure().exposure()).isGreaterThan(asme.exposure().exposure());
        assertThat(iso.warnings()).noneMatch(warning -> warning.contains("asks for"));
    }

    @Test
    void aPanoramicShotTooCloseForClassBIsWarnedAboutInIsoTerms() {
        TechniqueSheet asme = Techniques.planTechnique(request().build());
        TechniqueSheet iso = Techniques.planTechnique(
                request().distanceRule(DistanceRule.ISO_17636_CLASS_B).build());
        assertThat(asme.shot()).isSameAs(ShotType.PANORAMIC);
        assertThat(asme.warnings()).noneMatch(warning -> warning.contains("unsharpness"));
        assertThat(iso.geometry()).isEqualTo(asme.geometry());
        assertThat(iso.warnings()).contains("source to object 73 mm is under the 222 mm ISO 17636-1 class B asks for");
    }

    @Test
    void theFaultNamesTheUnsharpnessUnderSectionFive() {
        BeamGeometry close = new BeamGeometry(mm(20), mm(20), mm(3));
        assertThat(Techniques.distanceFaults(DistanceRule.ASME_V, close, mm(10)))
                .containsExactly("geometric unsharpness 3.000 mm is over 0.508 mm");
        assertThat(Techniques.distanceFaults(DistanceRule.ISO_17636_CLASS_A, close, mm(10)))
                .containsExactly("source to object 20 mm is under the 166 mm ISO 17636-1 class A asks for");
        BeamGeometry far = new BeamGeometry(mm(400), mm(20), mm(3));
        for (DistanceRule rule : DistanceRule.values()) {
            assertThat(Techniques.distanceFaults(rule, far, mm(10))).isEmpty();
        }
    }
}
