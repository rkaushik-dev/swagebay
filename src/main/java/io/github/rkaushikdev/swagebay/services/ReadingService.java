package io.github.rkaushikdev.swagebay.services;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.audit.Action;
import io.github.rkaushikdev.swagebay.audit.Entry;
import io.github.rkaushikdev.swagebay.evaluation.Indication;
import io.github.rkaushikdev.swagebay.evaluation.SpotExamination;
import io.github.rkaushikdev.swagebay.evaluation.Verdict;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.naming.Naming;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.persistence.WorkMapper;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.personnel.Person;
import io.github.rkaushikdev.swagebay.records.WeldRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Recording what a reader saw, and reaching a disposition for the weld.
 *
 * <p>Reading is where the software has the least to say and the most to be careful about. It does
 * not decide what is on the film. A qualified person does that, and this records what they decided,
 * who they were and when.
 *
 * <p>What it does enforce is the three things a reading is not allowed to be. It is not allowed to
 * be done by somebody whose certification or eye exam has lapsed, because a report signed by them is
 * worthless and the shop would rather find that out now than in review. It is not allowed to be
 * recorded twice, because a second reading of the same film is a rereading and it belongs on the
 * record as one, with both readers named. And it is not allowed to be recorded against a film nobody
 * has established is admissible, because indications read off an inadmissible film are not evidence
 * of anything.
 *
 * <p>The weld disposition falls out of the readings rather than being entered. It is computed from
 * every indication on every admissible film covering the weld, run through whichever code the job is
 * worked to.
 */
public final class ReadingService {

    /** What the trail says a reader called on a film with nothing on it. */
    private static final String NOTHING = "nothing";

    private ReadingService() {}

    /**
     * Store a reading, refusing the three things a reading cannot be.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a film that is not there
     * @throws ConflictError when the reader may not interpret film that day, naming what lapsed; when
     *     the film has been read, naming by whom; or when the film is not admissible
     */
    public static void recordReading(UnitOfWork work, ReadingRecord record) {
        RadiographRow row = work.welds().radiograph(record.radiographNumber());

        requireReader(record.reader(), record.readAt());
        if (row.getReadAt().isPresent()) {
            throw new ConflictError(
                    "that film has already been read",
                    Context.of(
                            "number",
                            record.radiographNumber(),
                            "by",
                            row.getReadBy().orElse(null)));
        }
        if (!row.isAdmissible()) {
            throw new ConflictError(
                    "an inadmissible film is not evidence of anything",
                    Context.of("number", record.radiographNumber()));
        }

        row.setReadBy(record.reader().badge());
        row.setReadAt(record.readAt());
        for (Indication indication : record.indications()) {
            row.addIndication(WorkMapper.indicationToRow(indication));
        }
        work.flush();
    }

    /**
     * Store a second reading beside the first, never over it.
     *
     * <p>A client who disagrees with a call asks for another reader to look at the same film. What
     * comes back is a second opinion and the record has to hold both, because the interesting case
     * is the one where they differ and a record that keeps only the later one hides exactly that.
     *
     * <p>The second reader's indications become the ones the disposition is computed from, since a
     * reread exists to correct the first pass. What the first pass said is not lost. It goes on the
     * audit trail as the old value, with the reason the reread was asked for. A reread that agrees
     * goes on the trail as a signature.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a film that is not there
     * @throws ConflictError for a film nobody has read, one already reread, a reread by the first
     *     reader, one dated before the reading it revisits, or a reader who may not interpret film
     *     that day
     */
    public static void recordReread(UnitOfWork work, RereadRequest request) {
        String number = request.radiographNumber();
        RadiographRow row = work.welds().radiograph(number);

        Optional<LocalDateTime> firstRead = row.getReadAt();
        if (firstRead.isEmpty()) {
            throw new ConflictError("nothing has been read on that film yet", Context.of("number", number));
        }
        if (row.getRereadAt().isPresent()) {
            throw new ConflictError(
                    "that film has already been reread",
                    Context.of("number", number, "by", row.getRereadBy().orElse(null)));
        }
        if (row.getReadBy().filter(request.reader().badge()::equals).isPresent()) {
            throw new ConflictError(
                    "a reread is done by somebody other than the first reader",
                    Context.of("badge", request.reader().badge()));
        }
        if (request.readAt().isBefore(firstRead.get())) {
            throw new ConflictError("a reread comes after the reading it revisits", Context.of("number", number));
        }
        requireReader(request.reader(), request.readAt());

        String was = describeIndications(
                row.getIndications().stream().map(WorkMapper::rowToIndication).toList());
        String became = describeIndications(request.indications());

        row.setRereadBy(request.reader().badge());
        row.setRereadAt(request.readAt());
        row.setRereadReason(request.reason());
        row.clearIndications();
        for (Indication indication : request.indications()) {
            row.addIndication(WorkMapper.indicationToRow(indication));
        }

        String badge = request.reader().badge();
        if (!was.equals(became)) {
            work.audit()
                    .changed(
                            request.readAt(),
                            badge,
                            "radiograph",
                            number,
                            "indications",
                            was,
                            became,
                            request.reason());
        } else {
            work.audit()
                    .append(new Entry(
                            request.readAt(),
                            badge,
                            Action.SIGNED,
                            "radiograph",
                            number,
                            "",
                            "",
                            "",
                            request.reason()));
        }
        work.flush();
    }

