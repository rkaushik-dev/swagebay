package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.prepared;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.persistence.models.StoredImageRow;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreRepositoryTest {

    private static final LocalDateTime CAPTURED = LocalDateTime.of(2024, 9, 24, 23, 40);
    private static final LocalDateTime INGESTED = LocalDateTime.of(2024, 9, 25, 7, 0);
    private static final String HASH = ImageStore.digestOf("plate one".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = ImageStore.digestOf("plate two".getBytes(StandardCharsets.UTF_8));

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
