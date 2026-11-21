package io.github.rkaushikdev.swagebay.testing;

import io.github.rkaushikdev.swagebay.personnel.Level;
import io.github.rkaushikdev.swagebay.personnel.LicenceRole;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.safety.Assignment;
import io.github.rkaushikdev.swagebay.safety.Dosimeter;
import io.github.rkaushikdev.swagebay.safety.DosimeterKind;
import java.time.LocalDate;
import java.util.List;

/** The crew, their dosimetry, and the reader most tests want, that the job and reading tests build on. */
public final class PeopleFixtures {

    private static final LocalDate CERTIFIED = LocalDate.of(2023, 1, 15);
    private static final LocalDate CREW_EYES = LocalDate.of(2024, 1, 10);
    private static final LocalDate READER_EYES = LocalDate.of(2024, 6, 1);

    private PeopleFixtures() {}

    /** A licensed Level II lead hand, badge {@code 0142}, and a Level I assistant, badge {@code 0201}. */
    public static List<Person> crew() {
        Person reader = new Person(
                "0142", "lead hand", Level.LEVEL_II, LicenceRole.RADIOGRAPHER, CERTIFIED, CREW_EYES, CREW_EYES);
        Person helper = new Person(
                "0201",
                "assistant",
                Level.LEVEL_I,
                LicenceRole.ASSISTANT,
                LocalDate.of(2023, 6, 1),
                CREW_EYES,
                CREW_EYES);
        return List.of(reader, helper);
    }

    /**
     * A full kit of dosimeters for each of the {@link #crew()}, badges {@code 0142} and {@code 0201},
     * the instruments read on the day calibrated until the start of 2025.
     */
    public static List<Assignment> kitted() {
        return List.of(kit("0142"), kit("0201"));
    }

    /** A Level II radiographer with current eyes, badge {@code 0142}, which is what most tests want. */
    public static Person aReader() {
        return aReader("0142");
    }

    /** {@link #aReader()} under another badge. */
    public static Person aReader(String badge) {
        return aReader(badge, Level.LEVEL_II);
    }

    /** {@link #aReader()} under another badge, at another level. */
    public static Person aReader(String badge, Level level) {
        return aReader(badge, level, READER_EYES);
    }

    /** {@link #aReader()} under another badge, at another level, with both eye tests passed on that day. */
    public static Person aReader(String badge, Level level, LocalDate vision) {
        return new Person(badge, "reader", level, LicenceRole.RADIOGRAPHER, CERTIFIED, vision, vision);
    }

    private static Assignment kit(String badge) {
        LocalDate due = LocalDate.of(2025, 1, 1);
        return new Assignment(
                badge,
                List.of(
                        new Dosimeter(DosimeterKind.BADGE, "B-" + badge),
                        new Dosimeter(DosimeterKind.DIRECT_READING, "D-" + badge, due),
                        new Dosimeter(DosimeterKind.ALARMING_RATEMETER, "A-" + badge, due)));
    }
}
