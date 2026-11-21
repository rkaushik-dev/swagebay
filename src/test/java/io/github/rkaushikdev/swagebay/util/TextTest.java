package io.github.rkaushikdev.swagebay.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void unicodeSpaceIsPaddingToo() {
        assertThat(Text.strip("  24-0001　\n")).isEqualTo("24-0001");
        assertThat(Text.strip("\u0085x ")).isEqualTo("x");
        assertThat(Text.strip("plain")).isEqualTo("plain");
        assertThat(Text.isBlank("  \t")).isTrue();
        assertThat(Text.isBlank(" a ")).isFalse();
    }

    @Test
    void wordsSplitOnAnyRunOfSpace() {
        assertThat(Text.words("  one two \t three ")).containsExactly("one", "two", "three");
        assertThat(Text.words("   ")).isEmpty();
    }
}
