package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.geometry.PipeSchedule;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.materials.Materials;
import io.github.rkaushikdev.swagebay.persistence.models.JobRow;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.StoredImageRow;
import io.github.rkaushikdev.swagebay.persistence.models.WeldRow;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreRepositoryTest {

    private static final LocalDate OPENED = LocalDate.of(2024, 9, 23);
    private static final LocalDateTime SHOT = LocalDateTime.of(2024, 9, 24, 23, 30);
    private static final LocalDateTime CAPTURED = LocalDateTime.of(2024, 9, 24, 23, 40);
    private static final LocalDateTime INGESTED = LocalDateTime.of(2024, 9, 25, 7, 0);
    private static final WeldId WELD = WeldId.parse("GW-104");
    private static final String HASH = ImageStore.digestOf("plate one".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = ImageStore.digestOf("plate two".getBytes(StandardCharsets.UTF_8));

    /** A job, what was shot on it, and the numbers the shots were given. */
    private record Prepared(JobNumber job, List<String> numbers) {}

    /**
     * A job opened on the 23rd of September 2024, weld GW-104 on it, and {@code shots} exposures of
     * that weld, numbered from the job's radiograph counter in this unit of work: the rows a job,
     * a weld and recorded shots leave behind.
     */
    private static Prepared prepared(UnitOfWork work, int shots) {
        int year = OPENED.getYear() % 100;
        JobNumber number = new JobNumber(year, work.jobs().nextSerial(year));
        JobRow job = new JobRow();
        job.setYear(number.year());
        job.setSerial(number.serial());
        job.setClient("c");
        job.setSite("s");
        job.setAcceptanceCode("API 1104");
        job.setProcedureNumber("RT-001");
        job.setOpenedOn(OPENED);
        WeldRow weld =
                WorkMapper.jointToRow(PipeSchedule.girthWeld("6", "80", Materials.STEEL, mm(2)), WELD, new WeldRow());
        weld.setWeldLength(mm(529));
        weld.setWelder("w-19");
        weld.setWeldedOn(OPENED);
        job.addWeld(weld);
        work.jobs().add(job);
        List<String> numbers = new ArrayList<>();
        for (int index = 1; index <= shots; index++) {
            RadiographRow film = new RadiographRow();
            film.setNumber(work.numbering().takeRadiographNumber(number.toString()));
            film.setExposureIndex(index);
            film.setExposedAt(SHOT);
            film.setTechniqueReference("RT-001 rev 1");
            film.setMedium("film");
            film.setAdmissible(true);
            weld.addRadiograph(film);
            work.flush();
            numbers.add(film.getNumber());
        }
        return new Prepared(number, List.copyOf(numbers));
    }

    private static Prepared prepared(UnitOfWork work) {
        return prepared(work, 1);
    }

    private static StoredImage anImage(
            String number, int revision, ImageFormat imageFormat, Purpose purpose, String digest) {
        return new StoredImage(
                number,
                ImageStore.expectedName(number, imageFormat, revision),
                imageFormat,
                16,
                2048,
                512,
                512,
                digest,
                CAPTURED,
                purpose);
    }

    private static StoredImage anImage(String number) {
        return anImage(number, 0, ImageFormat.DICONDE, Purpose.RECORD, HASH);
    }

    private static StoredImage aViewingCopy(String number) {
        return anImage(number, 1, ImageFormat.JPEG, Purpose.VIEWING, HASH);
    }

    @Test
    void anIngestedFileComesBackAgainstItsExposure() {
        List<StoredImage> found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = prepared(work);
            work.store().ingest(anImage(shot.numbers().get(0)), INGESTED, "0142");
            return work.store().forRadiograph(shot.numbers().get(0));
        });
        assertThat(found).hasSize(1);
        assertThat(found.get(0).digest()).isEqualTo(HASH);
    }

    @Test
    void theSameFileIngestedTwiceLandsOnce() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            work.store().ingest(anImage(shot.numbers().get(0)), INGESTED, "0142");
            work.store().ingest(anImage(shot.numbers().get(0)), INGESTED, "0142");
            assertThat(work.store().forRadiograph(shot.numbers().get(0))).hasSize(1);
        });
    }

    @Test
    void theSameNameAgainstDifferentBytesIsAConflict() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            String number = shot.numbers().get(0);
            work.store().ingest(anImage(number), INGESTED, "0142");
            assertThatThrownBy(() -> work.store()
                            .ingest(anImage(number, 0, ImageFormat.DICONDE, Purpose.RECORD, OTHER), INGESTED, "0142"))
                    .isInstanceOf(ConflictError.class);
        });
    }

    @Test
    void aFileAgainstAnExposureNobodyShotIsRefused() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work);
            assertThatThrownBy(() -> work.store().ingest(anImage("24-0001-R9999"), INGESTED, "0142"))
                    .isInstanceOf(NotFoundError.class);
        });
    }

    @Test
    void revisionsComeBackInOrder() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            String number = shot.numbers().get(0);
            work.store().ingest(aViewingCopy(number), INGESTED, "0142");
            work.store().ingest(anImage(number), INGESTED, "0142");
            assertThat(work.store().forRadiograph(number))
                    .extracting(StoredImage::revision)
                    .containsExactly(0, 1);
        });
    }

    @Test
    void whoIngestedItAndWhenIsOnTheRow() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            StoredImageRow row = work.store().ingest(anImage(shot.numbers().get(0)), INGESTED, "0142");
            work.flush();
            assertThat(row.getIngestedBy()).isEqualTo("0142");
            assertThat(row.getIngestedAt()).isEqualTo(INGESTED);
        });
    }

    @Test
    void aFileTheStoreDoesNotHoldIsANotFound() {
        TestDatabase.fresh().transaction(work -> {
            prepared(work);
            assertThat(work.store().find("nothing.tif")).isEmpty();
            assertThatThrownBy(() -> work.store().get("nothing.tif")).isInstanceOf(NotFoundError.class);
        });
    }

    @Test
    void theManifestGathersEveryFileOnTheJob() {
        Manifest manifest = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = prepared(work, 2);
            for (String number : shot.numbers()) {
                work.store().ingest(anImage(number), INGESTED, "0142");
            }
            return work.store().manifestFor(shot.job());
        });
        assertThat(manifest.size()).isEqualTo(2);
        assertThat(manifest.job()).isEqualTo("24-0001");
    }

    @Test
    void aJobWithNoFilesHasAnEmptyManifest() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            assertThat(work.store().manifestFor(shot.job()).size()).isZero();
        });
    }

    @Test
    void anotherJobsFilesAreNotInThisManifest() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            work.store().ingest(anImage(shot.numbers().get(0)), INGESTED, "0142");
            assertThat(work.store().manifestFor(new JobNumber(24, 99)).size()).isZero();
        });
    }

    @Test
    void theManifestVerifiesAgainstWhatIsOnTheDisk() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            String number = shot.numbers().get(0);
            work.store().ingest(anImage(number), INGESTED, "0142");
            Manifest manifest = work.store().manifestFor(shot.job());
            String name = ImageStore.expectedName(number, ImageFormat.DICONDE);
            assertThat(manifest.verify(Map.of(name, HASH)).isClean()).isTrue();
            assertThat(manifest.verify(Map.of(name, OTHER)).altered()).containsExactly(name);
        });
    }

    @Test
    void theManifestFindsAnExposureWithNoRecordCopy() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = prepared(work);
            work.store().ingest(aViewingCopy(shot.numbers().get(0)), INGESTED, "0142");
            assertThat(work.store().manifestFor(shot.job()).radiographsWithoutARecordCopy())
                    .containsExactly(shot.numbers().get(0));
        });
    }

    @Test
    void theFilesSurviveTheTransactionThatIngestedThem() {
        Database made = TestDatabase.fresh();
        Prepared shot = made.transactionResult(work -> {
            Prepared prepared = prepared(work);
            work.store().ingest(anImage(prepared.numbers().get(0)), INGESTED, "0142");
            return prepared;
        });
        made.transaction(work ->
                assertThat(work.store().forRadiograph(shot.numbers().get(0))).hasSize(1));
    }
}
