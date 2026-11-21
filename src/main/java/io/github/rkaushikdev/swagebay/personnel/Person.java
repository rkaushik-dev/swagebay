package io.github.rkaushikdev.swagebay.personnel;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Somebody on the crew, with both kinds of authority on one card.
 *
 * <p>The certification level and the licence role are separate and both combinations really
 * happen: a new Level II who has not sat the licence exam cannot take a source out of the truck,
 * and a veteran radiographer whose eye exam lapsed last month cannot interpret film. Expiry is
 * three dates, not one, and every one that has run out is reported rather than the first.
 *
 * @param badge the badge number, never blank
 * @param name the name on the card
 * @param level the certification level under the written practice
 * @param licenceRole what the radiation licence allows
 * @param certifiedOn when the certification was granted
 * @param nearVisionOn when the near vision test was passed
 * @param colourPerceptionOn when the colour perception test was passed
 * @param methods the methods the person is certified in, which include radiography
 */
public record Person(
        String badge,
        String name,
        Level level,
        LicenceRole licenceRole,
        LocalDate certifiedOn,
        LocalDate nearVisionOn,
        LocalDate colourPerceptionOn,
        Set<String> methods) {

    /** The method this package tracks. */
    public static final String RADIOGRAPHY = "RT";

    /**
     * A person; the methods are copied.
     *
     * @throws ValidationError without a badge number, or without radiography among the methods
     */
    public Person {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(licenceRole, "licenceRole");
        Objects.requireNonNull(certifiedOn, "certifiedOn");
        Objects.requireNonNull(nearVisionOn, "nearVisionOn");
        Objects.requireNonNull(colourPerceptionOn, "colourPerceptionOn");
        methods = Set.copyOf(methods);
        if (Text.strip(badge).isEmpty()) {
            throw new ValidationError("a person needs a badge number");
        }
        if (!methods.contains(RADIOGRAPHY)) {
            throw new ValidationError("this package only tracks radiography", Context.of("badge", badge));
        }
    }

    /** A person certified in radiography alone. */
    public Person(
            String badge,
            String name,
            Level level,
            LicenceRole licenceRole,
            LocalDate certifiedOn,
            LocalDate nearVisionOn,
            LocalDate colourPerceptionOn) {
        this(badge, name, level, licenceRole, certifiedOn, nearVisionOn, colourPerceptionOn, Set.of(RADIOGRAPHY));
    }

    /** The first day the certification no longer holds. */
    public LocalDate certificationExpires() {
        return certifiedOn.plusDays(Personnel.CERTIFICATION_INTERVAL_DAYS);
    }

    /** The first day the near vision test no longer holds. */
    public LocalDate nearVisionExpires() {
        return nearVisionOn.plusDays(Personnel.VISION_INTERVAL_DAYS);
    }

    /** The first day the colour perception test no longer holds. */
    public LocalDate colourPerceptionExpires() {
        return colourPerceptionOn.plusDays(Personnel.VISION_INTERVAL_DAYS);
    }

    /**
     * Every credential that has run out by that day, in the order {@code certification},
     * {@code near vision test}, {@code colour perception test}.
     */
    public List<String> lapsedOn(LocalDate day) {
        List<String> out = new ArrayList<>();
        if (!day.isBefore(certificationExpires())) {
            out.add("certification");
        }
        if (!day.isBefore(nearVisionExpires())) {
            out.add("near vision test");
        }
        if (!day.isBefore(colourPerceptionExpires())) {
            out.add("colour perception test");
        }
        return List.copyOf(out);
    }

    /** Whether nothing has run out by that day. */
    public boolean isCurrentOn(LocalDate day) {
        return lapsedOn(day).isEmpty();
    }

    /** Whether the person may read film on that day. */
    public boolean mayInterpretOn(LocalDate day) {
        return level.mayInterpret() && isCurrentOn(day);
    }

    /** Whether the person may write a technique sheet on that day. */
    public boolean mayWriteTechniqueOn(LocalDate day) {
        return level.mayWriteTechnique() && isCurrentOn(day);
    }

    /** Whether the person may approve a procedure on that day. */
    public boolean mayApproveProcedureOn(LocalDate day) {
        return level.mayApproveProcedure() && isCurrentOn(day);
    }

    /**
     * Whether the person may run a source without a radiographer beside them. This is the licence
     * question, which does not care about the level at all, nor about the eye tests.
     */
    public boolean mayShootAloneOn(LocalDate day) {
        if (!licenceRole.mayOperateUnattended()) {
            return false;
        }
        return !lapsedOn(day).contains("certification");
    }

    /** The name, badge and level as the card reads: {@code lead hand (0142, Level II)}. */
    @Override
    public String toString() {
        return name + " (" + badge + ", " + level + ")";
    }
}
