package io.github.rkaushikdev.swagebay.testing;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;

import io.github.rkaushikdev.swagebay.evaluation.Code;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.services.JobService;
import io.github.rkaushikdev.swagebay.services.NewJob;
import io.github.rkaushikdev.swagebay.services.NewWeld;
import io.github.rkaushikdev.swagebay.services.ShootingService;
import io.github.rkaushikdev.swagebay.services.ShotRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A job with shots on it, made through the services, for every test that needs films on file
 * before it can say anything: readings, rereads, the close out, the image store, the morning sheet.
 */
public final class ServiceFixtures {

    /** The day the fixture job opens, and its weld is made. */
    public static final LocalDate OPENED = LocalDate.of(2024, 9, 23);

    /** When every fixture exposure is made. */
    public static final LocalDateTime SHOT = LocalDateTime.of(2024, 9, 24, 23, 30);

    /** The one weld on the fixture job. */
    public static final WeldId WELD = WeldId.parse("GW-104");

    private ServiceFixtures() {}

    /**
     * A job, and the radiograph numbers its shots were given, in the order shot.
     *
     * @param job the job opened
     * @param numbers the numbers of the films shot on its weld
     */
    public record Prepared(JobNumber job, List<String> numbers) {

        /** A prepared job; the numbers are copied. */
        public Prepared {
            numbers = List.copyOf(numbers);
        }
    }

    /** {@link #prepared(UnitOfWork, int, boolean)} with one sound shot. */
    public static Prepared prepared(UnitOfWork work) {
        return prepared(work, 1, true);
    }

    /** {@link #prepared(UnitOfWork, int, boolean)} with that many sound shots. */
    public static Prepared prepared(UnitOfWork work, int shots) {
        return prepared(work, shots, true);
    }

    /**
     * An API 1104 job opened on {@link #OPENED}, weld {@link #WELD} on it (six inch schedule 80
     * carbon steel, 529 mm long, a 2 mm cap), and {@code shots} exposures of it recorded at
     * {@link #SHOT} on the fixture film, numbered from one.
     *
     * @param visible whether the essential hole shows on every film, which decides whether they are
     *     admissible
     */
    public static Prepared prepared(UnitOfWork work, int shots, boolean visible) {
        JobNumber job = JobService.openJob(work, new NewJob("c", "s", Code.API_1104, "RT-001", OPENED));
        JobService.addWeld(
                work,
                job,
                new NewWeld(WELD, PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), mm(529), "w-19", OPENED));
        List<String> numbers = new ArrayList<>();
        for (int index = 1; index <= shots; index++) {
            ShotRecord shot = new ShotRecord(WELD, index, SHOT, "RT-001 rev 1", ImagingFixtures.filmEvidence(visible));
            numbers.add(ShootingService.recordShot(work, job, shot).number());
        }
        return new Prepared(job, numbers);
    }
}
