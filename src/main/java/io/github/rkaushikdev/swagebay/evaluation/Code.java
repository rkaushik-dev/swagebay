package io.github.rkaushikdev.swagebay.evaluation;

/**
 * The acceptance code a job is worked to. It is a property of the job, not of the weld.
 *
 * <p>The same weld, with the same imperfections, in the same wall, is accepted under one and
 * rejected under the other, and that is not a defect in either of them. API 1104 was written for a
 * pipeline that will be pressure tested and then buried, UW-51 for a vessel that will be in service
 * with people standing next to it.
 *
 * <p>A vessel examined by spot radiography is judged under UW-52 rather than UW-51. The design
 * took a lower joint efficiency in exchange for shooting less of the weld, and the acceptance
 * standard that goes with it is looser on slag and blind to porosity, but has no allowance at all
 * for lack of fusion.
 */
public enum Code {
    API_1104("API 1104", true, false, "9.3"),
    ASME_VIII("ASME Section VIII Division 1", false, true, "UW-51"),
    ASME_VIII_SPOT("ASME Section VIII Division 1 spot", false, true, "UW-52");

    private final String value;
    private final boolean judgesByKind;
    private final boolean windowMovesWithTheWall;
    private final String paragraph;

    Code(String value, boolean judgesByKind, boolean windowMovesWithTheWall, String paragraph) {
        this.value = value;
        this.judgesByKind = judgesByKind;
        this.windowMovesWithTheWall = windowMovesWithTheWall;
        this.paragraph = paragraph;
    }

    /** The code the way a specification names it. */
    public String value() {
        return value;
    }

    /** API has a rule per imperfection. UW-51 and UW-52 have a rule per shape. */
    public boolean judgesByKind() {
        return judgesByKind;
    }

    /** Whether the aligned window is a number of wall thicknesses rather than a fixed length. */
    public boolean windowMovesWithTheWall() {
        return windowMovesWithTheWall;
    }

    /** The paragraph every clause of the code is numbered under: {@code UW-52}. */
    public String paragraph() {
        return paragraph;
    }

    @Override
    public String toString() {
        return value;
    }
}
