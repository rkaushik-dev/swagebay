package io.github.rkaushikdev.swagebay.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.LicenceRole;
import io.github.rkaushikdev.swagebay.personnel.Person;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcedureTest {

    private static final LocalDate FIRST_EFFECTIVE = LocalDate.of(2023, 1, 1);
    private static final LocalDate FIRST_APPROVED = LocalDate.of(2022, 12, 15);
    private static final LocalDate SECOND_EFFECTIVE = LocalDate.of(2024, 3, 1);
    private static final LocalDate SECOND_APPROVED = LocalDate.of(2024, 2, 20);

    private static Person signer(Level level, LocalDate certified) {
        return new Person(
                "0001",
                "level three",
                level,
                LicenceRole.SAFETY_OFFICER,
                certified,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 1));
    }

    private static Person signer() {
        return signer(Level.LEVEL_III, LocalDate.of(2022, 1, 1));
    }

    private static Revision revision(int rev, LocalDate effective, LocalDate approved, Person by) {
        return new Revision("RT-001", rev, "radiography of butt welds", Code.ASME_V, effective, by, approved);
    }

    private static Revision revision(int rev, LocalDate effective, LocalDate approved) {
        return revision(rev, effective, approved, signer());
    }

    private static Revision revision(int rev) {
        return revision(rev, FIRST_EFFECTIVE, FIRST_APPROVED);
    }

    private static Revision signedBy(Person by) {
        return revision(0, FIRST_EFFECTIVE, FIRST_APPROVED, by);
    }

    @Test
    void aRevisionLabelsItselfTheWayTheCoverSheetDoes() {
        assertThat(revision(2).label()).isEqualTo("RT-001 rev 2");
        assertThat(revision(2)).hasToString("RT-001 rev 2 (ASME Section V)");
    }

    @Test
    void anApprovalNeedsBothASignatureAndADate() {
        assertThatThrownBy(() -> new Revision(
                        "RT-001", 0, "t", Code.ASME_V, FIRST_EFFECTIVE, Optional.of(signer()), Optional.empty()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Revision(
                        "RT-001",
                        0,
                        "t",
                        Code.ASME_V,
                        FIRST_EFFECTIVE,
                        Optional.empty(),
                        Optional.of(LocalDate.of(2022, 12, 1))))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aDraftWithNoApprovalAtAllIsALegalObject() {
        Revision draft = new Revision("RT-002", 0, "draft", Code.ASME_V, FIRST_EFFECTIVE);
        assertThat(draft.isApproved()).isFalse();
        assertThat(draft.approvalIsSound()).isFalse();
    }

    @Test
    void aRevisionCannotTakeEffectBeforeItWasSigned() {
        assertThatThrownBy(() -> revision(0, LocalDate.of(2022, 1, 1), FIRST_APPROVED))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void onlyALevelThreeSignatureIsSound() {
        assertThat(revision(0).approvalIsSound()).isTrue();
        assertThat(signedBy(signer(Level.LEVEL_II, LocalDate.of(2022, 1, 1))).approvalIsSound())
                .isFalse();
    }

    @Test
    void aSignatureFromSomebodyWhoseCertificationHadLapsedIsNotSound() {
        Person lapsed = signer(Level.LEVEL_III, LocalDate.of(2018, 1, 1));
        assertThat(signedBy(lapsed).approvalIsSound()).isFalse();
    }

    @Test
    void theRegisterAnswersAsOfADateAndNotAsOfToday() {
        ProcedureRegister register = new ProcedureRegister(
                List.of(revision(0, FIRST_EFFECTIVE, FIRST_APPROVED), revision(1, SECOND_EFFECTIVE, SECOND_APPROVED)));
        assertThat(register.effectiveOn("RT-001", LocalDate.of(2023, 6, 1)).revision())
                .isZero();
        assertThat(register.effectiveOn("RT-001", LocalDate.of(2024, 6, 1)).revision())
                .isEqualTo(1);
        assertThat(register.effectiveOn("RT-001", SECOND_EFFECTIVE).revision()).isEqualTo(1);
    }

    @Test
    void workBeforeTheFirstRevisionHasNoGoverningProcedure() {
        ProcedureRegister register = new ProcedureRegister(List.of(revision(0)));
        assertThatThrownBy(() -> register.effectiveOn("RT-001", LocalDate.of(2022, 6, 1)))
                .isInstanceOfSatisfying(
                        ConfigurationError.class,
                        caught -> assertThat(caught.context().get("day")).isEqualTo("2022-06-01"));
    }

    @Test
    void anUnapprovedRevisionIsNeverReturnedAsEffective() {
        Revision draft = new Revision("RT-001", 2, "draft", Code.ASME_V, LocalDate.of(2024, 1, 1));
        ProcedureRegister register = new ProcedureRegister(List.of(revision(0), draft));
        assertThat(register.effectiveOn("RT-001", LocalDate.of(2024, 6, 1)).revision())
                .isZero();
    }

    @Test
    void anOldRevisionStaysResolvableForever() {
        Revision old = revision(0);
        Revision fresh = revision(1, SECOND_EFFECTIVE, SECOND_APPROVED);
        ProcedureRegister register = new ProcedureRegister(List.of(old, fresh));
        assertThat(register.effectiveOn("RT-001", LocalDate.of(2023, 6, 1))).isEqualTo(old);
        assertThat(register.isSuperseded(old, LocalDate.of(2024, 6, 1))).isTrue();
        assertThat(register.isSuperseded(old, LocalDate.of(2023, 6, 1))).isFalse();
    }

    @Test
    void theHistoryOfAProcedureComesBackInOrder() {
        ProcedureRegister register =
                new ProcedureRegister(List.of(revision(1, SECOND_EFFECTIVE, SECOND_APPROVED), revision(0)));
        assertThat(register.history("RT-001")).extracting(Revision::revision).containsExactly(0, 1);
        assertThat(register.history("RT-999")).isEmpty();
    }

    @Test
    void theSameRevisionTwiceIsRefused() {
        assertThatThrownBy(() -> new ProcedureRegister(List.of(revision(0), revision(0))))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void addingARevisionKeepsTheRegisterSound() {
        ProcedureRegister register = new ProcedureRegister(List.of(revision(0)));
        register.add(revision(1, SECOND_EFFECTIVE, SECOND_APPROVED));
        assertThat(register.current("RT-001", LocalDate.of(2024, 6, 1)).revision())
                .isEqualTo(1);
        assertThatThrownBy(() -> register.add(revision(1, SECOND_EFFECTIVE, SECOND_APPROVED)))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theRegisterListsTheProceduresItHolds() {
        Revision other =
                new Revision("RT-002", 0, "other", Code.API_1104, FIRST_EFFECTIVE, signer(), LocalDate.of(2022, 12, 1));
        ProcedureRegister register = new ProcedureRegister(List.of(revision(0), other));
        assertThat(register.numbers()).containsExactly("RT-001", "RT-002");
        assertThat(Code.API_1104).hasToString("API 1104");
    }
}
