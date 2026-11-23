package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.DistanceRule;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.LeakTest;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanningServiceTest {

    @Test
    void filmKeysAreReadOffACommaSeparatedListInOrder() {
        assertThat(PlanningService.filmKeys(" d7, d5 ,,d4")).containsExactly("d7", "d5", "d4");
        assertThatThrownBy(() -> PlanningService.filmKeys(" , "))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("at least one film");
    }

    @Test
    void aPlanCarriesItsDistanceRuleOntoEveryFilmItIsComparedOn() {
        PlanRequest request = PlanRequest.builder(WELD, "IR-5512", "d7", SHOT)
                .distanceRule(DistanceRule.ISO_17636_CLASS_A)
                .build();
        assertThat(request.onFilm("d4").distanceRule()).isSameAs(DistanceRule.ISO_17636_CLASS_A);
        assertThat(PlanRequest.builder(WELD, "IR-5512", "d7", SHOT).build().distanceRule())
                .isSameAs(DistanceRule.ASME_V);
    }

    private static final LocalDateTime ASSAY = LocalDateTime.of(2024, 10, 1, 0, 0);
    private static final LocalDateTime SHOT = ASSAY.plusDays(20);
    private static final LocalDate OPENED = LocalDate.of(2024, 10, 1);
    private static final WeldId WELD = WeldId.parse("GW-104");

    private static SealedSource aSource(boolean wiped) {
        SealedSource source = new SealedSource(
                new SourceCertificate("IR-5512", Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                curie(15));
        if (wiped) {
            source.recordLeakTest(new LeakTest(ASSAY, BigDecimal.ZERO, "rp"));
        }
        return source;
    }

    private static JobNumber aJob(UnitOfWork work) {
        return JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", OPENED));
    }

    private static NewWeld aWeld(String nominal, String schedule) {
        return new NewWeld(
                WELD, PipeSchedule.girthWeld(nominal, schedule, Materials.STEEL, mm(2)), mm(529), "w-19", OPENED);
    }

    private static JobNumber prepared(UnitOfWork work, String nominal, String schedule, boolean wiped) {
        SourceService.registerSource(work, aSource(wiped));
        JobNumber job = aJob(work);
        JobService.addWeld(work, job, aWeld(nominal, schedule));
        return job;
    }

    private static JobNumber prepared(UnitOfWork work) {
        return prepared(work, "6", "80", true);
    }

    /** A double wall single image shot of the weld with the fixture source, on that film. */
    private static PlanRequest.Builder aRequest(String film) {
        return PlanRequest.builder(WELD, "IR-5512", film, SHOT).shot(ShotType.DWSI);
    }

    private static PlanRequest.Builder aRequest() {
        return aRequest("d7");
    }

    @Test
    void aSheetIsPlannedFromWhatIsOnTheRecord() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            TechniqueSheet sheet = PlanningService.plan(work, job, aRequest().build());
            assertThat(sheet.shot()).isSameAs(ShotType.DWSI);
            assertThat(sheet.penetrated()).isEqualTo(mm("25.94"));
            assertThat(sheet.isClean()).isTrue();
        });
    }

    @Test
    void theWallComesOffTheWeldAndNobodyRetypesIt() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work, "6", "40", true);
            assertThat(PlanningService.plan(work, job, aRequest().build()).penetrated())
                    .isEqualTo(mm("18.22"));
        });
    }

    @Test
    void theSourceIsDecayedToTheNightOfTheShot() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            TechniqueSheet early = PlanningService.plan(work, job, aRequest().build());
            TechniqueSheet late = PlanningService.plan(
                    work, job, aRequest().shotAt(SHOT.plusDays(90)).build());
            assertThat(late.exposure().exposure())
                    .isGreaterThan(early.exposure().exposure());
        });
    }

    @Test
    void aSlowerFilmGivesALongerSheet() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            TechniqueSheet fast = PlanningService.plan(work, job, aRequest("d7").build());
            TechniqueSheet slow = PlanningService.plan(work, job, aRequest("d4").build());
            assertThat(slow.exposure().exposure()).isGreaterThan(fast.exposure().exposure());
        });
    }

    @Test
    void theIndicatorSideTravelsThroughToTheSheet() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            TechniqueSheet sheet = PlanningService.plan(
                    work, job, aRequest().indicatorSide(IndicatorSide.FILM).build());
            assertThat(sheet.indicator().needsFMarker()).isTrue();
        });
    }

    @Test
    void aSheetWithWarningsStillComesBack() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            TechniqueSheet sheet = PlanningService.plan(
                    work, job, aRequest().window(minutes(1)).build());
            assertThat(sheet.isClean()).isFalse();
            assertThat(sheet.warnings()).isNotEmpty();
        });
    }

    @Test
    void aWeldThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobNumber job = prepared(work);
                    PlanningService.plan(
                            work, job, aRequest().weld(WeldId.parse("GW-999")).build());
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSourceThatIsNotThereIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobNumber job = prepared(work);
                    PlanningService.plan(
                            work, job, aRequest().sourceSerial("IR-9999").build());
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aJobThatIsNotThereIsRefusedToo() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    prepared(work);
                    PlanningService.plan(work, new JobNumber(24, 99), aRequest().build());
                }))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aRequestNamesASourceAndAFilm() {
        assertThatThrownBy(() -> PlanRequest.builder(WELD, "  ", "d7", SHOT).build())
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(
                        () -> PlanRequest.builder(WELD, "IR-5512", "  ", SHOT).build())
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aRequestMovedOntoAnotherFilmKeepsEverythingElse() {
        PlanRequest first =
                aRequest().window(minutes(30)).indicatorSide(IndicatorSide.FILM).build();
        PlanRequest moved = first.onFilm("d4");
        assertThat(moved.filmKey()).isEqualTo("d4");
        assertThat(moved.window()).isEqualTo(first.window());
        assertThat(moved.indicatorSide()).isEqualTo(first.indicatorSide());
        assertThat(moved.shot()).isEqualTo(first.shot());
    }

    @Test
    void severalFilmsAreComparedSideBySide() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            Map<String, TechniqueSheet> sheets =
                    PlanningService.compareFilms(work, job, aRequest().build(), List.of("d4", "d5", "d7"));
            assertThat(sheets).containsOnlyKeys("d4", "d5", "d7");
            assertThat(sheets.get("d4").exposure().exposure())
                    .isGreaterThan(sheets.get("d7").exposure().exposure());
        });
    }

    @Test
    void comparingNothingIsRefused() {
        Database made = TestDatabase.fresh();
        assertThatThrownBy(() -> made.transaction(work -> {
                    JobNumber job = prepared(work);
                    PlanningService.compareFilms(work, job, aRequest().build(), List.of());
                }))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theFastestWorkableFilmIsTheQuickestCleanOne() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            assertThat(PlanningService.fastestWorkable(work, job, aRequest().build(), List.of("d4", "d5", "d7")))
                    .contains("d7");
        });
    }

    @Test
    void nothingWorkableComesBackAsNothing() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work);
            assertThat(PlanningService.fastestWorkable(
                            work, job, aRequest().window(minutes(1)).build(), List.of("d4", "d7")))
                    .isEmpty();
        });
    }

    @Test
    void aWallOffTheEndOfTheChartStillRefuses() {
        TestDatabase.fresh().transaction(work -> {
            SourceService.registerSource(work, aSource(true));
            JobNumber job = aJob(work);
            JobService.addWeld(work, job, aWeld("24", "160"));
            assertThatThrownBy(() -> PlanningService.plan(work, job, aRequest().build()))
                    .isInstanceOf(TechniqueError.class);
        });
    }

    @Test
    void anUnwipedSourceShowsUpAsAWarningOnTheSheet() {
        TestDatabase.fresh().transaction(work -> {
            JobNumber job = prepared(work, "6", "80", false);
            assertThat(PlanningService.plan(work, job, aRequest().build()).warnings())
                    .anyMatch(warning -> warning.contains("leak test"));
        });
    }
}
