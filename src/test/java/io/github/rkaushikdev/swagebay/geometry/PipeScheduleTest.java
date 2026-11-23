package io.github.rkaushikdev.swagebay.geometry;

import static io.github.rkaushikdev.swagebay.materials.Materials.STEEL;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PipeScheduleTest {

    @Test
    void nominalIsNotAMeasurement() {
        assertThat(PipeSchedule.outsideDiameter("2")).isEqualTo(mm("60.3"));
        assertThat(PipeSchedule.outsideDiameter("1/2")).isEqualTo(mm("21.3"));
        assertThat(PipeSchedule.outsideDiameter("6")).isEqualTo(mm("168.3"));
    }

    @Test
    void outsideDiameterDoesNotMoveWithTheSchedule() {
        for (String schedule : List.of("STD", "XS", "40", "80", "160")) {
            PipeSize size = PipeSchedule.resolve("6", schedule);
            assertThat(size.outsideDiameter()).isEqualTo(mm("168.3"));
        }
    }

    @Test
    void aHeavierScheduleEatsTheBore() {
        PipeSize light = PipeSchedule.resolve("6", "40");
        PipeSize heavy = PipeSchedule.resolve("6", "160");
        assertThat(heavy.wall()).isGreaterThan(light.wall());
        assertThat(heavy.insideDiameter()).isLessThan(light.insideDiameter());
        assertThat(light.wall()).isEqualTo(mm("7.11"));
        assertThat(heavy.wall()).isEqualTo(mm("18.26"));
    }

    @Test
    void standardAndScheduleFortyPartCompanyAboveTenInch() {
        assertThat(PipeSchedule.wall("8", "STD")).isEqualTo(PipeSchedule.wall("8", "40"));
        assertThat(PipeSchedule.wall("12", "STD")).isNotEqualTo(PipeSchedule.wall("12", "40"));
        assertThat(PipeSchedule.wall("12", "STD")).isEqualTo(mm("9.53"));
        assertThat(PipeSchedule.wall("12", "40")).isEqualTo(mm("10.31"));
    }

    @Test
    void standardWallIsFlatFromTwelveInchUp() {
        Set<Length> walls = Stream.of("12", "14", "16", "18", "20", "24")
                .map(nominal -> PipeSchedule.wall(nominal, "STD"))
                .collect(Collectors.toSet());
        assertThat(walls).isEqualTo(Set.of(mm("9.53")));
    }

    @Test
    void theScheduleNameIsCaseInsensitive() {
        assertThat(PipeSchedule.wall("6", "std")).isEqualTo(PipeSchedule.wall("6", "STD"));
        assertThat(PipeSchedule.resolve("6", "xs").schedule()).isEqualTo("XS");
    }

    @Test
    void anUnpublishedCombinationIsRefusedRatherThanInterpolated() {
        assertThatThrownBy(() -> PipeSchedule.wall("14", "XXS"))
                .isInstanceOfSatisfying(ConfigurationError.class, caught -> assertThat(caught.context())
                        .containsEntry("schedule", "XXS"));
        assertThatThrownBy(() -> PipeSchedule.wall("7", "40")).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> PipeSchedule.outsideDiameter("7")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theTableCoversTheSizesTheShopMeets() {
        List<String> sizes = PipeSchedule.nominalSizes();
        assertThat(sizes.get(0)).isEqualTo("1/2");
        assertThat(sizes.get(sizes.size() - 1)).isEqualTo("24");
        assertThat(sizes).hasSize(19);
        for (String nominal : sizes) {
            assertThat(PipeSchedule.wall(nominal, "STD").um()).isPositive();
        }
    }

    @Test
    void theStainlessSchedulesAreTheirOwnWalls() {
        assertThat(PipeSchedule.wall("2", "10S")).isEqualTo(mm("2.77"));
        assertThat(PipeSchedule.wall("2", "5s")).isEqualTo(mm("1.65"));
        assertThat(PipeSchedule.wall("6", "40S")).isEqualTo(PipeSchedule.wall("6", "40"));
        assertThat(PipeSchedule.wall("12", "40S")).isEqualTo(mm("9.53"));
        assertThat(PipeSchedule.wall("12", "40S")).isNotEqualTo(PipeSchedule.wall("12", "40"));
        assertThat(PipeSchedule.wall("24", "10S")).isEqualTo(mm("6.35"));
        assertThatThrownBy(() -> PipeSchedule.wall("2", "10")).isInstanceOf(ConfigurationError.class);
        assertThatThrownBy(() -> PipeSchedule.wall("14", "40S")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void theIntermediateCarbonSteelSchedulesAreCarried() {
        assertThat(PipeSchedule.wall("8", "20")).isEqualTo(mm("6.35"));
        assertThat(PipeSchedule.wall("10", "30")).isEqualTo(mm("7.80"));
        assertThat(PipeSchedule.wall("12", "60")).isEqualTo(mm("14.27"));
        assertThat(PipeSchedule.wall("16", "100")).isEqualTo(mm("26.19"));
        assertThat(PipeSchedule.wall("6", "120")).isEqualTo(mm("14.27"));
        assertThat(PipeSchedule.wall("20", "140")).isEqualTo(mm("44.45"));
        assertThat(PipeSchedule.wall("14", "10")).isEqualTo(mm("6.35"));
        assertThatThrownBy(() -> PipeSchedule.wall("6", "140")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void everySizeGetsHeavierAlongTheNumberedSchedules() {
        List<String> numbered = List.of("10", "20", "30", "40", "60", "80", "100", "120", "140", "160");
        for (String nominal : PipeSchedule.nominalSizes()) {
            List<Length> walls = numbered.stream()
                    .filter(schedule -> isPublished(nominal, schedule))
                    .map(schedule -> PipeSchedule.wall(nominal, schedule))
                    .toList();
            assertThat(walls).as(nominal).isSorted().doesNotHaveDuplicates();
            assertThat(walls).as(nominal).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void theColumnsAreTheOnesThePrintedTableCarries() {
        assertThat(PipeSchedule.SCHEDULES).hasSize(17).startsWith("5S", "10S").endsWith("160", "XXS");
        assertThat(PipeSchedule.resolve("3", "80s")).hasToString("NPS 3 sch 80S");
    }

    private static boolean isPublished(String nominal, String schedule) {
        try {
            PipeSchedule.wall(nominal, schedule);
            return true;
        } catch (ConfigurationError notPublished) {
            return false;
        }
    }

    @Test
    void aLineListLineBecomesAJoint() {
        Joint joint = PipeSchedule.girthWeld("6", "40", STEEL, mm("2.0"));
        assertThat(joint.form()).isSameAs(JointForm.PIPE_GIRTH);
        assertThat(joint.wall()).isEqualTo(mm("7.11"));
        assertThat(joint.od()).isEqualTo(mm("168.3"));
        assertThat(joint.totalThickness()).isEqualTo(mm("9.11"));
        assertThat(joint.isSmallBore()).isFalse();
    }

    @Test
    void smallLinesComeBackSmall() {
        Joint joint = PipeSchedule.girthWeld("2", "80", STEEL);
        assertThat(joint.isSmallBore()).isTrue();
        assertThat(joint.wall()).isEqualTo(mm("5.54"));
    }

    @Test
    void aTapeMeasureReadingFindsItsNominalSize() {
        assertThat(PipeSchedule.nearestNominal(mm("168.3"))).isEqualTo("6");
        assertThat(PipeSchedule.nearestNominal(mm("169.0"))).isEqualTo("6");
        assertThat(PipeSchedule.nearestNominal(mm("60.0"))).isEqualTo("2");
    }

    @Test
    void aReadingBetweenTwoSizesIsRefusedWhenItIsTooFarOff() {
        assertThatThrownBy(() -> PipeSchedule.nearestNominal(mm("900"))).isInstanceOf(ConfigurationError.class);
        assertThat(PipeSchedule.nearestNominal(mm("900"), BigDecimal.ONE)).isEqualTo("24");
    }

    @Test
    void aResolvedSizePrintsTheWayTheWorkOrderReads() {
        assertThat(PipeSchedule.resolve("1-1/2", "80")).hasToString("NPS 1-1/2 sch 80");
    }
}
