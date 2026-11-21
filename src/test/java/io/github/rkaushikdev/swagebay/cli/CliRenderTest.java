package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Render.bullets;
import static io.github.rkaushikdev.swagebay.cli.Render.heading;
import static io.github.rkaushikdev.swagebay.cli.Render.pairs;
import static io.github.rkaushikdev.swagebay.cli.Render.plain;
import static io.github.rkaushikdev.swagebay.cli.Render.table;
import static io.github.rkaushikdev.swagebay.cli.Render.widths;
import static io.github.rkaushikdev.swagebay.cli.Render.yesNo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliRenderTest {

    @Test
    void widthsComeFromTheLongestCell() {
        assertThat(widths(List.of(List.of("a", "bbb"), List.of("cc", "d")))).containsExactly(2, 3);
        assertThat(widths(List.of())).isEmpty();
    }

    @Test
    void aRaggedTableIsRefused() {
        assertThatThrownBy(() -> widths(List.of(List.of("a", "b"), List.of("c"))))
                .isInstanceOfSatisfying(ValidationError.class, refused -> assertThat(refused.context())
                        .containsEntry("expected", 2));
    }

    @Test
    void aTableLinesItsColumnsUp() {
        String rendered = table(List.of("weld", "exposures"), List.of(List.of("GW-104", "8"), List.of("GW-1104", "3")));
        List<String> lines = rendered.lines().toList();
        assertThat(lines.get(0)).isEqualTo("weld     exposures");
        assertThat(lines.get(1)).isEqualTo("-------  ---------");
        assertThat(lines.get(2)).isEqualTo("GW-104   8");
    }

    @Test
    void numericColumnsAreRightAligned() {
        String rendered =
                table(List.of("weld", "n"), List.of(List.of("GW-104", "8"), List.of("GW-1104", "12")), List.of(1));
        List<String> lines = rendered.lines().toList();
        assertThat(lines.get(2)).endsWith(" 8");
        assertThat(lines.get(3)).endsWith("12");
    }

    @Test
    void aTableWithNoRowsStillPrintsItsHeadings() {
        String rendered = table(List.of("weld", "n"), List.of());
        assertThat(rendered.lines().toList().getFirst()).isEqualTo("weld  n");
        assertThat(rendered.lines()).hasSize(2);
    }

    @Test
    void aTableWithNoHeadingsIsRefused() {
        assertThatThrownBy(() -> table(List.of(), List.of(List.of("a")))).isInstanceOf(ValidationError.class);
    }

    @Test
    void nothingInTheOutputNeedsATerminalToRead() {
        String rendered = table(List.of("a"), List.of(List.of("b")));
        assertThat(rendered).doesNotContain("\u001b");
        assertThat(rendered.chars()).allMatch(character -> character < 128);
    }

    @Test
    void trailingPaddingIsTrimmedSoLinesDoNotCarrySpaces() {
        String rendered = table(List.of("weld", "note"), List.of(List.of("GW-104", "")));
        assertThat(rendered.lines().toList().get(2)).doesNotEndWith(" ");
    }

    @Test
    void aPairBlockLinesTheLabelsUp() {
        String rendered = pairs(List.of(Map.entry("shot", "panoramic"), Map.entry("exposures", "1")));
        assertThat(rendered.lines().toList().get(0)).isEqualTo("shot       panoramic");
        assertThat(rendered.lines().toList().get(1)).isEqualTo("exposures  1");
    }

    @Test
    void anEmptyPairBlockIsEmpty() {
        assertThat(pairs(List.of())).isEmpty();
    }

    @Test
    void bulletsTakeAMarker() {
        assertThat(bullets(List.of("one", "two"))).isEqualTo("- one\n- two");
        assertThat(bullets(List.of("one"), "*")).isEqualTo("* one");
        assertThat(bullets(List.of())).isEmpty();
    }

    @Test
    void aHeadingIsUnderlinedToItsOwnWidth() {
        assertThat(heading("technique sheet")).isEqualTo("technique sheet\n===============");
    }

    @Test
    void yesAndNoAreWordsRatherThanSymbols() {
        assertThat(yesNo(true)).isEqualTo("yes");
        assertThat(yesNo(false)).isEqualTo("no");
    }

    @Test
    void aRoundNumberPrintsWithoutAnExponent() {
        assertThat(plain(BigDecimal.valueOf(100))).isEqualTo("100");
        assertThat(plain(new BigDecimal("2E+2"))).isEqualTo("200");
        assertThat(plain(BigDecimal.valueOf(1000))).isEqualTo("1000");
    }

    @Test
    void trailingZerosAreStillTrimmed() {
        assertThat(plain(new BigDecimal("2.500"))).isEqualTo("2.5");
        assertThat(plain(new BigDecimal("0.0240"))).isEqualTo("0.024");
    }

    @Test
    void aSmallNumberKeepsItsPlaces() {
        assertThat(plain(new BigDecimal("0.06"))).isEqualTo("0.06");
        assertThat(plain(new BigDecimal("1.6"))).isEqualTo("1.6");
    }
}
