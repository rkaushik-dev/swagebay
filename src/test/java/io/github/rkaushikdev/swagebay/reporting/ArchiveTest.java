package io.github.rkaushikdev.swagebay.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArchiveTest {

    private static final LocalDate FILED = LocalDate.of(2024, 12, 1);

    private static Location location() {
        return new Location("Come By Chance", "A", "14");
    }

    private static Filed filed() {
        return filed("24-0001-R0001");
    }

    private static Filed filed(String number) {
        return filed(number, "24-0001", Retention.FIVE_YEARS, true);
    }

    private static Filed filed(String number, Retention retention) {
        return filed(number, "24-0001", retention, true);
    }

    private static Filed filed(String number, String job, Retention retention, boolean where) {
        return where
                ? new Filed(number, job, FILED, retention, location())
                : new Filed(number, job, FILED, retention, Optional.empty());
    }

    @Test
    void aFiveYearRetentionHasADate() {
        Filed entry = filed();
        assertThat(entry.retention().hasAnEndDate()).isTrue();
        assertThat(entry.destroyAfter()).contains(FILED.plusDays(1826));
    }

    @Test
    void aLifeOfTheLineRetentionHasNoDateRatherThanAMadeUpOne() {
        Filed entry = filed("24-0001-R0001", Retention.INDEFINITE);
        assertThat(entry.retention().hasAnEndDate()).isFalse();
        assertThat(entry.destroyAfter()).isEmpty();
        assertThat(entry.retention().days()).isEmpty();
    }

    @Test
    void aVesselLifeRetentionHasNoDateEither() {
        assertThat(filed("24-0001-R0001", Retention.VESSEL_LIFE).destroyAfter()).isEmpty();
    }

    @Test
    void nothingIndefiniteEverComesDue() {
        assertThat(filed("24-0001-R0001", Retention.INDEFINITE).isDueOn(LocalDate.of(2099, 1, 1)))
                .isFalse();
    }

    @Test
    void aFiveYearFilmComesDueOnTheDayAndNotBefore() {
        Filed entry = filed();
        assertThat(entry.isDueOn(FILED.plusDays(1825))).isFalse();
        assertThat(entry.isDueOn(FILED.plusDays(1826))).isTrue();
    }

    @Test
    void aFilmWithNoLocationIsAFaultRatherThanStored() {
        assertThat(filed("24-0001-R0001", "24-0001", Retention.FIVE_YEARS, false)
                        .faults())
                .containsExactly("24-0001-R0001 is marked retained with no location");
        assertThat(filed().faults()).isEmpty();
    }

    @Test
    void aLocationNeedsEveryPartFilledIn() {
        for (String missing : List.of("site", "cabinet", "box")) {
            assertThatThrownBy(() -> new Location(
                            missing.equals("site") ? "" : "s",
                            missing.equals("cabinet") ? "" : "A",
                            missing.equals("box") ? "" : "14"))
                    .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                            .containsEntry("field", missing));
        }
    }

    @Test
    void aFiledRadiographNeedsANumber() {
        assertThatThrownBy(() -> filed("  ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void nothingIsFiledTwice() {
        Archive archive = new Archive();
        archive.file(filed());
        assertThatThrownBy(() -> archive.file(filed())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aFilmIsFoundByItsNumber() {
        Archive archive = new Archive();
        archive.file(filed());
        assertThat(archive.find("24-0001-R0001").orElseThrow().job()).isEqualTo("24-0001");
        assertThat(archive.find("24-0001-R9999")).isEmpty();
    }

    @Test
    void theFilmsOnOneJobComeBackInOrder() {
        Archive archive = new Archive();
        archive.file(filed("24-0001-R0002"));
        archive.file(filed("24-0001-R0001"));
        archive.file(filed("24-0002-R0001", "24-0002", Retention.FIVE_YEARS, true));
        assertThat(archive.forJob("24-0001"))
                .extracting(Filed::radiograph)
                .containsExactly("24-0001-R0001", "24-0001-R0002");
    }

    @Test
    void theDestructionListOnlyNamesWhatHasADate() {
        Archive archive = new Archive();
        archive.file(filed("24-0001-R0001"));
        archive.file(filed("24-0001-R0002", Retention.INDEFINITE));
        LocalDate day = FILED.plusDays(2000);
        assertThat(archive.dueForDestruction(day)).containsExactly("24-0001-R0001");
    }

    @Test
    void theIndefiniteFilmsAreListedOnTheirOwn() {
        Archive archive = new Archive();
        archive.file(filed("24-0001-R0001"));
        archive.file(filed("24-0001-R0002", Retention.VESSEL_LIFE));
        archive.file(filed("24-0001-R0003", Retention.INDEFINITE));
        assertThat(archive.keptIndefinitely()).containsExactly("24-0001-R0002", "24-0001-R0003");
    }

    @Test
    void theArchiveReportsEveryFilmWithNoLocation() {
        Archive archive = new Archive();
        archive.file(filed("24-0001-R0001", "24-0001", Retention.FIVE_YEARS, false));
        archive.file(filed("24-0001-R0002"));
        assertThat(archive.faults()).hasSize(1);
    }

    @Test
    void aFilmAJobSaysExistsAndTheCabinetDoesNotIsReported() {
        Archive archive = new Archive();
        archive.file(filed("24-0001-R0001"));
        assertThat(archive.missingFrom(List.of("24-0001-R0001", "24-0001-R0002")))
                .containsExactly("24-0001-R0002");
        assertThat(archive.missingFrom(List.of("24-0001-R0001"))).isEmpty();
    }

    @Test
    void aLocationPrintsTheWaySomebodyWouldWalkToIt() {
        assertThat(location().describe()).isEqualTo("Come By Chance cabinet A box 14");
        assertThat(Retention.INDEFINITE).hasToString("life of the line");
    }
}
