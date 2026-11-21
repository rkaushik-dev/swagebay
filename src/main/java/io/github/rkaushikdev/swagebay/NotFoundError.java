package io.github.rkaushikdev.swagebay;

import io.github.rkaushikdev.swagebay.util.Repr;

/** A record that was asked for by identity does not exist. */
public class NotFoundError extends SwagebayError {

    /** The code of this class of error. */
    public static final String CODE = "qi.not_found";

    private final String kind;
    private final Object identity;

    /** No record of that kind with that identity. */
    public NotFoundError(String kind, Object identity) {
        super("no " + kind + " with identity " + Repr.of(identity), Context.of("kind", kind, "identity", identity));
        this.kind = kind;
        this.identity = identity;
    }

    @Override
    public String code() {
        return CODE;
    }

    /** The kind of record that was looked for. */
    public String kind() {
        return kind;
    }

    /** The identity it was looked for under. */
    public Object identity() {
        return identity;
    }
}
