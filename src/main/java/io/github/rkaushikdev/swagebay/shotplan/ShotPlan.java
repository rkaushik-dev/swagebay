package io.github.rkaushikdev.swagebay.shotplan;

import io.github.rkaushikdev.swagebay.safety.RadiationField;
import io.github.rkaushikdev.swagebay.safety.RopeOff;
import io.github.rkaushikdev.swagebay.technique.TechniqueSheet;
import java.util.List;
import java.util.Objects;

/**
 * Everything that has to be true before the source comes out.
 *
 * <p>A blocker is not a warning. A sheet with warnings gets signed by a Level III who has read
 * them. A plan with blockers does not go out.
 *
 * @param sheet the technique sheet the plan was made from
 * @param radiationField the field the source makes on site
 * @param rope the restricted area rope off round that field
 * @param site what the ground allows
 * @param blockers everything in the way, in the order a supervisor walking the job finds it
 * @param warnings the sheet's warnings, which need a signature and do not block
 * @param expectedDose what each of the crew is expected to take, in the order they were kitted
 */
public record ShotPlan(
        TechniqueSheet sheet,
        RadiationField radiationField,
        RopeOff rope,
        SiteConstraints site,
        List<String> blockers,
        List<String> warnings,
        List<CrewDose> expectedDose) {

    /** A plan; the lists are copied. */
    public ShotPlan {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(radiationField, "radiationField");
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(site, "site");
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
        expectedDose = List.copyOf(expectedDose);
    }

    /** Whether nothing is in the way. */
    public boolean canProceed() {
        return blockers.isEmpty();
    }

    /** Whether the sheet carries warnings a Level III has to sign for. */
    public boolean needsASignature() {
        return !warnings.isEmpty();
    }

    /** The blockers joined with {@code "; "}, or the sheet and the rope off when it can go. */
    public String describe() {
        if (!blockers.isEmpty()) {
            return String.join("; ", blockers);
        }
        return sheet.describe() + ", " + rope.describe();
    }
}
