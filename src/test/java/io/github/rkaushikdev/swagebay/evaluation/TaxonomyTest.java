package io.github.rkaushikdev.swagebay.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaxonomyTest {

    @Test
    void thePlanarKindsAreTheSharpEdgedOnes() {
        assertThat(Kind.C.isPlanar()).isTrue();
        assertThat(Kind.IP.isPlanar()).isTrue();
        assertThat(Kind.IF.isPlanar()).isTrue();
        assertThat(Kind.HB.isPlanar()).isTrue();
    }

    @Test
    void theVolumetricKindsAreTheRoundedOnes() {
        assertThat(Kind.P.isVolumetric()).isTrue();
        assertThat(Kind.ESI.isVolumetric()).isTrue();
        assertThat(Kind.ISI.isVolumetric()).isTrue();
        assertThat(Kind.P.isPlanar()).isFalse();
    }

    @Test
    void everyKindIsOneOrTheOtherAndNeverBoth() {
        for (Kind kind : Kind.values()) {
            assertThat(kind.isPlanar()).isNotEqualTo(kind.isVolumetric());
        }
        assertThat(Kind.PLANAR.size() + Kind.VOLUMETRIC.size()).isEqualTo(Kind.values().length);
    }

    @Test
    void onlyPorosityIsRounded() {
        Set<Kind> rounded = Arrays.stream(Kind.values()).filter(Kind::isRounded).collect(Collectors.toSet());
        assertThat(rounded).isEqualTo(Set.of(Kind.P, Kind.CP));
    }

    @Test
    void theSurfaceBreakingKindsAreTheOnesAFingerWouldFind() {
        assertThat(Kind.U.isSurfaceBreaking()).isTrue();
        assertThat(Kind.BT.isSurfaceBreaking()).isTrue();
        assertThat(Kind.IC.isSurfaceBreaking()).isTrue();
        assertThat(Kind.ISI.isSurfaceBreaking()).isFalse();
    }

    @Test
    void aCrackIsNeverAcceptableAtAnyLength() {
        assertThat(Kind.C.isAlwaysRejectable()).isTrue();
        for (Kind kind : Kind.values()) {
            if (kind != Kind.C) {
                assertThat(kind.isAlwaysRejectable()).isFalse();
            }
        }
    }

    @Test
    void theAggregatedKindsAddUpAgainstALengthOfWeld() {
        assertThat(Kind.ESI.isAggregated()).isTrue();
        assertThat(Kind.IP.isAggregated()).isTrue();
        assertThat(Kind.U.isAggregated()).isTrue();
        assertThat(Kind.P.isAggregated()).isFalse();
        assertThat(Kind.ISI.isAggregated()).isFalse();
    }

    @Test
    void theTwoKindsOfInadequatePenetrationAreSeparateEntries() {
        assertThat(Kind.IP).isNotSameAs(Kind.IPD);
        assertThat(Kind.IP.isPlanar()).isTrue();
        assertThat(Kind.IPD.isPlanar()).isTrue();
        assertThat(Kind.IPD.value()).contains("high low");
    }

    @Test
    void theTwoKindsOfIncompleteFusionAreSeparateToo() {
        assertThat(Kind.IF).isNotSameAs(Kind.IFD);
        assertThat(Kind.IFD.value()).contains("cold lap");
    }

    @Test
    void aCodeReadsBackIntoAKind() {
        assertThat(Kind.byCode("ESI")).isSameAs(Kind.ESI);
        assertThat(Kind.byCode(" ip ")).isSameAs(Kind.IP);
        assertThat(Kind.byCode("c")).isSameAs(Kind.C);
    }

    @Test
    void anUnknownCodeIsRefused() {
        assertThatThrownBy(() -> Kind.byCode("XX")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Kind.byCode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCodeIsWhatGoesOnTheReport() {
        assertThat(Kind.ESI.code()).isEqualTo("ESI");
        assertThat(Kind.IPD).hasToString("IPD");
        assertThat(Kind.C.describe()).isEqualTo("C, crack, planar");
        assertThat(Kind.P.describe()).isEqualTo("P, porosity, volumetric");
    }
}
