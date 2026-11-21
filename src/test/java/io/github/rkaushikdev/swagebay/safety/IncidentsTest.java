package io.github.rkaushikdev.swagebay.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IncidentsTest {

    private static final LocalDateTime FOUND = LocalDateTime.of(2025, 2, 24, 23, 40);

    private static Incident incident(
            String reference, Occurrence occurrence, LocalDateTime found, Optional<LocalDateTime> notified) {
        return new Incident(
                reference,
                occurrence,
                found,
                "Come By Chance",
                "0142",
                "the source hung up in the guide tube",
                notified);
    }

    private static Incident incident(String reference, Occurrence occurrence) {
        return incident(reference, occurrence, FOUND, Optional.empty());
    }

    private static Incident incident(Occurrence occurrence) {
        return incident("INC-1", occurrence);
    }

    private static Incident incident() {
        return incident(Occurrence.OVEREXPOSURE);
    }

    @Test
    void theClockStartsWhenTheShopFoundOut() {
        Incident entry = incident(Occurrence.OVEREXPOSURE);
        assertThat(entry.dueAt()).isEqualTo(FOUND.plusHours(24));
    }

    @Test
    void aStuckSourceIsAnImmediateCall() {
        Incident entry = incident(Occurrence.SOURCE_DISCONNECTED);
        assertThat(entry.occurrence().isImmediate()).isTrue();
        assertThat(entry.dueAt()).isEqualTo(FOUND.plusHours(1));
    }

    @Test
    void aMeterFoundOutOfCalibrationIsAWrittenReport() {
        Incident entry = incident(Occurrence.METER_OUT_OF_CALIBRATION);
        assertThat(entry.occurrence().needsAWrittenReport()).isTrue();
        assertThat(entry.occurrence().isImmediate()).isFalse();
        assertThat(entry.dueAt()).isEqualTo(FOUND.plusDays(30));
    }

    @Test
    void theDeadlinesAreNotAllTheSame() {
        assertThat(Arrays.stream(Occurrence.values()).map(Occurrence::deadline).collect(Collectors.toSet()))
                .hasSizeGreaterThan(3);
    }

    @Test
    void anIncidentNotYetReportedCountsDown() {
        Incident entry = incident();
        assertThat(entry.timeLeftAt(FOUND.plusHours(1))).isEqualTo(Duration.ofHours(23));
        assertThat(entry.isNotified()).isFalse();
    }

    @Test
    void reportingItStopsTheClock() {
        Incident entry = incident();
        entry.recordNotification(FOUND.plusHours(2));
        assertThat(entry.isNotified()).isTrue();
        assertThat(entry.timeLeftAt(FOUND.plusDays(5))).isEqualTo(Duration.ZERO);
    }

    @Test
    void somethingReportedInsideTheDeadlineIsNotLate() {
        Incident entry = incident();
        entry.recordNotification(FOUND.plusHours(2));
        assertThat(entry.isLateAt(FOUND.plusDays(5))).isFalse();
    }

    @Test
    void somethingReportedAfterTheDeadlineStaysLateForever() {
        Incident entry = incident();
        entry.recordNotification(FOUND.plusHours(30));
        assertThat(entry.isLateAt(FOUND.plusDays(5))).isTrue();
    }

    @Test
    void somethingUnreportedBecomesLateWhenTheDeadlinePasses() {
        Incident entry = incident();
        assertThat(entry.isLateAt(FOUND.plusHours(23))).isFalse();
        assertThat(entry.isLateAt(FOUND.plusHours(25))).isTrue();
    }

    @Test
    void anIncidentCannotBeReportedBeforeItWasFound() {
        assertThatThrownBy(() -> incident("INC-1", Occurrence.OVEREXPOSURE, FOUND, Optional.of(FOUND.minusHours(1))))
                .isInstanceOf(ValidationError.class);
        Incident entry = incident();
        assertThatThrownBy(() -> entry.recordNotification(FOUND.minusHours(1))).isInstanceOf(ValidationError.class);
    }

    @Test
    void anIncidentIsOnlyReportedOnce() {
        Incident entry = incident();
        entry.recordNotification(FOUND.plusHours(1));
        assertThatThrownBy(() -> entry.recordNotification(FOUND.plusHours(2))).isInstanceOf(ConflictError.class);
    }

    @Test
    void anIncidentNeedsEveryFieldFilledIn() {
        for (String missing : List.of("reference", "site", "reported_by", "account")) {
            assertThatThrownBy(() -> new Incident(
                            missing.equals("reference") ? "" : "INC-1",
                            Occurrence.OVEREXPOSURE,
                            FOUND,
                            missing.equals("site") ? "" : "s",
                            missing.equals("reported_by") ? "" : "0142",
                            missing.equals("account") ? "" : "a"))
                    .isInstanceOfSatisfying(
                            ValidationError.class,
                            caught -> assertThat(caught.context().get("field")).isEqualTo(missing));
        }
    }

    @Test
    void aReferenceIsUsedOnce() {
        IncidentRegister register = new IncidentRegister();
        register.record(incident("INC-1", Occurrence.OVEREXPOSURE));
        assertThatThrownBy(() -> register.record(incident("INC-1", Occurrence.OVEREXPOSURE)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anIncidentThatIsNotThereIsRefused() {
        assertThatThrownBy(() -> new IncidentRegister().get("INC-9")).isInstanceOf(ConflictError.class);
    }

    @Test
    void theOutstandingListComesBackSoonestDeadlineFirst() {
        IncidentRegister register = new IncidentRegister();
        register.record(incident("INC-1", Occurrence.METER_OUT_OF_CALIBRATION));
        register.record(incident("INC-2", Occurrence.SOURCE_DISCONNECTED));
        register.record(incident("INC-3", Occurrence.OVEREXPOSURE));
        assertThat(register.outstandingAt(FOUND))
                .extracting(Incident::reference)
                .containsExactly("INC-2", "INC-3", "INC-1");
    }

    @Test
    void somethingReportedDropsOffTheOutstandingList() {
        IncidentRegister register = new IncidentRegister();
        register.record(incident("INC-1", Occurrence.OVEREXPOSURE));
        register.get("INC-1").recordNotification(FOUND.plusHours(1));
        assertThat(register.outstandingAt(FOUND.plusHours(2))).isEmpty();
    }

    @Test
    void theLateListNamesWhatIsPastItsDeadline() {
        IncidentRegister register = new IncidentRegister();
        register.record(incident("INC-1", Occurrence.SOURCE_DISCONNECTED));
        register.record(incident("INC-2", Occurrence.METER_OUT_OF_CALIBRATION));
        assertThat(register.lateAt(FOUND.plusHours(2))).containsExactly("INC-1");
    }

    @Test
    void theImmediateListIsWhatSomebodyIsOnThePhoneAbout() {
        IncidentRegister register = new IncidentRegister();
        register.record(incident("INC-1", Occurrence.SOURCE_DISCONNECTED));
        register.record(incident("INC-2", Occurrence.OVEREXPOSURE));
        assertThat(register.immediateAt(FOUND)).containsExactly("INC-1");
        register.get("INC-1").recordNotification(FOUND.plusMinutes(10));
        assertThat(register.immediateAt(FOUND)).isEmpty();
    }

    @Test
    void anIncidentPrintsWhatIsOwed() {
        assertThat(incident().describe()).contains("due 2025-02-25 23:40");
        Incident entry = incident();
        entry.recordNotification(FOUND);
        assertThat(entry.describe()).endsWith("reported");
    }
}
