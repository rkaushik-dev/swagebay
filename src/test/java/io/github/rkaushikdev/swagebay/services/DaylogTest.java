package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.mrPerHour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.safety.SurveyStage;
import io.github.rkaushikdev.swagebay.testing.ImagingFixtures;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DaylogTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 3);
    private static final LocalDateTime SHOT = LocalDateTime.of(2025, 3, 3, 23, 30);
    private static final WeldId WELD = WeldId.parse("GW-104");

    private static DayLog aLog() {
        return new DayLog(DAY, "Come By Chance", List.of("0142", "0201"), "880D-221", "IR-5512", "minus eight, clear");
    }

    /** A job opened on the first of March 2025, weld GW-104 on it, and that many shots of it on the day. */
    private static List<String> prepared(UnitOfWork work, int shots, boolean visible) {
        LocalDate opened = LocalDate.of(2025, 3, 1);
        JobNumber job = JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", opened));
        JobService.addWeld(
                work,
                job,
                new NewWeld(WELD, PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), mm(529), "w-19", opened));
        List<String> numbers = new ArrayList<>();
        for (int index = 1; index <= shots; index++) {
            ShotRecord shot =
                    new ShotRecord(WELD, index, SHOT, "RT-001 rev 1", ImagingFixtures.filmEvidence(visible, SHOT));
            numbers.add(ShootingService.recordShot(work, job, shot).number());
        }
        return numbers;
    }

    private static List<String> prepared(UnitOfWork work, int shots) {
        return prepared(work, shots, true);
    }

    @Test
    void aLogNamesTheSiteAndTheCrew() {
        assertThatThrownBy(() -> new DayLog(DAY, "  ", List.of("0142"), "880D-221", "IR-5512"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new DayLog(DAY, "site", List.of(), "880D-221", "IR-5512"))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aLogIsOpenUntilItIsClosed() {
        DayLog log = aLog();
        assertThat(log.isClosed()).isFalse();
        log.close(LocalDateTime.of(2025, 3, 4, 6, 0));
        assertThat(log.isClosed()).isTrue();
    }

    @Test
    void aLogIsOnlyClosedOnce() {
        DayLog log = aLog();
        log.close(LocalDateTime.of(2025, 3, 4, 6, 0));
        assertThatThrownBy(() -> log.close(LocalDateTime.of(2025, 3, 4, 7, 0))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aClosedLogIsNotEdited() {
        DayLog log = aLog();
        log.setAccount("quiet night");
        log.close(LocalDateTime.of(2025, 3, 4, 6, 0));
        assertThatThrownBy(() -> log.setAccount("actually not quiet")).isInstanceOf(ConflictError.class);
        assertThat(log.account()).isEqualTo("quiet night");
    }

    @Test
    void anAdditionAfterTheCloseIsANoteWithItsOwnTime() {
        DayLog log = aLog();
        log.close(LocalDateTime.of(2025, 3, 4, 6, 0));
        log.addNote(new Note(LocalDateTime.of(2025, 3, 4, 9, 0), "0142", "the meter was recalibrated"));
        assertThat(log.notes()).hasSize(1);
        assertThat(log.notes().get(0).writtenAt()).isAfter(log.closedAt().orElseThrow());
    }

    @Test
    void aNoteCannotBeBackdatedBeforeTheClose() {
        DayLog log = aLog();
        log.close(LocalDateTime.of(2025, 3, 4, 6, 0));
        assertThatThrownBy(() -> log.addNote(new Note(LocalDateTime.of(2025, 3, 4, 5, 0), "0142", "sneaking this in")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aNoteIsSignedAndSaysSomething() {
        assertThatThrownBy(() -> new Note(LocalDateTime.of(2025, 3, 4, 9, 0), "  ", "text"))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Note(LocalDateTime.of(2025, 3, 4, 9, 0), "0142", "  "))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theShotsOnADayComeBackInTimeOrder() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work, 3);
            List<String> numbers = DayLogService.shotsOn(work, DAY).stream()
                    .map(RadiographRow::getNumber)
                    .toList();
            assertThat(numbers).hasSize(3);
            assertThat(numbers).isSorted();
            assertThat(DayLogService.shotsOn(work, LocalDate.of(2025, 3, 4))).isEmpty();
        });
    }

    @Test
    void theWeldsShotThatDayAreListedOnceEach() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work, 3);
            assertThat(DayLogService.weldsShotOn(work, DAY)).containsExactly("GW-104");
        });
    }

    @Test
    void aShotWithNoSurveyIsAGapTheLogHasToExplain() {
        TestDatabase.fresh().transaction(work -> {
            List<String> numbers = prepared(work, 1);
            assertThat(DayLogService.gapsOn(work, DAY)).contains(numbers.get(0) + " has no survey");
        });
    }

    @Test
    void aSurveyedShotLeavesNoGap() {
        TestDatabase.fresh().transaction(work -> {
            List<String> numbers = prepared(work, 1);
            SurveyService.recordSurvey(
                    work,
                    numbers.get(0),
                    SurveyStage.AFTER,
                    SHOT,
                    "0142",
                    "Ludlum 3",
                    LocalDateTime.of(2025, 12, 1, 0, 0),
                    List.of(new Point("device", Length.fromM(1), 0, mrPerHour(1), true)));
            assertThat(DayLogService.gapsOn(work, DAY)).isEmpty();
            assertThat(DayLogService.surveysOn(work, DAY)).hasSize(1);
        });
    }

    @Test
    void anInadmissibleShotIsAGapToo() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work, 1, false);
            assertThat(DayLogService.gapsOn(work, DAY)).anyMatch(gap -> gap.contains("not admissible"));
        });
    }

    @Test
    void theSummaryIsAssembledAndTheRestIsTyped() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work, 2);
            DayLog log = aLog();
            log.setAccount("source hung once, cleared on the second crank");
            Map<String, Object> body = DayLogService.summary(work, log);
            assertThat(body.get("exposures")).isEqualTo(2);
            assertThat(body.get("welds")).isEqualTo(List.of("GW-104"));
            assertThat(body.get("crew")).isEqualTo(List.of("0142", "0201"));
            assertThat(body.get("closed")).isEqualTo(false);
            assertThat(log.account()).startsWith("source hung");
        });
    }

    @Test
    void aLogPrintsTheWayTheHandoverReads() {
        assertThat(aLog().describe()).isEqualTo("2025-03-03 Come By Chance, 2 on crew, open");
    }
}
