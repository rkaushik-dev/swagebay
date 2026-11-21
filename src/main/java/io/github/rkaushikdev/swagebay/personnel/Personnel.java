package io.github.rkaushikdev.swagebay.personnel;

import java.time.LocalDate;
import java.util.List;

/**
 * Who is allowed to do what, and until when.
 *
 * <p>Two systems of authority land on the same person and people mix them up constantly.
 * Certification under the written practice says what somebody may do with a radiograph. Certification
 * as a radiographer under the radiation licence says what somebody may do with a source: a
 * radiographer may run a job, an assistant may only work under the direct personal supervision of
 * one. These are the questions asked of a whole crew.
 */
public final class Personnel {

    /** The eye tests are annual. */
    public static final int VISION_INTERVAL_DAYS = 365;

    /** Three years, which is what the written practice says for radiography. */
    public static final int CERTIFICATION_INTERVAL_DAYS = 1095;

    private Personnel() {}

    /**
     * Whether a crew on site is properly supervised: an assistant needs a licensed radiographer
     * with them, and an empty crew is not supervised either.
     */
    public static boolean supervisionSatisfied(List<Person> crew, LocalDate day) {
        return crew.stream().anyMatch(person -> person.mayShootAloneOn(day));
    }

    /** The members of a crew who may read the film on that day, in crew order. */
    public static List<Person> interpreters(List<Person> crew, LocalDate day) {
        return crew.stream().filter(person -> person.mayInterpretOn(day)).toList();
    }
}
