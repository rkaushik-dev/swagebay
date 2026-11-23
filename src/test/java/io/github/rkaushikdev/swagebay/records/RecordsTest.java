package io.github.rkaushikdev.swagebay.records;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Acceptance;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Disposition;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.IndicationSet;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.evaluation.SpotExamination;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordsTest {

    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 7, 10, 23, 15);
    private static final LocalDateTime READ = LocalDateTime.of(2024, 7, 11, 8, 0);
    private static final WeldId WELD = WeldId.parse("GW-104");

    private static final Admissibility GOOD = new Admissibility(true, List.of(), List.of("indicator", "density"));
    private static final Admissibility BAD =
            new Admissibility(false, List.of("the 2T hole is not visible"), List.of("indicator"));

    private static Radiograph film() {
        return film("R-001", 1);
    }

    private static Radiograph film(String number, int index) {
        return film(number, index, GOOD, List.of(), true);
    }

    private static Radiograph film(String number, int index, Admissibility admissibility) {
        return film(number, index, admissibility, List.of(), true);
    }

    private static Radiograph film(String number, int index, List<Indication> indications) {
        return film(number, index, GOOD, indications, true);
    }

    private static Radiograph film(
            String number, int index, Admissibility admissibility, List<Indication> indications, boolean read) {
        return new Radiograph(
                number,
                WELD,
                index,
                SHOT,
                "RT-001 rev 1",
                admissibility,
                indications,
                read ? "0142" : "",
                read ? Optional.of(READ) : Optional.empty());
    }

    private static WeldRecord weldRecord() {
        return new WeldRecord(JobNumber.parse("24-0417"), WELD, mm(500), mm(10), Code.API_1104);
    }

    private static Indication slag(long at) {
        return new Indication(Kind.ESI, mm(at), mm(30), mm(1));
    }

    @Test
    void aRadiographCarriesTheTechniqueItWasShotTo() {
        assertThat(film().techniqueReference()).isEqualTo("RT-001 rev 1");
        assertThat(film().isAdmissible()).isTrue();
        assertThat(film().hasBeenRead()).isTrue();
    }

    @Test
    void aRadiographWithoutATechniqueReferenceIsRefused() {
        assertThatThrownBy(() -> new Radiograph("R-001", WELD, 1, SHOT, "  ", GOOD))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aReadingNeedsBothAReaderAndADate() {
        assertThatThrownBy(() ->
                        new Radiograph("R-001", WELD, 1, SHOT, "RT-001", GOOD, List.of(), "0142", Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void indicationsCannotAppearWithoutSomebodyHavingReadTheFilm() {
        assertThatThrownBy(() -> new Radiograph(
                        "R-001",
                        WELD,
                        1,
                        SHOT,
                        "RT-001",
                        GOOD,
                        List.of(new Indication(Kind.ESI, mm(10), mm(10), mm(1))),
                        "",
                        Optional.empty()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void exposuresAreNumberedFromOne() {
        assertThatThrownBy(() -> film("R-001", 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> film("  ", 1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aWeldGathersItsRadiographs() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        record.add(film("R-002", 2));
        assertThat(record.radiographs()).hasSize(2);
    }

    @Test
    void aRadiographForAnotherWeldIsRefused() {
        WeldRecord record = weldRecord();
        Radiograph other = new Radiograph("R-009", WeldId.parse("GW-110"), 1, SHOT, "RT-001", GOOD);
        assertThatThrownBy(() -> record.add(other))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("expected", "GW-104"));
    }

    @Test
    void theSameNumberOrTheSameExposureTwiceIsRefused() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        assertThatThrownBy(() -> record.add(film("R-001", 2))).isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> record.add(film("R-002", 1))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aWeldWithEveryExposureReadReachesAVerdict() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        record.add(film("R-002", 2));
        assertThat(record.isIncomplete(2)).isFalse();
        assertThat(record.disposition(2)).contains(Disposition.ACCEPT);
    }

    @Test
    void aWeldShortOfExposuresReachesNoVerdict() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        assertThat(record.isIncomplete(2)).isTrue();
        assertThat(record.verdict(2)).isEmpty();
        assertThat(record.blockers(2)).contains("1 of 2 exposures are not shot");
    }

    @Test
    void oneInadmissibleRadiographStopsTheWholeWeld() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        record.add(film("R-002", 2, BAD));
        assertThat(record.isIncomplete(2)).isTrue();
        assertThat(record.verdict(2)).isEmpty();
        assertThat(record.blockers(2)).contains("R-002 is not admissible and has to be reshot");
    }

    @Test
    void anUnreadRadiographStopsItToo() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        record.add(film("R-002", 2, GOOD, List.of(), false));
        assertThat(record.blockers(2)).contains("R-002 has not been read");
    }

    @Test
    void indicationsFromEveryAdmissibleFilmAreJudgedTogether() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1, List.of(slag(10))));
        record.add(film("R-002", 2, List.of(slag(60))));
        assertThat(record.allIndications().size()).isEqualTo(2);
        assertThat(record.disposition(2)).contains(Disposition.REPAIR);
    }

    @Test
    void neitherFilmWouldHaveFailedOnItsOwn() {
        IndicationSet one = new IndicationSet(mm(500), List.of(slag(10)));
        assertThat(Acceptance.judge(Code.API_1104, one, mm(10)).accepted()).isTrue();
    }

    @Test
    void indicationsOnAnInadmissibleFilmAreNotCounted() {
        WeldRecord record = weldRecord();
        record.add(film("R-001", 1));
        record.add(film("R-002", 2, BAD, List.of(new Indication(Kind.C, mm(60), mm(30))), true));
        assertThat(record.allIndications().size()).isZero();
    }

    @Test
    void anExposureCountOfNothingIsRefused() {
        WeldRecord record = weldRecord();
        assertThatThrownBy(() -> record.isIncomplete(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aRadiographPrintsItsOwnState() {
        assertThat(film().describe()).isEqualTo("R-001 read by 0142, 0 indications");
        assertThat(film("R-001", 1, GOOD, List.of(), false).describe()).isEqualTo("R-001 admissible, not yet read");
        assertThat(film("R-001", 1, BAD, List.of(), false).describe()).contains("not admissible");
    }

    private static WeldRecord spotIncrement() {
        return new WeldRecord(JobNumber.parse("24-0417"), WELD, mm(3000), mm(15), Code.ASME_VIII_SPOT);
    }

    private static Indication fusion() {
        return new Indication(Kind.IF, mm(100), mm(3));
    }

    @Test
    void aSpotExaminedWeldIsJudgedSpotBySpot() {
        WeldRecord increment = spotIncrement();
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.NotShot());
        increment.add(film("R-001", 1, List.of(fusion())));
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.TracersDue(2));
        increment.add(film("R-002", 2));
        increment.add(film("R-003", 3));
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.RepairDue(0));
        increment.add(film("R-004", 4));
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.Accepted(true));
    }

    @Test
    void aFailedTracerRejectsTheIncrementWhateverItsNeighboursSay() {
        WeldRecord increment = spotIncrement();
        increment.add(film("R-002", 2, List.of(fusion())));
        increment.add(film("R-001", 1, List.of(fusion())));
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.Rejected(1));
    }

    @Test
    void aSpotNobodyCanReadStopsTheCount() {
        WeldRecord increment = spotIncrement();
        increment.add(film("R-001", 1, List.of(fusion())));
        increment.add(film("R-002", 2, BAD));
        increment.add(film("R-003", 3, GOOD, List.of(), false));
        assertThat(increment.spotStanding()).isEqualTo(new SpotExamination.TracersDue(2));
    }

    @Test
    void onlyASpotExaminedWeldHasAProgressiveStanding() {
        assertThatThrownBy(() -> weldRecord().spotStanding())
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("spot examined");
    }

    @Test
    void theBlockersRefuseANonsenseCountToo() {
        assertThatThrownBy(() -> weldRecord().blockers(0)).isInstanceOf(ValidationError.class);
        WeldRecord record = weldRecord();
        record.add(film());
        assertThat(record.blockers(1)).isEmpty();
        assertThat(record.isIncomplete(1)).isFalse();
    }
}
