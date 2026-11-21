package io.github.rkaushikdev.swagebay.reporting;

import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.procedure.ProcedureRegister;
import io.github.rkaushikdev.swagebay.procedure.Revision;
import io.github.rkaushikdev.swagebay.util.Text;
import io.github.rkaushikdev.swagebay.util.Times;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The traceability chain, which is the question a review actually asks.
 *
 * <p>Nobody in a review asks whether the software worked. They pick one radiograph out of a stack of
 * four hundred and ask what it was shot with, to which procedure revision, by whom, and whether all
 * of those were valid on the night. Answering that from four separate records by hand takes twenty
 * minutes a film and it is the single most expensive hour of any audit.
 *
 * <p>The chain is built rather than stored, and each link carries whether it holds. A link that
 * cannot be established is reported as broken rather than being left out, because a chain with a
 * link quietly missing looks complete.
 *
 * <p>The links are deliberately not all the same shape. Some are facts about the record, like which
 * technique a film names. Some are judgements about a date, like whether the procedure revision was
 * in force. Keeping them apart means a reviewer can see which are transcription and which are
 * reasoning.
 */
public final class Traceability {

    private Traceability() {}

    /**
     * The chain behind one radiograph, the procedure revision left unchecked.
     *
     * @throws NotFoundError for a radiograph that is not there
     */
    public static Chain chainFor(UnitOfWork work, String radiographNumber) {
        return chain(work, radiographNumber, Optional.empty());
    }

    /**
     * The chain behind one radiograph, the procedure link resolved to the approved revision that was
     * in force the day it was shot.
     *
     * @throws NotFoundError for a radiograph that is not there
     */
    public static Chain chainFor(UnitOfWork work, String radiographNumber, ProcedureRegister register) {
        return chain(work, radiographNumber, Optional.of(register));
    }

    /**
     * Every chain on one job, in radiograph order, the procedure revision left unchecked.
     *
     * @throws NotFoundError for a job that is not there
     */
    public static List<Chain> chainsForJob(UnitOfWork work, int year, int serial) {
        return chains(work, year, serial, Optional.empty());
    }

    /**
     * Every chain on one job, in radiograph order, each procedure link resolved against the register.
     *
     * @throws NotFoundError for a job that is not there
     */
    public static List<Chain> chainsForJob(UnitOfWork work, int year, int serial, ProcedureRegister register) {
        return chains(work, year, serial, Optional.of(register));
    }

    /**
     * The chains on one job that do not hold, in radiograph order, the procedure revision left
     * unchecked.
     *
     * @throws NotFoundError for a job that is not there
     */
    public static List<Chain> brokenChains(UnitOfWork work, int year, int serial) {
        return broken(chainsForJob(work, year, serial));
    }

    /**
     * The chains on one job that do not hold, in radiograph order, each procedure link resolved
     * against the register.
     *
     * @throws NotFoundError for a job that is not there
     */
    public static List<Chain> brokenChains(UnitOfWork work, int year, int serial, ProcedureRegister register) {
        return broken(chainsForJob(work, year, serial, register));
    }

    /**
     * The chain behind one radiograph, refusing one that does not hold.
     *
     * @throws NotFoundError for a radiograph that is not there, or one whose chain is broken
     */
    public static Chain assertTraceable(UnitOfWork work, String radiographNumber) {
        Chain chain = chainFor(work, radiographNumber);
        if (!chain.isSound()) {
            throw new NotFoundError("intact traceability chain", radiographNumber);
        }
        return chain;
    }

    private static Chain chain(UnitOfWork work, String radiographNumber, Optional<ProcedureRegister> register) {
        RadiographRow film = work.welds().radiograph(radiographNumber);
        WeldRow weld = film.getWeld().orElseThrow();
        JobRow job = weld.getJob().orElseThrow();
        LocalDate shotOn = film.getExposedAt().toLocalDate();
        String technique = film.getTechniqueReference();
        boolean read = film.getReadAt().isPresent();

        List<Link> links = List.of(
                new Link("job", String.format(Locale.ROOT, "%02d-%04d", job.getYear(), job.getSerial()), true),
                new Link("weld", weld.getPrefix() + "-" + weld.getNumber(), true),
                new Link("welder", weld.getWelder(), !Text.isBlank(weld.getWelder())),
                new Link("exposed", Times.iso(shotOn), true),
                new Link(
                        "technique",
                        technique,
                        !Text.isBlank(technique),
                        Text.isBlank(technique) ? "no technique named" : ""),
                new Link(
                        "admissible",
                        film.isAdmissible() ? "yes" : "no",
                        film.isAdmissible(),
                        film.getInadmissibleReason().orElse("")),
                new Link("read by", film.getReadBy().orElse("nobody"), read, read ? "" : "not yet read"),
                procedureLink(job.getProcedureNumber(), shotOn, register));
        return new Chain(radiographNumber, links);
    }

    /**
     * Whether an approved revision of the procedure was in force the day the film was shot. Any
     * refusal from the register means the link does not hold; without a register it is not checked.
     */
    private static Link procedureLink(String number, LocalDate shotOn, Optional<ProcedureRegister> register) {
        if (register.isEmpty()) {
            return new Link("procedure", number, true, "revision not checked, no register given");
        }
        Revision revision;
        try {
            revision = register.get().effectiveOn(number, shotOn);
        } catch (SwagebayError refused) {
            return new Link("procedure", number, false, "no approved revision was in force");
        }
        return new Link("procedure", revision.label(), true);
    }

    private static List<Chain> chains(UnitOfWork work, int year, int serial, Optional<ProcedureRegister> register) {
        JobRow job = work.jobs().get(new JobNumber(year, serial));
        return job.getWelds().stream()
                .flatMap(weld -> weld.getRadiographs().stream())
                .map(RadiographRow::getNumber)
                .sorted()
                .map(number -> chain(work, number, register))
                .toList();
    }

    private static List<Chain> broken(List<Chain> chains) {
        return chains.stream().filter(chain -> !chain.isSound()).toList();
    }
}
