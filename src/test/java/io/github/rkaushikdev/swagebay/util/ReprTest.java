package io.github.rkaushikdev.swagebay.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReprTest {

    @Test
    void textIsQuotedWithTheMarkThatNeedsNoEscape() {
        assertThat(Repr.of("it's")).isEqualTo("\"it's\"");
        assertThat(Repr.of("a\"b")).isEqualTo("'a\"b'");
        assertThat(Repr.of("both'\"")).isEqualTo("'both\\'\"'");
        assertThat(Repr.of("tab\tx")).isEqualTo("'tab\\tx'");
        assertThat(Repr.of("\u0001")).isEqualTo("'\\x01'");
        assertThat(Repr.of('c')).isEqualTo("'c'");
    }

    @Test
    void numbersTruthAndAbsenceHaveTheirOwnForms() {
        assertThat(Repr.of(null)).isEqualTo("None");
        assertThat(Repr.of(Optional.empty())).isEqualTo("None");
        assertThat(Repr.of(Optional.of(3))).isEqualTo("3");
        assertThat(Repr.of(true)).isEqualTo("True");
        assertThat(Repr.of(false)).isEqualTo("False");
        assertThat(Repr.of(42L)).isEqualTo("42");
        assertThat(Repr.of(new BigDecimal("2.50"))).isEqualTo("Decimal('2.50')");
        assertThat(Repr.of(1.5)).isEqualTo("1.5");
        assertThat(Repr.of(1.5f)).isEqualTo("1.5");
    }

    @Test
    void collectionsAndDomainValuesAreQuotedInside() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", "x");
        assertThat(Repr.of(map)).isEqualTo("{'a': 1, 'b': 'x'}");
        assertThat(Repr.of(List.of("x", 2))).isEqualTo("['x', 2]");
        assertThat(Repr.of(List.of())).isEqualTo("[]");
        Repr.Quoted quoted = () -> "Length(9525)";
        assertThat(Repr.of(quoted)).isEqualTo("Length(9525)");
        assertThat(Repr.of(Thread.State.NEW)).isEqualTo("'NEW'");
        assertThat(Repr.of(new StringBuilder("raw"))).isEqualTo("raw");
    }
}
