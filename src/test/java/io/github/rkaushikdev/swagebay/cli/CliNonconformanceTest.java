package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConflictError;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliNonconformanceTest {

    private static final String RAISED = "2025-07-02";
    private static final String LATER = "2025-07-08";
    private static final String MUCH_LATER = "2025-09-01";

    @TempDir
    Path tmp;

    private Shell shop;

    @BeforeEach
    void anEmptyShop() {
        shop = Shell.against(Shell.fileUrl(tmp, "shop"));
        shop.run("init");
    }

    private Shell.Result raised(String reference, String job) {
        return shop.run(
                "ncr",
                "raise",
                reference,
                "--job",
                job,
                "--item",
                "GW-104",
                "--requirement",
                "API 1104 9.3.8",
                "--account",
                "elongated slag 32 mm",
                "--on",
                RAISED,
                "--by",
                "0142");
    }

    private Shell.Result raised() {
        return raised("NCR-1", "24-0001");
    }

    private static void assertConflict(Shell.Result result) {
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(result.failure()).isEqualTo(ConflictError.CODE);
    }

    private void decidedAndClosed() {
        shop.run("ncr", "decide", "NCR-1", "rework");
        shop.run("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303");
    }

    @Test
    void aReportIsRaisedUndecided() {
        Shell.Result result = raised();
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("undecided, open");
    }

    @Test
    void theSameReferenceTwiceIsAConflict() {
        raised();
        assertConflict(raised());
    }

    @Test
    void reworkNeedsNothingElse() {
        raised();
        assertThat(shop.run("ncr", "decide", "NCR-1", "rework").out()).contains("rework, open");
    }

    @Test
    void useAsIsWithoutAConcessionIsRefused() {
        raised();
        assertConflict(shop.run("ncr", "decide", "NCR-1", "use_as_is"));
    }

    @Test
    void useAsIsWithAConcessionGoesThrough() {
        raised();
        Shell.Result result = shop.run("ncr", "decide", "NCR-1", "use_as_is", "--concession", "CON-88");
        assertThat(result.out()).contains("use as is");
    }

    @Test
    void aDispositionNothingKnowsAboutIsRefused() {
        raised();
        assertThat(shop.run("ncr", "decide", "NCR-1", "have_a_think").code()).isNotZero();
    }

    @Test
    void closingNeedsADecisionFirst() {
        raised();
        assertConflict(shop.run("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303"));
    }

    @Test
    void theAuthorCannotCloseTheirOwn() {
        raised();
        shop.run("ncr", "decide", "NCR-1", "rework");
        assertConflict(shop.run("ncr", "close", "NCR-1", "--on", LATER, "--by", "0142"));
    }

    @Test
    void somebodyElseCan() {
        raised();
        shop.run("ncr", "decide", "NCR-1", "rework");
        Shell.Result result = shop.run("ncr", "close", "NCR-1", "--on", LATER, "--by", "0303");
        assertThat(result.out()).contains("closed 2025-07-08 by 0303");
    }

    @Test
    void nothingOpenSaysSo() {
        assertThat(shop.run("ncr", "open").out()).contains("nothing open");
    }

    @Test
    void theOpenListShowsTheReport() {
        raised();
        String output = shop.run("ncr", "open", "--on", LATER).out();
        assertThat(output).contains("NCR-1", "undecided").doesNotContain("overdue");
    }

    @Test
    void aReportLeftTwoMonthsShowsAsOverdue() {
        raised();
        assertThat(shop.run("ncr", "open", "--on", MUCH_LATER).out()).contains("overdue");
    }

    @Test
    void aClosedReportDropsOffTheOpenList() {
        raised();
        decidedAndClosed();
        assertThat(shop.run("ncr", "open").out()).contains("nothing open");
    }

    @Test
    void aJobWithNothingRaisedSaysSo() {
        assertThat(shop.run("ncr", "job", "24-0001").out()).contains("nothing raised against");
    }

    @Test
    void anOpenReportBlocksTheJobAndExitsFour() {
        raised();
        Shell.Result result = shop.run("ncr", "job", "24-0001", "--on", LATER);
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("stopping the sign off");
    }

    @Test
    void aJobWithEverythingClosedDoesNotBlock() {
        raised();
        decidedAndClosed();
        Shell.Result result = shop.run("ncr", "job", "24-0001", "--on", LATER);
        assertThat(result.code()).isZero();
        assertThat(result.out()).doesNotContain("stopping the sign off");
    }

    @Test
    void anotherJobsReportIsNotOnThisList() {
        raised("NCR-2", "24-0002");
        assertThat(shop.run("ncr", "job", "24-0001").out()).contains("nothing raised against");
    }
}
