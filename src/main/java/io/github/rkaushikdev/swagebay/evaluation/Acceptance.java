package io.github.rkaushikdev.swagebay.evaluation;

import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One door in front of codes that do not agree about anything.
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
        List<Finding> findings =
                switch (code) {
                    case API_1104 -> Assessment.assess(found, wall).findings();
                    case ASME_VIII -> fromUw51(found, wall);
                    case ASME_VIII_SPOT -> AsmeUw52.evaluate(found, wall);
                };
        return new Verdict(code, Disposition.of(findings), findings, wall);
    }

    /**
     * What every code says about the same weld, in the order the codes are declared.
     *
     * @throws ValidationError for a wall that is not positive
     */
    public static Map<Code, Disposition> dispositions(IndicationSet found, Length wall) {
        Map<Code, Disposition> out = new EnumMap<>(Code.class);
        for (Code code : Code.values()) {
            out.put(code, judge(code, found, wall).disposition());
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Whether any two codes reach different dispositions on the same weld.
     *
     * <p>It is the question a client asks when they change the specification halfway through a job,
     * and it is worth being able to answer without running every code by hand.
     */
    public static boolean codesDisagree(IndicationSet found, Length wall) {
        return dispositions(found, wall).values().stream().distinct().count() > 1;
    }

    /** UW-51 findings quote their subclause, and are filed against the kind of the first indication they name. */
    private static List<Finding> fromUw51(IndicationSet found, Length wall) {
        return AsmeUw51.evaluate(found.indications(), wall).stream()
                .map(item -> new Finding(
                        Code.ASME_VIII.paragraph() + "(" + item.rule() + ")",
                        item.indications().isEmpty()
                                ? Kind.C
                                : item.indications().get(0).kind(),
                        item.reason(),
                        item.indications()))
                .toList();
    }
}
