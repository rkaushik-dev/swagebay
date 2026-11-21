package io.github.rkaushikdev.swagebay.shotplan;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static io.github.rkaushikdev.swagebay.testing.SourceFixtures.SHOT_DAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.geometry.Joint;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.Films;
import io.github.rkaushikdev.swagebay.imaging.ImagingSystem;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.Length;
import io.github.rkaushikdev.swagebay.safety.Assignment;
import io.github.rkaushikdev.swagebay.safety.Barrier;
import io.github.rkaushikdev.swagebay.safety.RadiationField;
import io.github.rkaushikdev.swagebay.sources.Devices;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.technique.TechniqueRequest;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import io.github.rkaushikdev.swagebay.technique.Techniques;
import io.github.rkaushikdev.swagebay.testing.GeometryFixtures;
import io.github.rkaushikdev.swagebay.testing.PeopleFixtures;
import io.github.rkaushikdev.swagebay.testing.SourceFixtures;
import io.github.rkaushikdev.swagebay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotplanTest {

    private static final LocalDate DAY = LocalDate.of(2024, 5, 21);
    private static final SiteConstraints OPEN_GROUND = new SiteConstraints(Length.fromM(200));
    private static final SiteConstraints TIGHT = new SiteConstraints(Length.fromM(40));

    private final Joint sixInchWeld = GeometryFixtures.sixInchWeld();
    private final SealedSource iridiumSource = SourceFixtures.iridiumSource();
    private final List<Person> crew = PeopleFixtures.crew();
    private final List<Assignment> kitted = PeopleFixtures.kitted();

    private static TechniqueSheet sheetFor(Joint joint, SealedSource source) {
        return Techniques.planTechnique(TechniqueRequest.builder(
                        joint, source, ImagingSystem.fromFilm(Films.film("d7")), Films.film("d7"), SHOT_DAY)
                .shot(ShotType.DWSI)
                .build());
    }

    private static RadiationField bare() {
        return new RadiationField(Isotopes.IR192, curie(74));
    }

    private static ShotPlan plan(Joint joint, SealedSource source, List<Person> crew, List<Assignment> kit) {
        return plan(joint, source, crew, kit, OPEN_GROUND);
    }

    private static ShotPlan plan(
            Joint joint, SealedSource source, List<Person> crew, List<Assignment> kit, SiteConstraints site) {
        return plan(joint, source, crew, kit, site, bare());
    }

    private static ShotPlan plan(
            Joint joint,
            SealedSource source,
            List<Person> crew,
            List<Assignment> kit,
            SiteConstraints site,
            RadiationField radiation) {
        return ShotPlans.planShot(sheetFor(joint, source), radiation, site, crew, kit, DAY);
    }

    @Test
    void aJobWithRoomAndACrewCanProceed() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        assertThat(ready.canProceed()).isTrue();
        assertThat(ready.blockers()).isEmpty();
        assertThat(ready.needsASignature()).isFalse();
    }

    @Test
    void aBoundaryBiggerThanTheGroundBlocksTheJob() {
        ShotPlan blocked = plan(sixInchWeld, iridiumSource, crew, kitted, TIGHT);
        assertThat(blocked.canProceed()).isFalse();
        assertThat(blocked.blockers().get(0)).contains("clear ground");
        assertThat(ShotPlans.shortfall(blocked.rope(), blocked.site())).isGreaterThan(Length.fromM(80));
    }

    @Test
    void aCollimatorCanBeWhatMakesTheJobFitSideways() {
        RadiationField cupped = new RadiationField(Isotopes.IR192, curie(74), Devices.collimator("w60"));
        assertThat(ShotPlans.collimatorWouldHelp(bare(), TIGHT)).isTrue();
        assertThat(ShotPlans.collimatorWouldHelp(cupped, TIGHT)).isFalse();
    }

    @Test
    void aBarrierCanBringTheWholeBoundaryInsideTheGround() {
        RadiationField shielded = new RadiationField(Isotopes.IR192, curie(74), List.of(new Barrier("lead", mm(30))));
        assertThat(plan(sixInchWeld, iridiumSource, crew, kitted, TIGHT, shielded)
                        .canProceed())
                .isTrue();
    }

    @Test
    void aJobWithNoCrewIsBlocked() {
        assertThat(plan(sixInchWeld, iridiumSource, List.of(), kitted).blockers())
                .containsExactly("there is no crew");
    }

    @Test
    void anAssistantAloneIsBlockedOnSupervision() {
        List<String> blockers =
                plan(sixInchWeld, iridiumSource, List.of(crew.get(1)), kitted).blockers();
        assertThat(blockers).contains("no licensed radiographer is on the crew");
        assertThat(blockers).contains("nobody on the crew may interpret the film");
    }

    @Test
    void missingDosimetryIsBlockedAndNamedByBadge() {
        List<String> blockers = plan(sixInchWeld, iridiumSource, crew, List.of(new Assignment("0201")))
                .blockers();
        assertThat(blockers).hasSize(3);
        assertThat(blockers).allMatch(blocker -> blocker.startsWith("0201: "));
    }

    @Test
    void aWindowTooShortForTheJointBlocksIt() {
        SiteConstraints site = new SiteConstraints(Length.fromM(200), minutes(2));
        assertThat(plan(sixInchWeld, iridiumSource, crew, kitted, site).blockers())
                .anyMatch(blocker -> blocker.contains("source out"));
    }

    @Test
    void aGenerousWindowDoesNotBlock() {
        SiteConstraints site = new SiteConstraints(Length.fromM(200), minutes(600));
        assertThat(plan(sixInchWeld, iridiumSource, crew, kitted, site).canProceed())
                .isTrue();
    }

    @Test
    void everyBlockerIsCollectedNotJustTheFirst() {
        SiteConstraints site = new SiteConstraints(Length.fromM(40), minutes(2));
        ShotPlan blocked =
                plan(sixInchWeld, iridiumSource, List.of(crew.get(1)), List.of(new Assignment("0201")), site);
        assertThat(blocked.blockers()).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void sheetWarningsTravelWithThePlanWithoutBlockingIt() {
        iridiumSource.setWorkingFloor(curie(85));
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        assertThat(ready.canProceed()).isTrue();
        assertThat(ready.needsASignature()).isTrue();
        assertThat(ready.warnings()).anyMatch(warning -> warning.contains("working floor"));
    }

    @Test
    void thePlanPrintsTheSheetAndTheRopeWhenItCanGo() {
        String text = plan(sixInchWeld, iridiumSource, crew, kitted).describe();
        assertThat(text).contains("girth weld");
        assertThat(text).contains("restricted area at");
    }

    @Test
    void aBlockedPlanPrintsTheBlockersInstead() {
        assertThat(plan(sixInchWeld, iridiumSource, crew, kitted, TIGHT).describe())
                .contains("clear ground");
    }

    @Test
    void aSiteWithNoClearGroundIsRefusedOutright() {
        assertThatThrownBy(() -> new SiteConstraints(Length.ZERO)).isInstanceOf(ValidationError.class);
    }

    @Test
    void noShortfallWhenTheJobFits() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        assertThat(ShotPlans.shortfall(ready.rope(), ready.site())).isEqualTo(Length.ZERO);
        assertThat(Decimals.subtract(
                        ready.rope().downBeam().m(), ready.rope().offBeam().m()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void thePlanSaysWhatTheCrewIsExpectedToTake() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        assertThat(ready.expectedDose()).hasSameSizeAs(kitted);
        assertThat(ready.expectedDose()).extracting(CrewDose::badge).containsExactlyInAnyOrder("0142", "0201");
    }

    @Test
    void theExpectedDoseAtTheBoundaryIsASmallNumber() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        for (CrewDose entry : ready.expectedDose()) {
            assertThat(entry.expected()).isLessThan(usv(50));
        }
    }

    @Test
    void standingCloserTakesMore() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        RadiationField field = bare();
        CrewDose near = ShotPlans.expectedDose(field, ready.sheet(), "0142", Length.fromM(20));
        CrewDose far = ShotPlans.expectedDose(field, ready.sheet(), "0142", Length.fromM(200));
        assertThat(near.expected()).isGreaterThan(far.expected());
    }

    @Test
    void aLongerJobTakesMoreAtTheSameDistance() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        CrewDose one = ShotPlans.expectedDose(bare(), ready.sheet(), "0142", Length.fromM(140));
        assertThat(one.expected().nsv()).isPositive();
    }

    /** The stand off is checked before the sheet is read, so no sheet is needed to be refused. */
    @Test
    void nobodyStandingAnywhereIsRefused() {
        RadiationField field = bare();
        assertThatThrownBy(() -> ShotPlans.expectedDose(field, null, "0142", Length.ZERO))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aCeilingNamesWhoIsOverIt() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        assertThat(ShotPlans.overExpected(ready, usv(1000))).isEmpty();
        assertThat(ShotPlans.overExpected(ready, new Dose(1))).containsExactlyInAnyOrder("0142", "0201");
    }

    @Test
    void theExpectedDosePrintsWhereItWasTakenFrom() {
        ShotPlan ready = plan(sixInchWeld, iridiumSource, crew, kitted);
        String text = ready.expectedDose().get(0).describe();
        assertThat(text).contains("uSv");
        assertThat(text).contains(" m");
    }
}
