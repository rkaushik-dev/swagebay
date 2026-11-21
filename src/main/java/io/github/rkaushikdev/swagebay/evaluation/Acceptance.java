package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;

/**
 * One door in front of two codes that do not agree about anything.
 *
 * <p>What this does is pick, and record what it picked. What it does not do is average, reconcile
 * or apply both. A finding that quotes a clause from one code and a limit from the other is
 * worthless, and the way that gets written is by having one function that tries to be clever about
 * which rule is stricter.
 */
public final class Acceptance {

    private Acceptance() {}

    /**
     * Apply exactly one code, and say which one it was.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Verdict judge(Code code, IndicationSet found, Length wall) {
        if (wall.um() <= 0) {
            throw new ValidationError("a wall thickness has to be positive");
        }
        return switch (code) {
            case API_1104 -> fromApi(Assessment.assess(found, wall), wall);
            case ASME_VIII -> fromAsme(found, wall);
        };
    }

    /**
     * Whether the two codes reach different dispositions on the same weld.
     *
     * <p>It is the question a client asks when they change the specification halfway through a job,
     * and it is worth being able to answer without running both by hand.
     */
    public static boolean codesDisagree(IndicationSet found, Length wall) {
        Disposition api = judge(Code.API_1104, found, wall).disposition();
        Disposition asme = judge(Code.ASME_VIII, found, wall).disposition();
        return api != asme;
    }

    private static Verdict fromApi(Assessment assessment, Length wall) {
        return new Verdict(Code.API_1104, assessment.disposition(), assessment.findings(), wall);
    }

    /** UW-51 findings quote their subclause, and are filed against the kind of the first indication they name. */
    private static Verdict fromAsme(IndicationSet found, Length wall) {
        List<Finding> findings = AsmeUw51.evaluate(found.indications(), wall).stream()
                .map(item -> new Finding(
                        "UW-51(" + item.rule() + ")",
                        item.indications().isEmpty()
                                ? Kind.C
                                : item.indications().get(0).kind(),
                        item.reason(),
                        item.indications()))
                .toList();
        return new Verdict(Code.ASME_VIII, Assessment.dispositionOf(findings), findings, wall);
    }
}
