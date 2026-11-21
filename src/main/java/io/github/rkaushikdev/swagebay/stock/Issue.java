package io.github.rkaushikdev.swagebay.stock;

/**
 * Sheets taken off the shelf from one lot.
 *
 * @param lotNumber the lot they came from
 * @param sheets how many
 */
public record Issue(String lotNumber, int sheets) {}
