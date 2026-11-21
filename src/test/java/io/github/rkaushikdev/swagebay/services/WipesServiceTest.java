package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WipesServiceTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 10, 1, 0, 0);
    private static final LocalDateTime WIPED = LocalDateTime.of(2024, 10, 15, 9, 0);

    private static void stocked(UnitOfWork work, String serial) {
        SourceService.registerSource(
                work,
                new SealedSource(
                        new SourceCertificate(serial, Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                        curie(15)));
        if (work.sources().findDevice("880D-221").isEmpty()) {
            SourceService.registerDevice(work, "880D-221", "880d");
        }
    }

    private static void stocked(UnitOfWork work) {
        stocked(work, "IR-5512");
    }

    private static WipeResult wipe(UnitOfWork work, String serial, LocalDateTime at, long becquerels) {
        return WipeService.recordWipe(work, serial, at, BigDecimal.valueOf(becquerels), "rp");
    }

    @Test
    void aPassingWipeIsRecordedAndSetsTheNextDueDate() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            WipeResult result = wipe(work, "IR-5512", WIPED, 0);
            assertThat(result.passed()).isTrue();
            assertThat(result.expiresOn()).isEqualTo(WIPED.plusDays(180).toLocalDate());
            assertThat(result.sourceState()).isEqualTo("in service");
        });
    }

    @Test
    void aFailingWipeTakesTheSourceOutOfServiceAtOnce() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            WipeResult result = wipe(work, "IR-5512", WIPED, 400);
            assertThat(result.passed()).isFalse();
            assertThat(result.sourceState()).isEqualTo("awaiting disposal");
            assertThat(work.sources().get("IR-5512").getState()).isEqualTo("awaiting disposal");
        });
    }

    @Test
    void theLimitIsWhereTheRegulationPutsIt() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            stocked(work);
            assertThat(wipe(work, "IR-5512", WIPED, 184).passed()).isTrue();
        });
        made.transaction(work -> {
            stocked(work, "IR-6000");
            assertThat(wipe(work, "IR-6000", WIPED, 185).passed()).isFalse();
        });
    }

    @Test
    void aWipeNeedsASignatureAndABelievableResult() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThatThrownBy(() -> WipeService.recordWipe(work, "IR-5512", WIPED, BigDecimal.ZERO, "  "))
                    .isInstanceOf(ValidationError.class);
            assertThatThrownBy(() -> WipeService.recordWipe(work, "IR-5512", WIPED, BigDecimal.valueOf(-1), "rp"))
                    .isInstanceOf(ValidationError.class);
        });
    }

    @Test
    void aWipeAgainstASourceThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> wipe(work, "IR-9999", WIPED, 0)))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSourceNeverWipedHasNoDueDate() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(WipeService.wipeDueOn(work, "IR-5512")).isEmpty();
        });
    }

    @Test
    void theLatestPassingWipeSetsTheDueDate() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 0);
            LocalDateTime later = WIPED.plusDays(100);
            wipe(work, "IR-5512", later, 1);
            assertThat(WipeService.wipeDueOn(work, "IR-5512"))
                    .contains(later.plusDays(180).toLocalDate());
        });
    }

    @Test
    void aFailingWipeDoesNotSetADueDate() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 400);
            assertThat(WipeService.wipeDueOn(work, "IR-5512")).isEmpty();
        });
    }

    @Test
    void aSourceNeverWipedIsOverdueFromTheStart() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            assertThat(WipeService.wipesOverdue(work, LocalDate.of(2024, 10, 2)))
                    .containsExactly("IR-5512");
        });
    }

    @Test
    void aWipedSourceDropsOffTheOverdueListAndComesBackLater() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 0);
            assertThat(WipeService.wipesOverdue(work, LocalDate.of(2024, 12, 1)))
                    .isEmpty();
            assertThat(WipeService.wipesOverdue(work, LocalDate.of(2025, 6, 1))).containsExactly("IR-5512");
        });
    }

    @Test
    void aSourceOutOfServiceIsNotChasedForAWipe() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 400);
            assertThat(WipeService.wipesOverdue(work, LocalDate.of(2025, 6, 1))).isEmpty();
        });
    }

    @Test
    void theHistoryStartsWithTheAssay() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            List<HistoryEntry> sheet = WipeService.history(work, "IR-5512");
            assertThat(sheet.get(0).what()).isEqualTo("assay");
            assertThat(sheet.get(0).detail()).contains("90.0 Ci");
        });
    }

    @Test
    void theHistoryReadsForwards() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 0);
            WorkFixtures.passInspections(work, "880D-221", LocalDate.of(2024, 10, 20));
            SourceService.sendOut(
                    work, new Booking("IR-5512", "880D-221", "0142", "Come By Chance", LocalDate.of(2024, 10, 20)));
            List<HistoryEntry> sheet = WipeService.history(work, "IR-5512");
            assertThat(sheet).extracting(HistoryEntry::what).containsExactly("assay", "leak test", "sent out");
            assertThat(sheet).extracting(HistoryEntry::at).isSorted();
        });
    }

    @Test
    void theHistorySaysWhetherEachWipePassed() {
        TestDatabase.fresh().transaction(work -> {
            stocked(work);
            wipe(work, "IR-5512", WIPED, 400);
            HistoryEntry line = WipeService.history(work, "IR-5512").stream()
                    .filter(entry -> entry.what().equals("leak test"))
                    .findFirst()
                    .orElseThrow();
            assertThat(line.detail()).contains("failed");
            assertThat(line.detail()).contains("400 Bq removed by rp");
        });
    }

    @Test
    void theResultPrintsTheWayANoteToTheCrewReads() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            stocked(work);
            WipeResult passed = wipe(work, "IR-5512", WIPED, 0);
            assertThat(passed.describe()).isEqualTo("IR-5512 passed, current until 2025-04-13");
        });
        made.transaction(work -> {
            stocked(work, "IR-6000");
            WipeResult failed = wipe(work, "IR-6000", WIPED, 400);
            assertThat(failed.describe()).isEqualTo("IR-6000 failed, source is now awaiting disposal");
        });
    }
}
