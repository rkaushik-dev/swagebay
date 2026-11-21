package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.testing.PeopleFixtures.aReader;
import static io.github.rkaushikdev.swagebay.testing.ServiceFixtures.prepared;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.nonconformance.Disposition;
import io.github.rkaushikdev.swagebay.nonconformance.Report;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures.Prepared;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloseoutTest {

    private static final LocalDateTime READ = LocalDateTime.of(2024, 9, 25, 8, 0);
    private static final LocalDate CLOSED = LocalDate.of(2024, 9, 30);
    private static final LocalDate RAISED = LocalDate.of(2024, 9, 26);
    private static final String HASH = ImageStore.digestOf("plate one".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = ImageStore.digestOf("plate two".getBytes(StandardCharsets.UTF_8));

    private static Report aReport(String job) {
        return new Report("NCR-1", job, "GW-104", "API 1104 9.3.8", "elongated slag", RAISED, "0142");
    }

    private static Report aReport() {
        return aReport("24-0001");
    }

    private static StoredImage anImage(
            String number, int revision, ImageFormat imageFormat, Purpose purpose, int bits) {
        return new StoredImage(
                number,
                ImageStore.expectedName(number, imageFormat, revision),
                imageFormat,
                bits,
                2048,
                512,
                512,
                HASH,
                READ,
                purpose);
    }

    private static StoredImage anImage(String number) {
        return anImage(number, 0, ImageFormat.DICONDE, Purpose.RECORD, 16);
    }

    private static Prepared finished(UnitOfWork work) {
        Prepared shot = prepared(work);
        ReadingService.recordReading(work, new ReadingRecord(shot.numbers().get(0), aReader(), READ));
        return shot;
    }

    @Test
    void aFinishedJobIsClear() {
        Checklist found = TestDatabase.fresh()
                .transactionResult(
                        work -> CloseoutService.checklist(work, finished(work).job()));
        assertThat(found.isClear()).isTrue();
        assertThat(found.describe()).contains("ready to close");
    }

    @Test
    void anUnreadFilmShowsUpAsWork() {
        Checklist found = TestDatabase.fresh()
                .transactionResult(
                        work -> CloseoutService.checklist(work, prepared(work).job()));
        assertThat(found.workReasons()).anyMatch(reason -> reason.contains("has not been read"));
        assertThat(found.reportReasons()).isEmpty();
    }

    @Test
    void anOpenReportShowsUpApartFromTheWork() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.nonconformances().raiseReport(aReport());
            return CloseoutService.checklist(work, shot.job());
        });
        assertThat(found.workReasons()).isEmpty();
        assertThat(found.reportReasons()).containsExactly("NCR-1 is still open");
        assertThat(found.isClear()).isFalse();
    }

    @Test
    void aClosedReportDoesNotBlock() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            work.nonconformances().raiseReport(aReport());
            work.nonconformances().decide("NCR-1", Disposition.REWORK);
            work.nonconformances().close("NCR-1", RAISED.plusDays(1), "0303");
            assertThat(CloseoutService.checklist(work, shot.job()).isClear()).isTrue();
        });
    }

    @Test
    void anotherJobsReportDoesNotBlockThisOne() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            work.nonconformances().raiseReport(aReport("24-0099"));
            assertThat(CloseoutService.checklist(work, shot.job()).isClear()).isTrue();
        });
    }

    @Test
    void aJobWithNoImagesIsNotAskedAboutImages() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            assertThat(CloseoutService.checklist(work, shot.job()).imageReasons())
                    .isEmpty();
        });
    }

    @Test
    void anEightBitRecordCopyShowsUpAsAnImageFault() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.store()
                    .ingest(anImage(shot.numbers().get(0), 0, ImageFormat.DICONDE, Purpose.RECORD, 8), READ, "0142");
            return CloseoutService.checklist(work, shot.job());
        });
        assertThat(found.imageReasons()).anyMatch(reason -> reason.contains("12 bit"));
    }

    @Test
    void anExposureWithOnlyAViewingCopyShowsUp() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.store().ingest(anImage(shot.numbers().get(0), 1, ImageFormat.JPEG, Purpose.VIEWING, 16), READ, "0142");
            return CloseoutService.checklist(work, shot.job());
        });
        assertThat(found.imageReasons()).anyMatch(reason -> reason.contains("no record copy"));
    }

    @Test
    void aFileMissingFromTheDiskShowsUpWhenTheDiskIsOffered() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.store().ingest(anImage(shot.numbers().get(0)), READ, "0142");
            return CloseoutService.checklist(work, shot.job(), Map.of());
        });
        assertThat(found.imageReasons()).anyMatch(reason -> reason.contains("is missing from the store"));
    }

    @Test
    void aChangedFileShowsUpAsChanged() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.store().ingest(anImage(shot.numbers().get(0)), READ, "0142");
            String name = ImageStore.expectedName(shot.numbers().get(0), ImageFormat.DICONDE);
            return CloseoutService.checklist(work, shot.job(), Map.of(name, OTHER));
        });
        assertThat(found.imageReasons()).anyMatch(reason -> reason.contains("has changed since ingest"));
    }

    @Test
    void aStrayFileShowsUpApartFromAMissingOne() {
        Checklist found = TestDatabase.fresh().transactionResult(work -> {
            Prepared shot = finished(work);
            work.store().ingest(anImage(shot.numbers().get(0)), READ, "0142");
            String name = ImageStore.expectedName(shot.numbers().get(0), ImageFormat.DICONDE);
            return CloseoutService.checklist(work, shot.job(), Map.of(name, HASH, "holiday.jpg", OTHER));
        });
        assertThat(found.imageReasons()).anyMatch(reason -> reason.contains("not in the manifest"));
    }

    @Test
    void aMatchingDiskLeavesTheJobClear() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            work.store().ingest(anImage(shot.numbers().get(0)), READ, "0142");
            String name = ImageStore.expectedName(shot.numbers().get(0), ImageFormat.DICONDE);
            assertThat(CloseoutService.checklist(work, shot.job(), Map.of(name, HASH))
                            .isClear())
                    .isTrue();
        });
    }

    @Test
    void closingRefusesOnAnOpenReportAndNamesIt() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            work.nonconformances().raiseReport(aReport());
            assertThatThrownBy(() -> JobService.closeJob(work, shot.job(), CLOSED))
                    .isInstanceOfSatisfying(ConflictError.class, caught -> assertThat(
                                    (String) caught.context().get("reasons"))
                            .contains("NCR-1 is still open"));
        });
    }

    @Test
    void closingAClearJobWorks() {
        TestDatabase.fresh().transaction(work -> {
            Prepared shot = finished(work);
            JobService.closeJob(work, shot.job(), CLOSED);
            assertThat(work.jobs().get(shot.job()).getClosedOn()).contains(CLOSED);
        });
    }
}
