package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.Enhancement.check;
import static io.github.rkaushikdev.swagebay.imaging.Enhancement.faults;
import static io.github.rkaushikdev.swagebay.imaging.Enhancement.isAdmissible;
import static io.github.rkaushikdev.swagebay.imaging.Enhancement.viewingOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.QualificationError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EnhancementTest {

    private static Applied applied(Operation operation, String setting) {
        return new Applied(operation, setting, "0142");
    }

    private static Applied applied(Operation operation) {
        return applied(operation, "");
    }

    private static History history(Operation... operations) {
        History made = new History();
        for (Operation operation : operations) {
            made = made.then(applied(operation));
        }
        return made;
    }

    private static Set<Operation> viewingOnlyAnd(Operation extra) {
        Set<Operation> qualified = EnumSet.copyOf(viewingOnly());
        qualified.add(extra);
        return qualified;
    }

    @Test
    void windowingLeavesTheStoredDataAlone() {
        assertThat(Operation.WINDOW.isReversible()).isTrue();
        assertThat(Operation.CONTRAST_STRETCH.isReversible()).isTrue();
        assertThat(Operation.ROTATE_90.isReversible()).isTrue();
        assertThat(Operation.MIRROR.isReversible()).isTrue();
    }

    @Test
    void sharpeningAndSmoothingThrowInformationAway() {
        assertThat(Operation.SHARPEN.isReversible()).isFalse();
        assertThat(Operation.SMOOTH.isReversible()).isFalse();
        assertThat(Operation.NOISE_REDUCTION.isReversible()).isFalse();
    }

    @Test
    void theIrreversibleOnesAreTheOnesThatNeedQualifying() {
        for (Operation operation : Operation.values()) {
            if (operation.needsQualification()) {
                assertThat(operation.isReversible()).isFalse();
            }
        }
    }

    @Test
    void twoOperationsAreNeverAllowedAtAll() {
        Set<Operation> forbidden =
                Arrays.stream(Operation.values()).filter(Operation::isForbidden).collect(Collectors.toSet());
        assertThat(forbidden).containsExactlyInAnyOrder(Operation.LOCAL_EDIT, Operation.LOSSY_COMPRESSION);
    }

    @Test
    void anUnprocessedImageSaysSo() {
        assertThat(new History().describe()).isEqualTo("unprocessed");
        assertThat(new History().isReversible()).isTrue();
    }

    @Test
    void theHistoryKeepsTheOrderThingsWereDoneIn() {
        History made = history(Operation.WINDOW, Operation.CONTRAST_STRETCH);
        assertThat(made.operations())
                .extracting(Applied::operation)
                .containsExactly(Operation.WINDOW, Operation.CONTRAST_STRETCH);
        assertThat(made.describe()).contains(" then ");
    }

    @Test
    void aHistoryIsImmutableSoARecordCannotBeEdited() {
        History first = history(Operation.WINDOW);
        History second = first.then(applied(Operation.MIRROR));
        assertThat(first.operations()).hasSize(1);
        assertThat(second.operations()).hasSize(2);
    }

    @Test
    void viewingOperationsLeaveTheImageReversible() {
        assertThat(history(Operation.WINDOW, Operation.MIRROR).isReversible()).isTrue();
    }

    @Test
    void oneSharpenMakesTheWholeHistoryIrreversible() {
        History made = history(Operation.WINDOW, Operation.SHARPEN);
        assertThat(made.isReversible()).isFalse();
        assertThat(made.irreversible()).extracting(Applied::operation).containsExactly(Operation.SHARPEN);
    }

    @Test
    void aViewingOnlyHistoryPasses() {
        assertThat(isAdmissible(history(Operation.WINDOW, Operation.MIRROR), viewingOnly()))
                .isTrue();
    }

    @Test
    void anUnqualifiedSharpenIsAFault() {
        List<String> found = faults(history(Operation.SHARPEN), viewingOnly());
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("not part of the qualified technique");
    }

    @Test
    void aQualifiedSharpenIsAllowed() {
        Set<Operation> qualified = viewingOnlyAnd(Operation.SHARPEN);
        assertThat(isAdmissible(history(Operation.WINDOW, Operation.SHARPEN), qualified))
                .isTrue();
    }

    @Test
    void qualifyingASharpenDoesNotQualifyASmooth() {
        Set<Operation> qualified = viewingOnlyAnd(Operation.SHARPEN);
        assertThat(isAdmissible(history(Operation.SMOOTH), qualified)).isFalse();
    }

    @Test
    void aLocalEditIsNeverAllowedHoweverItWasQualified() {
        Set<Operation> qualified = EnumSet.allOf(Operation.class);
        List<String> found = faults(history(Operation.LOCAL_EDIT), qualified);
        assertThat(found.get(0)).contains("not allowed on a graded image");
    }

    @Test
    void compressingTwiceIsReportedOnItsOwn() {
        List<String> found = faults(history(Operation.LOSSY_COMPRESSION, Operation.LOSSY_COMPRESSION), viewingOnly());
        assertThat(found).anyMatch(f -> f.contains("compressed more than once"));
    }

    @Test
    void everyFaultIsReportedNotJustTheFirst() {
        List<String> found = faults(history(Operation.SHARPEN, Operation.LOCAL_EDIT), viewingOnly());
        assertThat(found).hasSize(2);
    }

    @Test
    void theCheckRefusesADisqualifiedImageAndSaysWhy() {
        assertThatThrownBy(() -> check(history(Operation.LOCAL_EDIT), viewingOnly()))
                .isInstanceOfSatisfying(QualificationError.class, caught -> assertThat(
                                (String) caught.context().get("reasons"))
                        .contains("not allowed"));
    }

    @Test
    void theCheckPassesASoundHistorySilently() {
        assertThatCode(() -> check(history(Operation.WINDOW), viewingOnly())).doesNotThrowAnyException();
    }

    @Test
    void anOperationIsAppliedBySomebody() {
        assertThatThrownBy(() -> new Applied(Operation.WINDOW, "", "  ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void anOperationPrintsItsSettingWhenItHasOne() {
        assertThat(applied(Operation.WINDOW, "1200/400").describe()).isEqualTo("window and level 1200/400 by 0142");
        assertThat(applied(Operation.MIRROR).describe()).isEqualTo("mirror by 0142");
    }

    @Test
    void theDefaultQualifiedSetIsTheReversibleOperations() {
        assertThat(viewingOnly())
                .isEqualTo(Arrays.stream(Operation.values())
                        .filter(Operation::isReversible)
                        .collect(Collectors.toSet()));
        assertThat(viewingOnly()).doesNotContain(Operation.SHARPEN);
    }
}
