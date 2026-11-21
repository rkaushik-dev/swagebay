package io.github.rkaushikdev.swagebay.sources;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A wipe test on a sealed source and what it found.
 *
 * @param performedAt when the wipe was taken
 * @param becquerelsRemoved the activity the wipe picked up
 * @param technician who took it
 */
public record LeakTest(LocalDateTime performedAt, BigDecimal becquerelsRemoved, String technician) {

    /** Six months between wipes, which is what the licence says and it is not negotiable. */
    public static final int INTERVAL_DAYS = 180;

    /** Two hundredths of a microcurie, the reportable threshold; a wipe at or above it fails. */
    public static final BigDecimal LIMIT_BQ = BigDecimal.valueOf(185);

    /** Whether the wipe came back under the reportable threshold. */
    public boolean passed() {
        return becquerelsRemoved.compareTo(LIMIT_BQ) < 0;
    }

    /** The moment this wipe stops covering the source. */
    public LocalDateTime expiresAt() {
        return performedAt.plusDays(INTERVAL_DAYS);
    }
}
