package io.github.rkaushikdev.swagebay.testing;

import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.safety.Inspection;
import java.time.LocalDate;

/** Fixtures that write through a unit of work, for the tests that need records already on file. */
public final class WorkFixtures {

    private WorkFixtures() {}

    /**
     * Put a device through both checks so it can leave the shop on {@code day}: a quarterly thirty
     * days before, and a pre use check on the day itself.
     *
     * <p>Every test that books a source out needs this, because the dispatch gate asks for both.
     */
    public static void passInspections(UnitOfWork work, String deviceSerial, LocalDate day) {
        passInspections(work, deviceSerial, day, day.minusDays(30));
    }

    /** {@link #passInspections(UnitOfWork, String, LocalDate)} with the quarterly done on {@code quarterlyOn}. */
    public static void passInspections(UnitOfWork work, String deviceSerial, LocalDate day, LocalDate quarterlyOn) {
        work.maintenance().record(Inspection.quarterly(deviceSerial, quarterlyOn, "0303"));
        work.maintenance().record(Inspection.preUse(deviceSerial, day, "0142"));
    }
}
