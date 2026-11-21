package io.github.rkaushikdev.swagebay.nonconformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NonconformanceTest {

    private static final LocalDate RAISED = LocalDate.of(2025, 6, 26);
    private static final LocalDate LATER = RAISED.plusDays(4);

    private static Report aReport(String reference, String job, String item, String by) {
        return new Report(
                reference, job, item, "API 1104 section 9.3.8", "elongated slag 32 mm in a 300 mm length", RAISED, by);
    }

    private static Report aReport(String reference, String job) {
        return aReport(reference, job, "GW-104", "0142");
    }

    private static Report aReport(String reference) {
        return aReport(reference, "24-0001");
    }

    private static Report aReport() {
        return aReport("NCR-1");
    }

    private static Report closedReworked() {
        Report report = aReport();
        report.decide(Disposition.REWORK);
        report.close(LATER, "0303");
        return report;
    }

    @Test
    void aReportOpensUndecidedAndOpen() {
        Report report = aReport();
        assertThat(report.isOpen()).isTrue();
        assertThat(report.disposition()).isEmpty();
        assertThat(report.describe()).contains("undecided");
    }

    @Test
    void everyFieldOnTheFormHasToBeFilledIn() {
        List<String> fields = List.of("reference", "job", "item", "requirement", "account", "raised_by");
        for (String fieldName : fields) {
            Map<String, String> blanks = new LinkedHashMap<>();
            blanks.put("reference", "NCR-1");
            blanks.put("job", "24-0001");
            blanks.put("item", "GW-104");
            blanks.put("requirement", "API 1104");
            blanks.put("account", "slag");
            blanks.put("raised_by", "0142");
            blanks.put(fieldName, "   ");
            assertThatThrownBy(() -> new Report(
                            blanks.get("reference"),
                            blanks.get("job"),
                            blanks.get("item"),
                            blanks.get("requirement"),
                            blanks.get("account"),
                            RAISED,
                            blanks.get("raised_by")))
                    .isInstanceOfSatisfying(
                            ValidationError.class,
                            caught -> assertThat(caught.context().get("field")).isEqualTo(fieldName));
        }
    }

    @Test
    void reworkNeedsNothingBeyondTheDecision() {
        Report report = aReport();
        report.decide(Disposition.REWORK);
        assertThat(report.disposition()).hasValue(Disposition.REWORK);
    }

    @Test
    void useAsIsWithoutAConcessionIsRefused() {
        assertThatThrownBy(() -> aReport().decide(Disposition.USE_AS_IS))
                .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                caught.context().get("disposition"))
                        .isEqualTo("use as is"));
    }

    @Test
    void useAsIsWithAConcessionIsAccepted() {
        Report report = aReport();
        report.decide(Disposition.USE_AS_IS, "CON-88", "");
        assertThat(report.concession()).isEqualTo("CON-88");
    }

    @Test
    void aRepairAlsoNeedsTheClientToAgree() {
        assertThatThrownBy(() -> aReport().decide(Disposition.REPAIR)).isInstanceOf(ConflictError.class);
        Report report = aReport();
        report.decide(Disposition.REPAIR, "CON-89", "");
        assertThat(report.disposition()).hasValue(Disposition.REPAIR);
    }

    @Test
    void aRejectedItemNeedsSomewhereToHaveGone() {
        assertThatThrownBy(() -> aReport().decide(Disposition.REJECT)).isInstanceOf(ConflictError.class);
        Report report = aReport();
        report.decide(Disposition.REJECT, "", "SCRAP-14");
        assertThat(report.removal()).isEqualTo("SCRAP-14");
    }

    @Test
    void onlyTwoDispositionsNeedAConcession() {
        assertThat(Arrays.stream(Disposition.values()).filter(Disposition::needsConcession))
                .containsExactlyInAnyOrder(Disposition.USE_AS_IS, Disposition.REPAIR);
    }

    @Test
    void aReportWithNothingDecidedCannotBeClosed() {
        assertThatThrownBy(() -> aReport().close(LATER, "0303")).isInstanceOf(ConflictError.class);
    }

    @Test
    void thePersonWhoRaisedItCannotCloseIt() {
        Report report = aReport("NCR-1", "24-0001", "GW-104", "0142");
        report.decide(Disposition.REWORK);
        assertThatThrownBy(() -> report.close(LATER, "0142"))
                .isInstanceOfSatisfying(
                        ConflictError.class,
                        caught -> assertThat(caught.context().get("badge")).isEqualTo("0142"));
    }

    @Test
    void anUnsignedCloseIsRefused() {
        Report report = aReport();
        report.decide(Disposition.REWORK);
        assertThatThrownBy(() -> report.close(LATER, "   ")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aCloseDatedBeforeTheRaiseIsRefused() {
        Report report = aReport();
        report.decide(Disposition.REWORK);
        assertThatThrownBy(() -> report.close(RAISED.minusDays(1), "0303")).isInstanceOf(ValidationError.class);
    }

    @Test
    void aClosedReportReadsAsClosed() {
        Report report = closedReworked();
        assertThat(report.isOpen()).isFalse();
        assertThat(report.describe()).contains("closed 2025-06-30 by 0303");
    }

    @Test
    void aClosedReportCannotBeClosedAgainOrRedecided() {
        Report report = closedReworked();
        assertThatThrownBy(() -> report.close(LATER, "0303")).isInstanceOf(ConflictError.class);
        assertThatThrownBy(() -> report.decide(Disposition.REJECT, "", "SCRAP-1"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theClockStopsWhenItCloses() {
        Report report = closedReworked();
        LocalDate muchLater = RAISED.plusDays(90);
        assertThat(report.daysOpen(muchLater)).isEqualTo(4);
        assertThat(report.isOverdue(muchLater)).isFalse();
    }

    @Test
    void aReportLeftOpenAMonthEscalates() {
        Report report = aReport();
        LocalDate justInside = RAISED.plusDays(Report.ESCALATE_AFTER_DAYS);
        assertThat(report.isOverdue(justInside)).isFalse();
        assertThat(report.isOverdue(justInside.plusDays(1))).isTrue();
        assertThat(report.escalatesOn()).isEqualTo(justInside.plusDays(1));
    }

    @Test
    void theRegisterRefusesTheSameReferenceTwice() {
        Register register = new Register();
        register.raiseReport(aReport());
        assertThatThrownBy(() -> register.raiseReport(aReport())).isInstanceOf(ConflictError.class);
        assertThat(register.size()).isEqualTo(1);
    }

    @Test
    void aReferenceNobodyRaisedIsRefused() {
        assertThatThrownBy(() -> new Register().get("NCR-9")).isInstanceOf(ConflictError.class);
    }

    @Test
    void oneJobsReportsReadInTheOrderTheyWereRaised() {
        Register register = new Register();
        register.raiseReport(aReport("NCR-2"));
        register.raiseReport(aReport("NCR-1"));
        register.raiseReport(aReport("NCR-3", "24-0002"));
        assertThat(register.forJob("24-0001")).extracting(Report::reference).containsExactly("NCR-1", "NCR-2");
    }

    @Test
    void theOpenListDropsAReportWhenItCloses() {
        Register register = new Register();
        register.raiseReport(aReport());
        assertThat(register.openReports()).hasSize(1);
        Report report = register.get("NCR-1");
        report.decide(Disposition.REWORK);
        report.close(LATER, "0303");
        assertThat(register.openReports()).isEmpty();
    }

    @Test
    void theOverdueListIsTheOpenOnesPastTheMonth() {
        Register register = new Register();
        register.raiseReport(aReport());
        assertThat(register.overdueOn(RAISED.plusDays(10))).isEmpty();
        assertThat(register.overdueOn(RAISED.plusDays(40))).hasSize(1);
    }

    @Test
    void theUndecidedListIsWhatTheMondayMeetingIsAbout() {
        Register register = new Register();
        register.raiseReport(aReport("NCR-1"));
        register.raiseReport(aReport("NCR-2"));
        register.get("NCR-2").decide(Disposition.REWORK);
        assertThat(register.awaitingADecision()).extracting(Report::reference).containsExactly("NCR-1");
    }

    @Test
    void theDispositionsAreCountedForTheQuarterlyReview() {
        Register register = new Register();
        register.raiseReport(aReport("NCR-1"));
        register.get("NCR-1").decide(Disposition.REWORK);
        register.raiseReport(aReport("NCR-2"));
        register.get("NCR-2").decide(Disposition.REWORK);
        register.raiseReport(aReport("NCR-3"));
        register.get("NCR-3").decide(Disposition.USE_AS_IS, "CON-1", "");
        assertThat(register.byDisposition()).containsOnly(entry("rework", 2), entry("use as is", 1));
    }

    @Test
    void everyConcessionTheClientSignedCanBeListed() {
        Register register = new Register();
        register.raiseReport(aReport("NCR-1"));
        register.raiseReport(aReport("NCR-2"));
        register.get("NCR-1").decide(Disposition.USE_AS_IS, "CON-1", "");
        register.get("NCR-2").decide(Disposition.REPAIR, "CON-2", "");
        assertThat(register.concessionsOn("24-0001")).containsExactly("CON-1", "CON-2");
    }

    @Test
    void anOpenReportStopsTheJobBeingSignedOff() {
        Register register = new Register();
        register.raiseReport(aReport());
        assertThat(register.blocksClosing("24-0001")).containsExactly("NCR-1 is still open");
    }

    @Test
    void aJobWithEverythingClosedIsNotBlocked() {
        Register register = new Register();
        register.raiseReport(aReport());
        Report report = register.get("NCR-1");
        report.decide(Disposition.REWORK);
        report.close(LATER, "0303");
        assertThat(register.blocksClosing("24-0001")).isEmpty();
    }

    @Test
    void anotherJobsReportsDoNotBlockThisOne() {
        Register register = new Register();
        register.raiseReport(aReport("NCR-1", "24-0002"));
        assertThat(register.blocksClosing("24-0001")).isEmpty();
    }
}
