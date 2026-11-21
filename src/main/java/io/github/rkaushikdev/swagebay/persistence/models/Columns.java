package io.github.rkaushikdev.swagebay.persistence.models;

/** Column definitions more than one table shares. */
final class Columns {

    /**
     * Text with no stated bound: character varying with no length, which holds as much as the
     * database allows in one character column. Free text and the exact text of a decimal both go
     * in one of these. It stays a character column rather than a large object so that a check
     * constraint can compare it with a number or measure its length.
     */
    static final String TEXT = "character varying";

    private Columns() {}
}
