package io.github.rkaushikdev.swagebay.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class TimesTest {

    @Test
    void secondsAreAlwaysWrittenAndAFractionOnlyWhenThereIsOne() {
        assertThat(Times.iso(LocalDateTime.of(2024, 5, 1, 6, 30))).isEqualTo("2024-05-01T06:30:00");
        assertThat(Times.iso(LocalDateTime.of(2024, 5, 1, 6, 30, 0, 250_000_000)))
                .isEqualTo("2024-05-01T06:30:00.250000");
        assertThat(Times.text(LocalDateTime.of(2024, 5, 1, 6, 30))).isEqualTo("2024-05-01 06:30:00");
        assertThat(Times.iso(LocalTime.of(22, 0))).isEqualTo("22:00:00");
        assertThat(Times.iso(LocalDate.of(2024, 5, 1))).isEqualTo("2024-05-01");
    }

    @Test
    void anOffsetIsWrittenAsHoursAndMinutes() {
        assertThat(Times.iso(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
                .isEqualTo("2024-05-01T00:00:00+00:00");
        assertThat(Times.iso(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(-3, -30))))
                .isEqualTo("2024-05-01T00:00:00-03:30");
    }

    @Test
    void theWrittenFormReadsBack() {
        LocalDateTime moment = LocalDateTime.of(2024, 11, 5, 23, 0, 1, 123_456_000);
        assertThat(Times.parse(Times.iso(moment))).isEqualTo(moment);
        assertThat(Times.parse("2024-11-05")).isEqualTo(LocalDateTime.of(2024, 11, 5, 0, 0));
        assertThat(Times.parse("2024-11-05 23:00")).isEqualTo(LocalDateTime.of(2024, 11, 5, 23, 0));
        assertThat(Times.isMoment("2024-11-05T23:00:00Z")).isTrue();
        assertThat(Times.isMoment("yesterday")).isFalse();
    }

    @Test
    void anInstantIsReadOnlyWhenItCarriesAnOffset() {
        assertThat(Times.parseInstant("2024-05-01T00:00:00Z"))
                .contains(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(Times.parseInstant("2024-05-01T00:00:00-0330"))
                .contains(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(-3, -30)));
        assertThat(Times.parseInstant("2024-05-01T00:00:00")).isEmpty();
    }

    @Test
    void textThatIsNotAMomentIsRefused() {
        assertThatThrownBy(() -> Times.parse("noon")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> Times.parse("2024-02-30")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> Times.parse("2024-05-01T00:00:00+01:00")).isInstanceOf(DateTimeParseException.class);
    }
}
