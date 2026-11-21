package io.github.rkaushikdev.swagebay.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonnelTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 1);
    private static final LocalDate CERTIFIED = LocalDate.of(2023, 1, 15);
    private static final LocalDate EYES = LocalDate.of(2024, 1, 10);

    private static Person person(
            String badge, Level level, LicenceRole role, LocalDate certified, LocalDate vision, LocalDate colour) {
        return new Person(badge, "crew", level, role, certified, vision, colour);
    }

    private static Person person() {
        return person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, EYES, EYES);
    }

    private static Person person(Level level) {
        return person("0142", level, LicenceRole.RADIOGRAPHER, CERTIFIED, EYES, EYES);
    }

    private static Person certified(LocalDate certified) {
        return person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, certified, EYES, EYES);
    }

    @Test
    void theTwoAuthoritiesAreSeparate() {
        assertThat(Level.LEVEL_II.mayInterpret()).isTrue();
        assertThat(Level.LEVEL_I.mayInterpret()).isFalse();
        assertThat(LicenceRole.RADIOGRAPHER.mayOperateUnattended()).isTrue();
        assertThat(LicenceRole.ASSISTANT.mayOperateUnattended()).isFalse();
    }

    @Test
    void aLevelOneSetsUpAndDoesNotInterpret() {
        Person hand = person(Level.LEVEL_I);
        assertThat(hand.mayInterpretOn(TODAY)).isFalse();
        assertThat(hand.mayWriteTechniqueOn(TODAY)).isFalse();
        assertThat(hand.mayShootAloneOn(TODAY)).isTrue();
    }

    @Test
    void onlyALevelThreeApprovesAProcedure() {
        assertThat(person(Level.LEVEL_II).mayApproveProcedureOn(TODAY)).isFalse();
        assertThat(person(Level.LEVEL_III).mayApproveProcedureOn(TODAY)).isTrue();
    }

    @Test
    void aNewLevelTwoWithoutALicenceCannotTouchTheSource() {
        Person fresh = person("0142", Level.LEVEL_II, LicenceRole.NONE, CERTIFIED, EYES, EYES);
        assertThat(fresh.mayInterpretOn(TODAY)).isTrue();
        assertThat(fresh.mayShootAloneOn(TODAY)).isFalse();
    }

    @Test
    void aLapsedEyeExamStopsInterpretationAndNotTheShooting() {
        Person tired =
                person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, LocalDate.of(2023, 1, 10), EYES);
        assertThat(tired.lapsedOn(TODAY)).contains("near vision test");
        assertThat(tired.mayInterpretOn(TODAY)).isFalse();
        assertThat(tired.mayShootAloneOn(TODAY)).isTrue();
    }

    @Test
    void aLapsedCertificationStopsBoth() {
        Person old = certified(LocalDate.of(2020, 1, 15));
        assertThat(old.lapsedOn(TODAY)).contains("certification");
        assertThat(old.mayInterpretOn(TODAY)).isFalse();
        assertThat(old.mayShootAloneOn(TODAY)).isFalse();
    }

    @Test
    void everyLapsedCredentialIsReportedNotJustTheFirst() {
        LocalDate stale = LocalDate.of(2019, 1, 1);
        Person everything = person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, stale, stale, stale);
        assertThat(everything.lapsedOn(TODAY))
                .containsExactly("certification", "near vision test", "colour perception test");
    }

    @Test
    void theCertificationRunsThreeYearsToTheDay() {
        Person hand = certified(LocalDate.of(2021, 6, 1));
        assertThat(hand.certificationExpires()).isEqualTo(LocalDate.of(2024, 5, 31));
        assertThat(hand.isCurrentOn(LocalDate.of(2024, 5, 31))).isFalse();
        assertThat(hand.isCurrentOn(LocalDate.of(2024, 5, 30))).isTrue();
    }

    @Test
    void theVisionTestsRunAYear() {
        LocalDate eyes = LocalDate.of(2023, 6, 1);
        Person hand = person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, eyes, eyes);
        assertThat(hand.nearVisionExpires()).isEqualTo(LocalDate.of(2024, 5, 31));
        assertThat(hand.colourPerceptionExpires()).isEqualTo(LocalDate.of(2024, 5, 31));
        assertThat(hand.isCurrentOn(TODAY)).isFalse();
    }

    @Test
    void theColourTestCanBeTheOneThatBites() {
        Person hand =
                person("0142", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, EYES, LocalDate.of(2023, 1, 1));
        assertThat(hand.lapsedOn(TODAY)).containsExactly("colour perception test");
    }

    @Test
    void anAssistantAloneOnSiteIsNotSupervised() {
        Person assistant = person("0201", Level.LEVEL_I, LicenceRole.ASSISTANT, CERTIFIED, EYES, EYES);
        assertThat(Personnel.supervisionSatisfied(List.of(assistant), TODAY)).isFalse();
        assertThat(Personnel.supervisionSatisfied(List.of(assistant, person()), TODAY))
                .isTrue();
    }

    @Test
    void anEmptyCrewIsNotSupervisedEither() {
        assertThat(Personnel.supervisionSatisfied(List.of(), TODAY)).isFalse();
    }

    @Test
    void aCrewReportsWhoCanReadTheFilm() {
        Person reader = person();
        Person hand = person("0201", Level.LEVEL_I, LicenceRole.RADIOGRAPHER, CERTIFIED, EYES, EYES);
        Person blind =
                person("0303", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, LocalDate.of(2022, 1, 1), EYES);
        assertThat(Personnel.interpreters(List.of(reader, hand, blind), TODAY))
                .extracting(Person::badge)
                .containsExactly("0142");
    }

    @Test
    void aPersonNeedsABadgeAndTheRadiographyMethod() {
        assertThatThrownBy(() -> person("  ", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, EYES, EYES))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Person(
                        "1", "x", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, TODAY, TODAY, TODAY, Set.of("UT")))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aPersonPrintsWithTheLevelOnTheCard() {
        assertThat(person()).hasToString("crew (0142, Level II)");
        assertThat(LicenceRole.ASSISTANT).hasToString("radiographer assistant");
    }
}
