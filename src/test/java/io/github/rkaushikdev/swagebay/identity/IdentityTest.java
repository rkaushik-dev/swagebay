package io.github.rkaushikdev.swagebay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IdentityTest {

    @Test
    void aJobNumberRoundTripsThroughItsCanonicalForm() {
        JobNumber job = JobNumber.parse("24-0417");
        assertThat(job.year()).isEqualTo(24);
        assertThat(job.serial()).isEqualTo(417);
        assertThat(job).hasToString("24-0417");
        assertThat(JobNumber.parse(job.toString())).isEqualTo(job);
    }

    @Test
    void jobNumbersSortByYearThenSerial() {
        Stream<JobNumber> numbers = Stream.of(new JobNumber(25, 3), new JobNumber(24, 900), new JobNumber(24, 12));
        assertThat(numbers.sorted().map(JobNumber::toString)).containsExactly("24-0012", "24-0900", "25-0003");
    }

    @Test
    void theNextJobNumberIsTheNextSerialAndItCanRunOut() {
        assertThat(new JobNumber(24, 417).next()).isEqualTo(new JobNumber(24, 418));
        assertThatThrownBy(() -> new JobNumber(24, 9999).next()).isInstanceOf(ValidationError.class);
    }

    @Test
    void aJobNumberThatIsNotShapedRightIsRefused() {
        for (String bad : List.of("", "240417", "2024-0417", "24-417", "24-04177", "ab-0417")) {
            assertThatThrownBy(() -> JobNumber.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void aWeldIdentifierCarriesItsRepairRound() {
        WeldId first = WeldId.parse("GW-104R1");
        WeldId asMade = WeldId.parse("GW-104");
        assertThat(asMade.isRepair()).isFalse();
        assertThat(first.isRepair()).isTrue();
        assertThat(first.repair()).isEqualTo(1);
        assertThat(first.original()).isEqualTo(asMade);
    }

    @Test
    void aRepairIsARoundOfTheSameWeldNotANewOne() {
        WeldId asMade = WeldId.parse("GW-104");
        WeldId first = asMade.nextRepair();
        WeldId second = first.nextRepair();
        assertThat(first).hasToString("GW-104R1");
        assertThat(second).hasToString("GW-104R2");
        assertThat(second.original()).isEqualTo(asMade);
        assertThat(first.number()).isEqualTo(second.number()).isEqualTo(asMade.number());
    }

    @Test
    void weldIdentifiersAreReadCaseInsensitivelyAndStoredUpper() {
        assertThat(WeldId.parse(" gw-104r1 ")).isEqualTo(new WeldId("GW", 104, 1));
        assertThat(WeldId.parse("bw-7")).hasToString("BW-7");
    }

    @Test
    void anUnknownPrefixIsRefused() {
        assertThatThrownBy(() -> WeldId.parse("XX-104")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new WeldId("XX", 104)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aWeldIdentifierThatIsNotShapedRightIsRefused() {
        for (String bad : List.of("", "GW104", "GW-", "GW-0R", "GW-104R", "GW-104-R1")) {
            assertThatThrownBy(() -> WeldId.parse(bad)).isInstanceOf(ValidationError.class);
        }
    }

    @Test
    void theDescriptionSpellsThePrefixOut() {
        assertThat(WeldId.parse("GW-104").describe()).isEqualTo("girth weld 104");
        assertThat(WeldId.parse("BW-7R2").describe()).isEqualTo("branch weld 7, repair 2");
    }

    @Test
    void weldsSortWithTheirRepairsAfterTheWeldAsFirstMade() {
        Stream<WeldId> welds = Stream.of(
                new WeldId("GW", 104, 2), new WeldId("GW", 104), new WeldId("GW", 103), new WeldId("GW", 104, 1));
        assertThat(welds.sorted().map(WeldId::toString)).containsExactly("GW-103", "GW-104", "GW-104R1", "GW-104R2");
    }

    @Test
    void aClockPositionIsReadTheFourWaysACrewWritesIt() {
        assertThat(ClockPosition.parse("12")).isEqualTo(new ClockPosition(0));
        assertThat(ClockPosition.parse("12:00")).isEqualTo(new ClockPosition(0));
        assertThat(ClockPosition.parse("top")).isEqualTo(new ClockPosition(0));
        assertThat(ClockPosition.parse("1200")).isEqualTo(new ClockPosition(0));
        assertThat(ClockPosition.parse("bottom")).isEqualTo(new ClockPosition(360));
    }

    @Test
    void aClockPositionCarriesMinutes() {
        assertThat(ClockPosition.parse("1:30")).isEqualTo(new ClockPosition(90));
        assertThat(ClockPosition.parse("130")).isEqualTo(new ClockPosition(90));
        assertThat(new ClockPosition(90)).hasToString("1:30");
        assertThat(new ClockPosition(0)).hasToString("12:00");
    }

    @Test
    void aClockPositionConvertsToDegreesRoundThePipe() {
        assertThat(ClockPosition.parse("3:00").degrees()).isEqualByComparingTo("90");
        assertThat(ClockPosition.parse("6:00").degrees()).isEqualByComparingTo("180");
        assertThat(ClockPosition.fromDegrees(BigDecimal.valueOf(270))).isEqualTo(ClockPosition.parse("9:00"));
        assertThat(ClockPosition.fromDegrees(BigDecimal.valueOf(450))).isEqualTo(ClockPosition.parse("3:00"));
    }

    @Test
    void theArcBetweenTwoPositionsRunsClockwise() {
        ClockPosition twelve = ClockPosition.parse("12:00");
        ClockPosition three = ClockPosition.parse("3:00");
        assertThat(twelve.arcTo(three)).isEqualTo(180);
        assertThat(three.arcTo(twelve)).isEqualTo(540);
        assertThat(twelve.arcTo(twelve)).isZero();
    }

    @Test
    void aClockPositionThatIsNotOnTheDialIsRefused() {
        for (String bad : List.of("", "13:00", "0:30", "12:60", "later", "-1")) {
            assertThatThrownBy(() -> ClockPosition.parse(bad)).isInstanceOf(ValidationError.class);
        }
        assertThatThrownBy(() -> new ClockPosition(720)).isInstanceOf(ValidationError.class);
    }
}
