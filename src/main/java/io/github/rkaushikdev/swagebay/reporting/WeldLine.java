package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.quantities.Length;
import java.util.List;
import java.util.Objects;

/**
 * One line of the weld summary.
 *
 * @param weld the weld, repair round included: {@code GW-104R1}
 * @param wall its wall thickness
 * @param welder who made it
 * @param radiographs how many films were shot of it
 * @param disposition {@code accept}, {@code repair} or {@code reject}, and {@code none} while the
 *     films do not support one
 * @param clauses the clauses of the code behind the disposition
 */
public record WeldLine(
        String weld, Length wall, String welder, int radiographs, String disposition, List<String> clauses) {

    /** A line; the clauses are copied. */
    public WeldLine {
        Objects.requireNonNull(weld, "weld");
        Objects.requireNonNull(wall, "wall");
        Objects.requireNonNull(welder, "welder");
        Objects.requireNonNull(disposition, "disposition");
        clauses = List.copyOf(clauses);
    }
}
