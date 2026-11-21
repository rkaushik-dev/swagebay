package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.Admissibility;
import io.github.rkaushikdev.swagebay.imaging.DensityVerdict;
import io.github.rkaushikdev.swagebay.imaging.Evidence;
import io.github.rkaushikdev.swagebay.imaging.NoiseMeasurement;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.WorkMapper;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Recording that a shot was made, and what came out of it.
 *
 * <p>Three things happen and they happen in one transaction. A number is taken. A radiograph row
 * is written with that number. The admissibility verdict is stored against it. Splitting them is
 * what leaves a hole in the numbering, and the hole is not recoverable afterwards because nobody can
 * prove whether the missing number was a lost film or a rolled back transaction.
 *
 * <p>The service will not record a shot against a weld that already has that exposure index. Two
 * films both labelled exposure two on the same weld means one of them is a reshoot that somebody
 * filed as a first shot, and the difference matters to the repair rate and to the coverage check.
 *
 * <p>Admissibility is computed here rather than being passed in. The caller supplies the evidence,
 * which is what the reader measured, and the rules decide. A caller that can pass in the verdict is
 * a caller that can pass in the wrong one.
 */
public final class ShootingService {

    private ShootingService() {}

    /**
     * Number the shot, store it, and say whether it is admissible.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job or a weld that is not there
     * @throws ConflictError when the weld already has that exposure, with the weld and the exposure
     */
    public static ShotResult recordShot(UnitOfWork work, JobNumber job, ShotRecord record) {
        JobRow jobRow = work.jobs().get(job);
        WeldRow weldRow = work.welds().get(jobRow, record.weld());

        boolean taken = weldRow.getRadiographs().stream()
                .anyMatch(radiograph -> radiograph.getExposureIndex() == record.exposureIndex());
        if (taken) {
            throw new ConflictError(
                    "that exposure is already recorded on this weld",
                    Context.of("weld", record.weld().toString(), "exposure", record.exposureIndex()));
        }

        Evidence evidence = record.evidence();
        Admissibility verdict = Admissibility.assess(evidence);
        String number = work.numbering().takeRadiographNumber(job.toString());
        RadiographRow row = new RadiographRow();
        row.setNumber(number);
        row.setExposureIndex(record.exposureIndex());
        row.setExposedAt(record.exposedAt());
        row.setTechniqueReference(record.techniqueReference());
        row.setMedium(evidence.system().medium().value());
        row.setAdmissible(verdict.admissible());
        row.setInadmissibleReason(
                verdict.admissible() ? null : String.join(WorkMapper.REASON_SEPARATOR, verdict.reasons()));
        row.setDensityThroughIndicator(
                evidence.density().flatMap(DensityVerdict::indicatorDensity).orElse(null));
        row.setNormalisedSnr(evidence.noise().map(NoiseMeasurement::normalised).orElse(null));
        weldRow.addRadiograph(row);
        work.flush();
        return new ShotResult(number, verdict);
    }

    /**
     * Which exposures are already on a weld, in order.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job or a weld that is not there
     */
    public static List<Integer> exposuresRecorded(UnitOfWork work, JobNumber job, WeldId weld) {
        WeldRow weldRow = work.welds().get(work.jobs().get(job), weld);
        return weldRow.getRadiographs().stream()
                .map(RadiographRow::getExposureIndex)
                .sorted()
                .toList();
    }

    /**
     * The exposure numbers from one to {@code expected} that have not been shot yet.
     *
     * @throws ValidationError for an expected count below one
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job or a weld that is not there
     */
    public static List<Integer> missingExposures(UnitOfWork work, JobNumber job, WeldId weld, int expected) {
        if (expected < 1) {
            throw new ValidationError("a weld needs at least one exposure");
        }
        Set<Integer> done = new TreeSet<>(exposuresRecorded(work, job, weld));
        return IntStream.rangeClosed(1, expected)
                .filter(index -> !done.contains(index))
                .boxed()
                .toList();
    }
}
