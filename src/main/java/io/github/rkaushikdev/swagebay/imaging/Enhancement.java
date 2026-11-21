package io.github.rkaushikdev.swagebay.imaging;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.QualificationError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What may be done to a digital radiograph, and what may not.
 *
 * <p>A film is a physical object and what is on it is what came out of the processor. A digital
 * image is numbers, and somebody can change the numbers. A processing history is held against the
 * set of operations the technique qualified, and every objection is reported.
 */
public final class Enhancement {

    private Enhancement() {}

    /** Everything about a processing history that a reviewer would object to, in order. */
    public static List<String> faults(History history, Set<Operation> qualified) {
        List<String> out = new ArrayList<>();
        for (Applied item : history.operations()) {
            Operation operation = item.operation();
            if (operation.isForbidden()) {
                out.add(operation + " is not allowed on a graded image");
            } else if (operation.needsQualification() && !qualified.contains(operation)) {
                out.add(operation + " was applied but is not part of the qualified technique");
            }
        }
        long compressions = history.operations().stream()
                .filter(item -> item.operation() == Operation.LOSSY_COMPRESSION)
                .count();
        if (compressions > 1) {
            out.add("the image has been compressed more than once");
        }
        return List.copyOf(out);
    }

    /** Whether the history draws no objection. */
    public static boolean isAdmissible(History history, Set<Operation> qualified) {
        return faults(history, qualified).isEmpty();
    }

    /**
     * Refuse an image whose processing history disqualifies it; a sound history passes silently.
     *
     * @throws QualificationError naming every fault, joined with semicolons, as {@code reasons}
     */
    public static void check(History history, Set<Operation> qualified) {
        List<String> found = faults(history, qualified);
        if (!found.isEmpty()) {
            throw new QualificationError(
                    "the processing history disqualifies this image", Context.of("reasons", String.join("; ", found)));
        }
    }

    /** The set a shop qualifies when it qualifies nothing, which is the usual case: the reversible ones. */
    public static Set<Operation> viewingOnly() {
        Set<Operation> reversible = Arrays.stream(Operation.values())
                .filter(Operation::isReversible)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Operation.class)));
        return Collections.unmodifiableSet(reversible);
    }
}
