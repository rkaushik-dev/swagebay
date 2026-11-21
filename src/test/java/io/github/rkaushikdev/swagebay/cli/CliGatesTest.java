package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Gates.EXIT_NOT_CLEAR;
import static io.github.rkaushikdev.swagebay.cli.Gates.asDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CliGatesTest {

    /** What a gate printed, and the status it answered. */
    private record Answer(int code, String output) {}

    private static Answer run(String title, List<Map.Entry<String, List<String>>> groups, String clearMessage) {
        StringWriter out = new StringWriter();
        int code = Gates.report(new PrintWriter(out, true), title, groups, clearMessage);
        return new Answer(code, out.toString());
    }

    private static Answer run(String title, List<Map.Entry<String, List<String>>> groups) {
        StringWriter out = new StringWriter();
        int code = Gates.report(new PrintWriter(out, true), title, groups);
        return new Answer(code, out.toString());
    }

    @Test
    void aDayOptionThatWasGivenIsThatDay() {
        assertThat(asDay(Optional.of(LocalDateTime.of(2025, 7, 8, 14, 30)))).isEqualTo(LocalDate.of(2025, 7, 8));
    }

    @Test
    void aDayOptionLeftOutIsTodayAndNotTheEpoch() {
        assertThat(asDay(Optional.empty())).isEqualTo(LocalDate.now());
    }

    @Test
    void nothingWrongPrintsTheClearMessageAndExitsZero() {
        Answer result = run("title", List.of(Map.entry("group", List.of())), "all good");
        assertThat(result.code()).isZero();
        assertThat(result.output().strip()).isEqualTo("all good");
    }

    @Test
    void nothingWrongAndNoMessagePrintsNothing() {
        Answer result = run("title", List.of(Map.entry("group", List.of())));
        assertThat(result.code()).isZero();
        assertThat(result.output()).isEmpty();
    }

    @Test
    void reasonsArePrintedUnderTheirHeadingsAndExitFour() {
        Answer result = run(
                "not clear",
                List.of(Map.entry("source", List.of("wipe test")), Map.entry("device", List.of("no quarterly"))));
        assertThat(result.code()).isEqualTo(EXIT_NOT_CLEAR);
        assertThat(result.output()).contains("not clear", "source", "wipe test", "no quarterly");
    }

    @Test
    void anEmptyGroupIsNotPrintedAsAHeading() {
        Answer result =
                run("not clear", List.of(Map.entry("source", List.of()), Map.entry("device", List.of("no quarterly"))));
        assertThat(result.output()).doesNotContain("source").contains("device");
    }

    @Test
    void aGroupWithNoLabelPrintsTheReasonsAlone() {
        Answer result = run("not clear", List.of(Map.entry("", List.of("NCR-1 is still open"))));
        assertThat(result.output()).contains("NCR-1 is still open");
        assertThat(result.code()).isEqualTo(EXIT_NOT_CLEAR);
    }

    @Test
    void theClearMessageIsNotPrintedWhenSomethingIsWrong() {
        Answer result = run("not clear", List.of(Map.entry("device", List.of("no quarterly"))), "all good");
        assertThat(result.output()).doesNotContain("all good");
    }
}
