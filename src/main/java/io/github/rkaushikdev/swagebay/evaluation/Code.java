package io.github.rkaushikdev.swagebay.evaluation;

/**
 * The acceptance code a job is worked to. It is a property of the job, not of the weld.
 *
 * <p>The same weld, with the same imperfections, in the same wall, is accepted under one and
 * rejected under the other, and that is not a defect in either of them. API 1104 was written for a
 * pipeline that will be pressure tested and then buried, UW-51 for a vessel that will be in service
 * with people standing next to it.
 */
public enum Code {
    API_1104("API 1104"),
    ASME_VIII("ASME Section VIII Division 1");

    private final String value;

    Code(String value) {
        this.value = value;
    }

    /** The code the way a specification names it. */
    public String value() {
        return value;
    }

    /** API has a rule per imperfection. UW-51 has a rule per shape. */
    public boolean judgesByKind() {
        return this == API_1104;
    }

    /** Whether the aligned window is a number of wall thicknesses rather than a fixed length. */
    public boolean windowMovesWithTheWall() {
        return this == ASME_VIII;
    }

    @Override
    public String toString() {
        return value;
    }
}
