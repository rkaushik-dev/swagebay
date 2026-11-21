package io.github.rkaushikdev.swagebay.naming;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.evaluation.Kind;
import io.github.rkaushikdev.swagebay.geometry.ShotType;
import io.github.rkaushikdev.swagebay.imaging.IndicatorSide;
import io.github.rkaushikdev.swagebay.imaging.TechniqueClass;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.safety.InspectionItem;
import io.github.rkaushikdev.swagebay.util.Text;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reading a stored or typed name back into the thing it names.
 *
 * <p>Every lookup here behaves the same way. It accepts the published value, which is what a sheet
 * prints, and the member name, which is what a caller types, in any case and with the spaces or
 * hyphens of a typed name standing for its underscores. Surrounding white space is ignored. An enum
 * that carries a label alongside its value publishes the label, because nobody types a tuple of a
 * label and a severity.
 *
 * <p>Every one refuses rather than defaulting. A name nobody publishes is a typo or a stale
 * integration, and quietly picking the first entry is how a job ends up worked to a code nobody
 * chose.
 */
public final class Naming {

    private Naming() {}

    /**
     * What a caller would type for one member, which is not always its value. An inspection item
     * publishes its label; the other enums named here publish their value, the text a sheet prints.
     * Any other enum publishes its text.
     */
    public static String published(Enum<?> member) {
        return switch (member) {
            case InspectionItem item -> item.label();
            case Code code -> code.value();
            case Kind kind -> kind.value();
            case ShotType shot -> shot.value();
            case IndicatorSide side -> side.value();
            case TechniqueClass techniqueClass -> techniqueClass.value();
            case Disposition disposition -> disposition.value();
            default -> member.toString();
        };
    }

    /**
     * The member whose published value or name matches, or a refusal naming every published value.
     *
     * @param kind the enum to look in
     * @param value what was typed or stored
     * @param what what is being looked for, for the refusal: {@code acceptance code}
     * @throws ValidationError naming the value given and every one that is known
     */
    public static <E extends Enum<E>> E named(Class<E> kind, String value, String what) {
        String wanted = Text.strip(value);
        String lowered = wanted.toLowerCase(Locale.ROOT);
        String folded = lowered.replace(" ", "_").replace("-", "_");
        for (E member : kind.getEnumConstants()) {
            String name = member.name().toLowerCase(Locale.ROOT);
            if (published(member).equals(wanted) || name.equals(lowered) || name.equals(folded)) {
                return member;
            }
        }
        String known =
                Arrays.stream(kind.getEnumConstants()).map(Naming::published).collect(Collectors.joining(", "));
        throw new ValidationError("no " + what + " by that name", Context.of("given", value, "known", known));
    }

    /** An acceptance code: {@code API 1104} or {@code api_1104}. */
    public static Code codeNamed(String value) {
        return named(Code.class, value, "acceptance code");
    }

    /** A shot type: {@code double wall single image} or {@code dwsi}. */
    public static ShotType shotNamed(String value) {
        return named(ShotType.class, value, "shot type");
    }

    /** A shot type when one is given; nothing means let the planner pick, which is a real answer. */
    public static Optional<ShotType> optionalShotNamed(Optional<String> value) {
        return value.map(Naming::shotNamed);
    }

    /** An indicator side: {@code film side} or {@code film}. */
    public static IndicatorSide sideNamed(String value) {
        return named(IndicatorSide.class, value, "indicator side");
    }

    /** A technique class: {@code B} or {@code b}. */
    public static TechniqueClass techniqueClassNamed(String value) {
        return named(TechniqueClass.class, value, "technique class");
    }

    /** A discontinuity, named by its code and now also by its description: {@code ESI} or {@code elongated slag inclusion}. */
    public static Kind kindNamed(String value) {
        return named(Kind.class, value, "discontinuity code");
    }

    /** A disposition: {@code use as is} or {@code use_as_is}. */
    public static Disposition dispositionNamed(String value) {
        return named(Disposition.class, value, "disposition");
    }

    /** An inspection item: {@code drive cable}, {@code drive_cable} or {@code drive-cable}. */
    public static InspectionItem itemNamed(String value) {
        return named(InspectionItem.class, value, "inspection item");
    }
}
