package io.github.rkaushikdev.swagebay.imaging;

import static io.github.rkaushikdev.swagebay.imaging.ImageStore.digestOf;
import static io.github.rkaushikdev.swagebay.imaging.ImageStore.expectedName;
import static io.github.rkaushikdev.swagebay.imaging.ImageStore.faults;
import static io.github.rkaushikdev.swagebay.imaging.ImageStore.isAdmissibleFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.ValidationError;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageStoreTest {

    private static final LocalDateTime CAPTURED = LocalDateTime.of(2025, 5, 27, 10, 0);
    private static final byte[] BYTES = "not really a radiograph".getBytes(StandardCharsets.US_ASCII);
    private static final String HASH = digestOf(BYTES);
    private static final String OTHER = digestOf("different bytes entirely".getBytes(StandardCharsets.US_ASCII));

    /** A stored file with the named parts replaced, the way a test varies one thing at a time. */
    private record Stored(
            String number, ImageFormat imageFormat, int bitDepth, int revision, Purpose purpose, String digest) {

        static Stored sound() {
            return new Stored("24-0001-R0001", ImageFormat.DICONDE, 16, 0, Purpose.RECORD, HASH);
        }

        Stored number(String value) {
            return new Stored(value, imageFormat, bitDepth, revision, purpose, digest);
        }

        Stored imageFormat(ImageFormat value) {
            return new Stored(number, value, bitDepth, revision, purpose, digest);
        }

        Stored bitDepth(int value) {
            return new Stored(number, imageFormat, value, revision, purpose, digest);
        }

        Stored revision(int value) {
            return new Stored(number, imageFormat, bitDepth, value, purpose, digest);
        }

        Stored purpose(Purpose value) {
            return new Stored(number, imageFormat, bitDepth, revision, value, digest);
        }

        Stored digest(String value) {
            return new Stored(number, imageFormat, bitDepth, revision, purpose, value);
        }

        StoredImage image() {
            return new StoredImage(
                    number,
                    expectedName(number, imageFormat, revision),
                    imageFormat,
                    bitDepth,
                    2048,
                    512,
                    BYTES.length,
                    digest,
                    CAPTURED,
                    purpose);
        }
    }

    private static StoredImage stored() {
        return Stored.sound().image();
    }

    private static Manifest manifest() {
        return new Manifest("24-0001");
    }

    @Test
    void theNameComesFromTheRadiographNumber() {
        assertThat(expectedName("24-0001-R0001", ImageFormat.TIFF)).isEqualTo("24-0001-R0001.tif");
    }

    @Test
    void aLowerCaseNumberStillNamesTheSameFile() {
        assertThat(expectedName("24-0001-r0001", ImageFormat.TIFF)).isEqualTo("24-0001-R0001.tif");
    }

    @Test
    void aRescanIsARevisionNotANewNumber() {
        assertThat(expectedName("24-0001-R0001", ImageFormat.TIFF, 2)).isEqualTo("24-0001-R0001-v2.tif");
    }

    @Test
    void aNameWithNoRadiographBehindItIsRefused() {
        assertThatThrownBy(() -> expectedName("   ", ImageFormat.TIFF)).isInstanceOf(ValidationError.class);
    }

    @Test
    void aNegativeRevisionIsRefused() {
        assertThatThrownBy(() -> expectedName("24-0001-R0001", ImageFormat.TIFF, -1))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void aFileNamedSomethingNobodyCanDeriveIsRefused() {
        assertThatThrownBy(() -> new StoredImage(
                        "24-0001-R0001", "scan_final_FINAL.tif", ImageFormat.TIFF, 16, 100, 100, 10, HASH, CAPTURED))
                .isInstanceOfSatisfying(ValidationError.class, caught -> assertThat(caught.context())
                        .containsEntry("filename", "scan_final_FINAL.tif"));
    }

    @Test
    void aDigestThatIsNotADigestIsRefused() {
        assertThatThrownBy(() -> Stored.sound().digest("deadbeef").image()).isInstanceOf(ValidationError.class);
    }

    @Test
    void aBitDepthNobodyShipsIsRefused() {
        assertThatThrownBy(() -> Stored.sound().bitDepth(10).image()).isInstanceOf(ValidationError.class);
    }

    @Test
    void anEmptyFileIsNotAnImage() {
        assertThatThrownBy(() -> new StoredImage(
                        "24-0001-R0001", "24-0001-R0001.tif", ImageFormat.TIFF, 16, 10, 10, 0, HASH, CAPTURED))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void anImageWithNoHeightIsRefused() {
        assertThatThrownBy(() -> new StoredImage(
                        "24-0001-R0001", "24-0001-R0001.tif", ImageFormat.TIFF, 16, 10, 0, 10, HASH, CAPTURED))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void theRevisionReadsBackOffTheName() {
        assertThat(Stored.sound().revision(3).image().revision()).isEqualTo(3);
        assertThat(stored().revision()).isZero();
    }

    @Test
    void aLosslessRecordCopyHasNothingWrongWithIt() {
        assertThat(faults(stored())).isEmpty();
        assertThat(isAdmissibleFile(stored())).isTrue();
    }

    @Test
    void aJpegCannotBeTheRecord() {
        List<String> reasons =
                faults(Stored.sound().imageFormat(ImageFormat.JPEG).bitDepth(8).image());
        assertThat(reasons).anyMatch(reason -> reason.contains("lossy"));
    }

    @Test
    void eightBitIsBelowWhatAnEvaluationIsMadeAt() {
        List<String> reasons = faults(Stored.sound().bitDepth(8).image());
        assertThat(reasons).anyMatch(reason -> reason.contains("12 bit"));
    }

    @Test
    void aViewingCopyThatClaimsToBeTheFirstFileIsWrong() {
        List<String> reasons =
                faults(Stored.sound().purpose(Purpose.VIEWING).revision(0).image());
        assertThat(reasons).anyMatch(reason -> reason.contains("viewing copy"));
    }

    @Test
    void aViewingCopyAsARevisionIsFineEvenLossy() {
        StoredImage image = Stored.sound()
                .imageFormat(ImageFormat.JPEG)
                .bitDepth(8)
                .revision(1)
                .purpose(Purpose.VIEWING)
                .image();
        assertThat(faults(image)).isEmpty();
    }

    @Test
    void onlyDicondeHasSomewhereToPutTheTechnique() {
        assertThat(ImageFormat.DICONDE.carriesTechnique()).isTrue();
        assertThat(ImageFormat.TIFF.carriesTechnique()).isFalse();
    }

    @Test
    void pixelsAreTheTwoDimensions() {
        assertThat(stored().pixels()).isEqualTo(2048L * 512);
    }

    @Test
    void aManifestHoldsWhatWasAdded() {
        Manifest manifest = manifest();
        manifest.add(stored());
        assertThat(manifest.size()).isEqualTo(1);
        assertThat(manifest.contains("24-0001-R0001.dcm")).isTrue();
    }

    @Test
    void addingTheSameFileTwiceIsNotAProblem() {
        Manifest manifest = manifest();
        manifest.add(stored());
        manifest.add(stored());
        assertThat(manifest.size()).isEqualTo(1);
    }

    @Test
    void theSameNameAgainstDifferentBytesIsRefused() {
        Manifest manifest = manifest();
        manifest.add(stored());
        assertThatThrownBy(() -> manifest.add(Stored.sound().digest(OTHER).image()))
                .isInstanceOf(ValidationError.class);
    }

    @Test
    void revisionsOfOneExposureComeBackInOrder() {
        Manifest manifest = manifest();
        manifest.add(Stored.sound().revision(2).purpose(Purpose.VIEWING).image());
        manifest.add(stored());
        assertThat(manifest.forRadiograph("24-0001-R0001"))
                .extracting(StoredImage::revision)
                .containsExactly(0, 2);
    }

    @Test
    void anotherExposureIsNotInThisOnesList() {
        Manifest manifest = manifest();
        manifest.add(stored());
        manifest.add(Stored.sound().number("24-0001-R0002").image());
        assertThat(manifest.forRadiograph("24-0001-R0001")).hasSize(1);
    }

    @Test
    void theRecordCopyIsTheOneThatIsNotAViewingCopy() {
        Manifest manifest = manifest();
        manifest.add(Stored.sound().revision(1).purpose(Purpose.VIEWING).image());
        manifest.add(stored());
        assertThat(manifest.recordCopy("24-0001-R0001").orElseThrow().revision())
                .isZero();
    }

    @Test
    void anExposureWithOnlyViewingCopiesHasNoRecord() {
        Manifest manifest = manifest();
        manifest.add(Stored.sound().revision(1).purpose(Purpose.VIEWING).image());
        assertThat(manifest.recordCopy("24-0001-R0001")).isEmpty();
        assertThat(manifest.radiographsWithoutARecordCopy()).containsExactly("24-0001-R0001");
    }

    @Test
    void aStoreThatMatchesTheManifestIsClean() {
        Manifest manifest = manifest();
        manifest.add(stored());
        Discrepancy found = manifest.verify(Map.of("24-0001-R0001.dcm", HASH));
        assertThat(found.isClean()).isTrue();
        assertThat(found.describe()).contains("where it was");
    }

    @Test
    void aFileThatIsGoneReadsAsMissing() {
        Manifest manifest = manifest();
        manifest.add(stored());
        Discrepancy found = manifest.verify(Map.of());
        assertThat(found.missing()).containsExactly("24-0001-R0001.dcm");
        assertThat(found.unexpected()).isEmpty();
        assertThat(found.altered()).isEmpty();
    }

    @Test
    void aFileNobodyExpectedIsNotAMissingFile() {
        Manifest manifest = manifest();
        manifest.add(stored());
        Discrepancy found = manifest.verify(Map.of("24-0001-R0001.dcm", HASH, "holiday.jpg", OTHER));
        assertThat(found.unexpected()).containsExactly("holiday.jpg");
        assertThat(found.missing()).isEmpty();
    }

    @Test
    void aFileResavedThroughAViewerReadsAsAltered() {
        Manifest manifest = manifest();
        manifest.add(stored());
        Discrepancy found = manifest.verify(Map.of("24-0001-R0001.dcm", OTHER));
        assertThat(found.altered()).containsExactly("24-0001-R0001.dcm");
        assertThat(found.missing()).isEmpty();
    }

    @Test
    void theThreeCausesAreCountedApart() {
        Manifest manifest = manifest();
        manifest.add(stored());
        manifest.add(Stored.sound().number("24-0001-R0002").image());
        Discrepancy found = manifest.verify(Map.of("24-0001-R0002.dcm", OTHER, "stray.tif", HASH));
        assertThat(found.missing()).containsExactly("24-0001-R0001.dcm");
        assertThat(found.altered()).containsExactly("24-0001-R0002.dcm");
        assertThat(found.unexpected()).containsExactly("stray.tif");
        assertThat(found.isClean()).isFalse();
        assertThat(found.describe()).isEqualTo("1 missing, 1 not in the manifest, 1 changed since ingest");
    }

    @Test
    void manifestFaultsNameTheFileTheyAreAbout() {
        Manifest manifest = manifest();
        manifest.add(Stored.sound().bitDepth(8).image());
        assertThat(manifest.faults().get(0)).startsWith("24-0001-R0001.dcm: 8 bit");
    }

    @Test
    void aCleanManifestReportsNoFaults() {
        Manifest manifest = manifest();
        manifest.add(stored());
        assertThat(manifest.faults()).isEmpty();
    }

    @Test
    void theManifestAddsUpWhatTheStoreHolds() {
        Manifest manifest = manifest();
        manifest.add(stored());
        manifest.add(Stored.sound().number("24-0001-R0002").image());
        assertThat(manifest.totalBytes()).isEqualTo(2L * BYTES.length);
    }

    @Test
    void theSameBytesAlwaysHashTheSameWay() {
        byte[] longer = "not really a radiograph ".getBytes(StandardCharsets.US_ASCII);
        assertThat(digestOf(BYTES)).isEqualTo(digestOf(BYTES));
        assertThat(digestOf(BYTES)).isNotEqualTo(digestOf(longer));
    }
}
