package io.github.rkaushikdev.swagebay.materials;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QualityTest {

    @Test
    void aShotReadsTheColumnThatCoversItsKilovoltage() {
        assertThat(Quality.forKilovoltage(80)).contains(Quality.KV100);
        assertThat(Quality.forKilovoltage(100)).contains(Quality.KV100);
        assertThat(Quality.forKilovoltage(101)).contains(Quality.KV150);
        assertThat(Quality.forKilovoltage(220)).contains(Quality.KV220);
        assertThat(Quality.forKilovoltage(300)).contains(Quality.KV400);
        assertThat(Quality.forKilovoltage(450)).contains(Quality.MV1);
        assertThat(Quality.forKilovoltage(1500)).contains(Quality.MV2);
        assertThat(Quality.forKilovoltage(6000)).contains(Quality.MV4_25);
    }

    @Test
    void nothingCoversNoVoltageOrMoreThanTheTableGoesTo() {
        assertThat(Quality.forKilovoltage(0)).isEmpty();
        assertThat(Quality.forKilovoltage(-10)).isEmpty();
        assertThat(Quality.forKilovoltage(25_001)).isEmpty();
    }

    @Test
    void theIsotopeColumnsHaveNoKilovoltage() {
        assertThat(Quality.IR192.isXray()).isFalse();
        assertThat(Quality.CO60.ceilingKv()).isEmpty();
        assertThat(Quality.KV220.isXray()).isTrue();
        assertThat(Quality.KV220.ceilingKv()).contains(220);
        assertThat(Quality.MV4_25.ceilingKv()).contains(25_000);
    }
}
