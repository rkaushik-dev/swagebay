package io.github.rkaushikdev.swagebay.imaging;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the processor read the day it was set up, which every later strip is compared to.
 *
 * <p>Comparing to the setup and not to yesterday is the point: comparing to yesterday follows a
 * drift down instead of catching it.
 *
 * @param establishedOn the day the processor was set up
 * @param speed the speed reading that day
 * @param contrast the contrast reading that day
 * @param fog the fog reading that day
 */
public record Baseline(LocalDate establishedOn, BigDecimal speed, BigDecimal contrast, BigDecimal fog) {}
