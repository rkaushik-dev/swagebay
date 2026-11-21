package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.quantities.Length;

/**
 * What lies inside one window along a weld.
 *
 * @param total the length of weld affected inside the window, overlap counted once
 * @param count how many indications reach into the window
 */
public record WindowTotal(Length total, int count) {}
