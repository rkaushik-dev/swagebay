package io.github.rkaushikdev.swagebay.reporting;

import static io.github.rkaushikdev.swagebay.reporting.Submittal.DEFAULT_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubmittalTest {

    private static final LocalDate PREPARED = LocalDate.of(2025, 3, 10);

    private static ReportPackage aPackage(String... faults) {
        return new ReportPackage(
                new JobNumber(25, 1),
                "Maritime Pipelines",
                "Come By Chance",
                Code.API_1104,
                "RT-001",
                LocalDate.of(2025, 3, 1),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(faults));
    }

    private static Submittal aSubmittal(Set<Enclosure> enclosures, int revision) {
        return new Submittal("25-0001", "Maritime Pipelines", PREPARED, "0142", enclosures, revision);
    }

    private static Submittal aSubmittal(Set<Enclosure> enclosures) {
        return aSubmittal(enclosures, 0);
    }

    private static Submittal aSubmittal() {
        return aSubmittal(EnumSet.copyOf(DEFAULT_REQUIRED));
    }

    /** Everything a client asks for by default, less {@code left}. */
    private static Set<Enclosure> requiredWithout(Enclosure left) {
        Set<Enclosure> enclosed = EnumSet.copyOf(DEFAULT_REQUIRED);
        enclosed.remove(left);
        return enclosed;
    }

    @Test
    void aCompleteParcelIsReady() {
        Submittal parcel = aSubmittal();
        assertThat(parcel.isReady()).isTrue();
        assertThat(parcel.missing()).isEmpty();
    }

    @Test
    void aMissingEnclosureIsNamed() {
        Submittal parcel = aSubmittal(requiredWithout(Enclosure.PROCEDURE));
        assertThat(parcel.isReady()).isFalse();
        assertThat(parcel.missing()).containsExactly(Enclosure.PROCEDURE);
    }

    @Test
    void everyMissingEnclosureIsNamedNotJustTheFirst() {
        Submittal parcel = aSubmittal(Set.of(Enclosure.REPORT));
        assertThat(parcel.missing()).hasSize(4);
    }

    @Test
    void somethingEnclosedThatWasNotAskedForIsNotAFault() {
        Set<Enclosure> enclosed = EnumSet.copyOf(DEFAULT_REQUIRED);
        enclosed.add(Enclosure.WELD_MAP);
        Submittal parcel = aSubmittal(enclosed);
        assertThat(parcel.isReady()).isTrue();
        assertThat(parcel.extra()).containsExactly(Enclosure.WELD_MAP);
    }

    @Test
    void aClientThatAsksForLessGetsLess() {
        List<Enclosure> lighter = List.of(Enclosure.REPORT, Enclosure.RADIOGRAPHS);
        Submittal parcel = aSubmittal(Set.of(Enclosure.REPORT, Enclosure.RADIOGRAPHS));
        assertThat(parcel.isReady(lighter)).isTrue();
        assertThat(parcel.isReady()).isFalse();
    }

    @Test
    void aClientThatAsksForMoreIsCheckedAgainstThat() {
        List<Enclosure> heavier = new ArrayList<>(DEFAULT_REQUIRED);
        heavier.add(Enclosure.WELD_MAP);
        heavier.add(Enclosure.REPAIR_RECORD);
        Submittal parcel = aSubmittal();
        assertThat(parcel.isReady()).isTrue();
        assertThat(parcel.missing(heavier)).containsExactly(Enclosure.WELD_MAP, Enclosure.REPAIR_RECORD);
    }

    @Test
    void aSubmittalIsSignedAndHasASaneRevision() {
        assertThatThrownBy(() -> new Submittal("25-0001", "c", PREPARED, "  ", Set.of()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new Submittal("25-0001", "c", PREPARED, "0142", Set.of(), -1))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aSoundPackageInACompleteParcelIsReadyToGo() {
        Readiness readiness = Submittals.check(aPackage(), aSubmittal());
        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.describe()).isEqualTo("ready to go");
    }

    @Test
    void aPackageFaultStopsACompleteParcel() {
        Readiness readiness = Submittals.check(aPackage("GW-104 has no radiographs"), aSubmittal());
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.describe()).contains("GW-104 has no radiographs");
    }

    @Test
    void aMissingEnclosureStopsASoundPackage() {
        Readiness readiness = Submittals.check(aPackage(), aSubmittal(requiredWithout(Enclosure.CALIBRATIONS)));
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.describe()).contains("no equipment calibration certificates enclosed");
    }

    @Test
    void bothKindsOfFaultAreReportedTogether() {
        Readiness readiness =
                Submittals.check(aPackage("R-001 has not been read"), aSubmittal(Set.of(Enclosure.REPORT)));
        assertThat(readiness.packageFaults()).hasSize(1);
        assertThat(readiness.missingEnclosures()).hasSize(4);
    }

    @Test
    void aResubmittalIsANewRevisionRatherThanAnEdit() {
        Submittal first = aSubmittal();
        Submittal second = Submittals.revise(first, EnumSet.copyOf(DEFAULT_REQUIRED), LocalDate.of(2025, 4, 1));
        assertThat(first.revision()).isZero();
        assertThat(second.revision()).isEqualTo(1);
        assertThat(second.preparedOn()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(second.job()).isEqualTo(first.job());
    }

    @Test
    void revisionsKeepStacking() {
        Submittal parcel = aSubmittal();
        for (int expected = 1; expected <= 3; expected++) {
            parcel = Submittals.revise(parcel, parcel.enclosures(), PREPARED);
            assertThat(parcel.revision()).isEqualTo(expected);
        }
    }

    @Test
    void theCoveringNoteListsWhatIsInTheParcel() {
        String note = aSubmittal().coveringNote();
        assertThat(note).contains("Job 25-0001 for Maritime Pipelines");
        assertThat(note).contains("Prepared 2025-03-10 by 0142");
        assertThat(note).contains("the radiographs");
        assertThat(note).doesNotContain("Submittal revision");
    }

    @Test
    void aRevisedParcelSaysSoOnTheNote() {
        String note = aSubmittal(EnumSet.copyOf(DEFAULT_REQUIRED), 2).coveringNote();
        assertThat(note).contains("Submittal revision 2");
    }
}
