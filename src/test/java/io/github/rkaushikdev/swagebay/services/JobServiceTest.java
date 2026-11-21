package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobServiceTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 9, 9);

    private static NewJob aJob(String client) {
        return new NewJob(client, "Come By Chance", Code.API_1104, "RT-001", OPENED);
    }

    private static NewJob aJob() {
        return aJob("Maritime Pipelines");
    }

    private static NewWeld aWeld(String weld, long length) {
        return new NewWeld(
                WeldId.parse(weld),
                PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)),
                mm(length),
                "w-19",
                OPENED);
    }

    private static NewWeld aWeld(String weld) {
        return aWeld(weld, 529);
    }

    private static NewWeld aWeld() {
        return aWeld("GW-104");
    }

    private static RadiographRow film(String number, int index, boolean admissible, boolean read) {
        RadiographRow row = new RadiographRow();
        row.setNumber(number);
        row.setExposureIndex(index);
        row.setExposedAt(LocalDateTime.of(2024, 9, 10, 23, 0));
        row.setTechniqueReference("RT-001 rev 1");
        row.setMedium("film");
        row.setAdmissible(admissible);
        row.setReadBy(read ? "0142" : null);
        row.setReadAt(read ? LocalDateTime.of(2024, 9, 11, 8, 0) : null);
        return row;
    }

    private static RadiographRow film() {
        return film("R-001", 1, true, true);
    }

    @Test
    void aJobGetsTheNextSerialForItsYear() {
        TestDatabase.fresh().transaction(work -> {
            assertThat(JobService.openJob(work, aJob())).isEqualTo(new JobNumber(24, 1));
            assertThat(JobService.openJob(work, aJob("Other"))).isEqualTo(new JobNumber(24, 2));
        });
    }

    @Test
    void theSerialSurvivesANewTransaction() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> JobService.openJob(work, aJob()));
        made.transaction(work -> assertThat(JobService.openJob(work, aJob())).isEqualTo(new JobNumber(24, 2)));
    }

    @Test
    void aJobThatRolledBackDoesNotBurnItsSerial() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobService.openJob(work, aJob());
                    throw new IllegalStateException("no");
                }))
                .isInstanceOf(IllegalStateException.class);
        made.transaction(work -> assertThat(JobService.openJob(work, aJob())).isEqualTo(new JobNumber(24, 1)));
    }

    @Test
    void aJobNeedsAClientASiteAndAProcedure() {
        for (String missing : List.of("client", "site", "procedure_number")) {
            assertThatThrownBy(() -> new NewJob(
                            missing.equals("client") ? "" : "c",
                            missing.equals("site") ? "" : "s",
                            Code.API_1104,
                            missing.equals("procedure_number") ? "" : "RT-001",
                            OPENED))
                    .isInstanceOfSatisfying(
                            ValidationError.class,
                            caught -> assertThat(caught.context().get("field")).isEqualTo(missing));
        }
    }

    @Test
    void aWeldGoesOntoAnOpenJob() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            JobRow job = work.jobs().get(number);
            assertThat(job.getWelds()).hasSize(1);
            assertThat(job.getWelds().get(0).getWall()).isEqualTo(mm("10.97"));
        });
    }

    @Test
    void theSameWeldTwiceIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            assertThatThrownBy(() -> JobService.addWeld(work, number, aWeld())).isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aRepairRoundIsADifferentWeldAsFarAsAddingGoes() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld("GW-104"));
            JobService.addWeld(work, number, aWeld("GW-104R1"));
            assertThat(work.jobs().get(number).getWelds()).hasSize(2);
        });
    }

    @Test
    void aWeldNeedsAWelderAndALength() {
        assertThatThrownBy(() -> aWeld("GW-104", 0)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new NewWeld(
                        WeldId.parse("GW-104"),
                        PipeSchedule.girthWeld("6", "80", Materials.STEEL),
                        mm(500),
                        "   ",
                        OPENED))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aWeldCannotBeAddedToAJobThatDoesNotExist() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> JobService.addWeld(work, new JobNumber(24, 99), aWeld())))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void anEmptyJobIsOutstandingOnHavingNoWelds() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            assertThat(JobService.outstanding(work, number)).contains("the job has no welds on it");
        });
    }

    @Test
    void aWeldWithNoRadiographsIsOutstanding() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            assertThat(JobService.outstanding(work, number)).contains("GW-104 has no radiographs");
        });
    }

    @Test
    void anUnreadOrInadmissibleFilmKeepsTheJobOpen() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            JobRow job = work.jobs().get(number);
            job.getWelds().get(0).addRadiograph(film("R-001", 1, true, false));
            job.getWelds().get(0).addRadiograph(film("R-002", 2, false, true));
            work.flush();
            List<String> pending = JobService.outstanding(work, number);
            assertThat(pending).contains("R-001 has not been read");
            assertThat(pending).contains("R-002 is not admissible");
        });
    }

    @Test
    void aFinishedJobCloses() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            work.jobs().get(number).getWelds().get(0).addRadiograph(film());
            work.flush();
            assertThat(JobService.outstanding(work, number)).isEmpty();
            JobService.closeJob(work, number, LocalDate.of(2024, 9, 20));
            assertThat(work.jobs().get(number).getClosedOn()).contains(LocalDate.of(2024, 9, 20));
        });
    }

    @Test
    void anUnfinishedJobRefusesToCloseAndSaysHowMany() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            assertThatThrownBy(() -> JobService.closeJob(work, number, LocalDate.of(2024, 9, 20)))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    caught.context().get("outstanding"))
                            .isEqualTo(1));
        });
    }

    @Test
    void aJobCannotCloseTwiceOrBeforeItOpened() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            work.jobs().get(number).getWelds().get(0).addRadiograph(film());
            work.flush();
            assertThatThrownBy(() -> JobService.closeJob(work, number, LocalDate.of(2024, 1, 1)))
                    .isInstanceOf(ValidationError.class);
            JobService.closeJob(work, number, LocalDate.of(2024, 9, 20));
            assertThatThrownBy(() -> JobService.closeJob(work, number, LocalDate.of(2024, 9, 21)))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aWeldCannotBeAddedToAClosedJob() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber number = JobService.openJob(work, aJob());
            JobService.addWeld(work, number, aWeld());
            work.jobs().get(number).getWelds().get(0).addRadiograph(film());
            work.flush();
            JobService.closeJob(work, number, LocalDate.of(2024, 9, 20));
            assertThatThrownBy(() -> JobService.addWeld(work, number, aWeld("GW-105")))
                    .isInstanceOf(ConflictError.class);
        });
    }
}