    /**
     * Every person who has read one film, in the order they read it.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a film that is not there
     */
    public static List<String> readersOf(UnitOfWork work, String number) {
        RadiographRow row = work.welds().radiograph(number);
        return Stream.of(row.getReadBy(), row.getRereadBy())
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The films on the job that two readers looked at, sorted, which are the ones a review asks
     * about.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static List<String> disputed(UnitOfWork work, JobNumber job) {
        JobRow jobRow = work.jobs().get(job);
        List<String> found = new ArrayList<>();
        for (WeldRow weldRow : jobRow.getWelds()) {
            for (RadiographRow radiograph : weldRow.getRadiographs()) {
                if (radiograph.getRereadAt().isPresent()) {
                    found.add(radiograph.getNumber());
                }
            }
        }
        return found.stream().sorted().toList();
    }

    /**
     * The disposition of the weld under the job's code, or nothing while the weld has no films or
     * any of them is unread or inadmissible. The films are put on the weld's record and judged there,
     * so the service and the record cannot disagree about when a weld is complete.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job or a weld that is not there
     */
    public static Optional<Verdict> weldVerdict(UnitOfWork work, JobNumber job, WeldId weld) {
        WeldRecord record = weldRecord(work, job, weld);
        if (record.radiographs().isEmpty()) {
            return Optional.empty();
        }
        return record.verdict(record.radiographs().size());
    }

    /**
     * Where a weld on a spot examined job stands under UW-52(d): the spot, the two tracers it sets
     * off when it fails, and the repair of the first.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job or a weld that is not there
     * @throws io.github.rkaushikdev.swagebay.ValidationError for a job that is not worked to spot
     *     radiography
     */
    public static SpotExamination.Standing spotStanding(UnitOfWork work, JobNumber job, WeldId weld) {
        return weldRecord(work, job, weld).spotStanding();
    }

    /** The weld and every film on it as the record the acceptance rules read. */
    private static WeldRecord weldRecord(UnitOfWork work, JobNumber job, WeldId weld) {
        JobRow jobRow = work.jobs().get(job);
        WeldRow weldRow = work.welds().get(jobRow, weld);
        WeldRecord record = new WeldRecord(
                job, weld, weldRow.getWeldLength(), weldRow.getWall(), Naming.codeNamed(jobRow.getAcceptanceCode()));
        weldRow.getRadiographs().stream().map(WorkMapper::rowToRadiograph).forEach(record::add);
        return record;
    }

    /**
     * Every film on the job still waiting for a reader, by number.
     *
     * @throws io.github.rkaushikdev.swagebay.NotFoundError for a job that is not there
     */
    public static List<String> readingsOutstanding(UnitOfWork work, JobNumber job) {
        JobRow jobRow = work.jobs().get(job);
        return work.welds().unread(jobRow).stream()
                .map(RadiographRow::getNumber)
                .toList();
    }

    /** The people on the crew who may read film that day, in the order given. */
    public static List<Person> readersAvailable(List<Person> crew, LocalDate day) {
        return crew.stream().filter(person -> person.mayInterpretOn(day)).toList();
    }

    /** What a reader wrote down, in one line, so the trail can hold it. */
    private static String describeIndications(List<Indication> found) {
        if (found.isEmpty()) {
            return NOTHING;
        }
        return String.join(
                "; ", found.stream().map(Indication::describe).sorted().toList());
    }

    private static void requireReader(Person reader, LocalDateTime at) {
        LocalDate day = at.toLocalDate();
        if (!reader.mayInterpretOn(day)) {
            throw new ConflictError(
                    "that reader may not interpret film on that day",
                    Context.of("badge", reader.badge(), "lapsed", String.join(", ", reader.lapsedOn(day))));
        }
    }
}
