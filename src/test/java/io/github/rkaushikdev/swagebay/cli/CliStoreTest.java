package io.github.rkaushikdev.swagebay.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.testing.ServiceFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliStoreTest {

    private static final byte[] PLATE = "a plate image, near enough for a hash".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OTHER = "a different plate image entirely".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tmp;

    private Shell shop;
    private List<String> numbers;

    @BeforeEach
    void aJobWithTwoShots() {
        String url = Shell.fileUrl(tmp, "shop");
        try (Database database = Database.open(url)) {
            database.migrate();
            numbers = database.transactionResult(work -> ServiceFixtures.prepared(work, 2))
                    .numbers();
        }
        shop = Shell.against(url);
    }

    private Path aFile(String name, byte[] body) throws IOException {
        return Files.write(tmp.resolve(name), body);
    }

    private Path aFile(String name) throws IOException {
        return aFile(name, PLATE);
    }

    private Path card() throws IOException {
        return Files.createDirectory(tmp.resolve("card"));
    }

    private Shell.Result ingest(String number, Path path, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "store", "ingest", number, path.toString(), "--width", "2048", "--height", "512", "--by", "0142"));
        args.addAll(List.of(extra));
        return shop.run(args.toArray(String[]::new));
    }

    @Test
    void aFileIsTakenInUnderTheNameItsExposureGivesIt() throws IOException {
        Shell.Result result = ingest(numbers.get(0), aFile("scan.dcm"));
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("stored " + numbers.get(0) + ".dcm");
    }

    @Test
    void theDigestPrintedIsTheDigestOfTheBytes() throws IOException {
        Shell.Result result = ingest(numbers.get(0), aFile("scan.dcm"));
        assertThat(result.out()).contains(ImageStore.digestOf(PLATE).substring(0, 12));
    }

    @Test
    void aFileTypeNothingReadsIsRefused() throws IOException {
        Shell.Result result = ingest(numbers.get(0), aFile("scan.raw"));
        assertThat(result.code()).isEqualTo(Main.EXIT_VALIDATION);
        assertThat(result.failure()).isEqualTo(ValidationError.CODE);
    }

    @Test
    void anEightBitRecordCopyIsStoredAndComplainedAbout() throws IOException {
        Shell.Result result = ingest(numbers.get(0), aFile("scan.dcm"), "--bits", "8");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("12 bit");
    }

    @Test
    void aViewingCopyGoesInAsARevision() throws IOException {
        ingest(numbers.get(0), aFile("scan.dcm"));
        Shell.Result result =
                ingest(numbers.get(0), aFile("view.jpg", OTHER), "--viewing", "--revision", "1", "--bits", "8");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains(numbers.get(0) + "-v1.jpg");
    }

    @Test
    void aJobWithNothingStoredSaysSo() {
        assertThat(shop.run("store", "list", "24-0001").out()).contains("no files stored");
    }

    @Test
    void theListingNamesEveryFile() throws IOException {
        ingest(numbers.get(0), aFile("scan.dcm"));
        ingest(numbers.get(1), aFile("second.dcm", OTHER));
        String output = shop.run("store", "list", "24-0001").out();
        assertThat(output).contains(numbers.get(0) + ".dcm", numbers.get(1) + ".dcm");
    }

    @Test
    void theListingCallsOutAnExposureWithNoRecordCopy() throws IOException {
        ingest(numbers.get(0), aFile("view.jpg"), "--viewing", "--revision", "1", "--bits", "8");
        assertThat(shop.run("store", "list", "24-0001").out()).contains("no record copy");
    }

    @Test
    void aStoreThatMatchesTheDiskVerifies() throws IOException {
        Path holding = card();
        ingest(numbers.get(0), aFile("scan.dcm"));
        Files.write(holding.resolve(numbers.get(0) + ".dcm"), PLATE);
        Shell.Result result = shop.run("store", "verify", "24-0001", holding.toString());
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("where it was");
    }

    @Test
    void aMissingFileFailsVerification() throws IOException {
        Path holding = card();
        ingest(numbers.get(0), aFile("scan.dcm"));
        Shell.Result result = shop.run("store", "verify", "24-0001", holding.toString());
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("1 missing");
    }

    @Test
    void aChangedFileFailsVerificationAndSaysSo() throws IOException {
        Path holding = card();
        ingest(numbers.get(0), aFile("scan.dcm"));
        Files.write(holding.resolve(numbers.get(0) + ".dcm"), OTHER);
        Shell.Result result = shop.run("store", "verify", "24-0001", holding.toString());
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("changed since ingest");
    }

    @Test
    void aStrayFileOnTheCardIsReportedApart() throws IOException {
        Path holding = card();
        ingest(numbers.get(0), aFile("scan.dcm"));
        Files.write(holding.resolve(numbers.get(0) + ".dcm"), PLATE);
        Files.write(holding.resolve("holiday.jpg"), OTHER);
        Shell.Result result = shop.run("store", "verify", "24-0001", holding.toString());
        assertThat(result.code()).isEqualTo(4);
        assertThat(result.out()).contains("not in the manifest");
    }

    @Test
    void verifyingAJobWithNothingStoredAgainstAnEmptyCardPasses() throws IOException {
        Path holding = card();
        assertThat(shop.run("store", "verify", "24-0001", holding.toString()).code())
                .isZero();
    }

    @Test
    void ingestingTheSameBytesTwiceIsNotAnError() throws IOException {
        Path path = aFile("scan.dcm");
        ingest(numbers.get(0), path);
        assertThat(ingest(numbers.get(0), path).code()).isZero();
    }

    @Test
    void theSameNameAgainstDifferentBytesConflicts() throws IOException {
        ingest(numbers.get(0), aFile("scan.dcm"));
        Shell.Result result = ingest(numbers.get(0), aFile("again.dcm", OTHER));
        assertThat(result.code()).isEqualTo(Main.EXIT_CONFLICT);
        assertThat(result.failure()).isEqualTo(ConflictError.CODE);
    }
}
