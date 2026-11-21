package io.github.rkaushikdev.swagebay.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Register;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NonconformanceRepositoryTest {

    private static final LocalDate RAISED = LocalDate.of(2025, 6, 30);
    private static final LocalDate LATER = RAISED.plusDays(5);

    private static Report aReport(String reference, String job) {
        return new Report(
                reference,
                job,
                "GW-104",
                "API 1104 section 9.3.8",
                "elongated slag 32 mm in a 300 mm length",
                RAISED,
                "0142");
    }

    private static Report aReport(String reference) {
        return aReport(reference, "24-0001");
    }

    private static Report aReport() {
        return aReport("NCR-1");
    }

    @Test
    void aRaisedReportComesBackUndecided() {
        Report found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            found = work.nonconformances().report("NCR-1");
        }
        assertThat(found.isOpen()).isTrue();
        assertThat(found.disposition()).isEmpty();
        assertThat(found.raisedBy()).isEqualTo("0142");
    }

    @Test
    void theSameReferenceTwiceIsAConflict() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            assertThatThrownBy(() -> work.nonconformances().raiseReport(aReport()))
                    .isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void aReferenceNobodyRaisedIsANotFound() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            assertThat(work.nonconformances().find("NCR-9")).isEmpty();
            assertThatThrownBy(() -> work.nonconformances().get("NCR-9")).isInstanceOf(NotFoundError.class);
        }
    }

    @Test
    void aDecisionIsWrittenBack() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REWORK);
            assertThat(work.nonconformances().report("NCR-1").disposition()).contains(Disposition.REWORK);
        }
    }

    @Test
    void useAsIsWithoutAConcessionIsStillRefusedThroughTheRepository() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            assertThatThrownBy(() -> work.nonconformances().decide("NCR-1", Disposition.USE_AS_IS))
                    .isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void aConcessionSurvivesTheRoundTrip() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.USE_AS_IS, "CON-88", "");
            assertThat(work.nonconformances().report("NCR-1").concession()).isEqualTo("CON-88");
        }
    }

    @Test
    void aRemovalReferenceSurvivesTheRoundTrip() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REJECT, "", "SCRAP-14");
            assertThat(work.nonconformances().report("NCR-1").removal()).isEqualTo("SCRAP-14");
        }
    }

    @Test
    void closingWritesTheDateAndTheBadge() {
        Report found;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REWORK);
            work.nonconformances().close("NCR-1", LATER, "0303");
            found = work.nonconformances().report("NCR-1");
        }
        assertThat(found.isOpen()).isFalse();
        assertThat(found.closedBy()).isEqualTo("0303");
        assertThat(found.closedOn()).contains(LATER);
    }

    @Test
    void theAuthorStillCannotCloseItThroughTheRepository() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REWORK);
            assertThatThrownBy(() -> work.nonconformances().close("NCR-1", LATER, "0142"))
                    .isInstanceOf(ConflictError.class);
        }
    }

    @Test
    void oneJobsReportsReadInTheOrderTheyWereRaised() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport("NCR-2"));
            work.nonconformances().raiseReport(aReport("NCR-1"));
            work.nonconformances().raiseReport(aReport("NCR-3", "24-0002"));
            assertThat(work.nonconformances().forJob("24-0001"))
                    .extracting(Report::reference)
                    .containsExactly("NCR-1", "NCR-2");
        }
    }

    @Test
    void theOpenListSpansEveryJob() {
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport("NCR-1"));
            work.nonconformances().raiseReport(aReport("NCR-2", "24-0002"));
            work.nonconformances().decide("NCR-1", Disposition.REWORK);
            work.nonconformances().close("NCR-1", LATER, "0303");
            assertThat(work.nonconformances().openReports())
                    .extracting(Report::reference)
                    .containsExactly("NCR-2");
        }
    }

    @Test
    void theRegisterForAJobAnswersTheDomainQuestions() {
        Register register;
        try (UnitOfWork work = TestDatabase.fresh().unitOfWork()) {
            work.nonconformances().raiseReport(aReport());
            register = work.nonconformances().registerFor("24-0001");
        }
        assertThat(register.blocksClosing("24-0001")).containsExactly("NCR-1 is still open");
        assertThat(register.awaitingADecision()).hasSize(1);
    }

    @Test
    void aReportSurvivesTheTransactionThatRaisedIt() {
        Database made = TestDatabase.fresh();
        made.transaction(work -> {
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REPAIR, "CON-9", "");
        });
        Report found;
        try (UnitOfWork work = made.unitOfWork()) {
            found = work.nonconformances().report("NCR-1");
        }
        assertThat(found.disposition()).contains(Disposition.REPAIR);
        assertThat(found.concession()).isEqualTo("CON-9");
    }
}
