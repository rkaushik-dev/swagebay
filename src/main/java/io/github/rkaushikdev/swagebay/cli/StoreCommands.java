package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.Discrepancy;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.ImageStore;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Image store commands, run from the van as often as from the office.
 *
 * <p>Ingest takes a real path because the digest has to be taken off the bytes that are actually on
 * the disk. Anything that let a caller pass a digest in would be a checker that trusts the thing it
 * is checking.
 *
 * <p>Verify is the reason this exists. It walks a directory, hashes what it finds, and prints the
 * three faults apart: gone, unexpected, and changed. It exits non zero when the store does not
 * match, so it can be the last line of a copy script and stop a truck leaving with a bad card.
 */
@Command(
        name = "store",
        description = "Ingest, list and verify stored images.",
        subcommands = {
            StoreCommands.IngestCommand.class,
            StoreCommands.ListCommand.class,
            StoreCommands.VerifyCommand.class
        })
final class StoreCommands extends Group {

    /** A panel image is not small, so a file is hashed a megabyte at a time. */
    private static final int CHUNK = 1024 * 1024;

    /** The digest of a file's bytes and how many there were. */
    private record Digested(String digest, long size) {}

    private static Digested digestOfFile(Path path) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java platform provides SHA-256", missing);
        }
        long size = 0;
        byte[] block = new byte[CHUNK];
        try (InputStream handle = Files.newInputStream(path)) {
            for (int read = handle.read(block); read >= 0; read = handle.read(block)) {
                size += read;
                digest.update(block, 0, read);
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return new Digested(HexFormat.of().formatHex(digest.digest()), size);
    }

    /**
     * The format a file's suffix names.
     *
     * @throws ValidationError for a suffix nothing here reads
     */
    private static ImageFormat formatFrom(Path path) {
        String suffix = suffixOf(path.getFileName().toString()).toLowerCase(Locale.ROOT);
        while (suffix.startsWith(".")) {
            suffix = suffix.substring(1);
        }
        for (ImageFormat candidate : ImageFormat.values()) {
            if (candidate.suffix().equals(suffix)) {
                return candidate;
            }
        }
        throw new ValidationError("nothing here reads that kind of file", Context.of("suffix", suffix));
    }

    /** The part of a name from its last dot, when the dot neither starts nor ends it; else nothing. */
    private static String suffixOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : "";
    }

    /** Take a file into the store under the name its exposure gives it. */
    @Command(name = "ingest", description = "Take a file into the store under the name its exposure gives it.")
    static final class IngestCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "RADIOGRAPH")
        private String radiograph;

        @Parameters(index = "1", paramLabel = "FILE", converter = ExistingPaths.FileOnly.class)
        private Path path;

        @Option(names = "--width", paramLabel = "INTEGER RANGE", required = true, converter = Counts.Positive.class)
        private int width;

        @Option(names = "--height", paramLabel = "INTEGER RANGE", required = true, converter = Counts.Positive.class)
        private int height;

        @Option(
                names = "--bits",
                paramLabel = "INTEGER RANGE",
                defaultValue = "16",
                showDefaultValue = Visibility.ALWAYS,
                converter = Counts.Positive.class)
        private int bits;

        @Option(
                names = "--revision",
                paramLabel = "INTEGER RANGE",
                defaultValue = "0",
                showDefaultValue = Visibility.ALWAYS,
                converter = Counts.NotNegative.class)
        private int revision;

        @Option(names = "--viewing", description = "a viewing copy, not the record")
        private boolean viewing;

        @Option(names = "--by", paramLabel = "TEXT", required = true)
        private String badge;

        @Override
        public Integer call() {
            ImageFormat imageFormat = formatFrom(path);
            Digested digested = digestOfFile(path);
            StoredImage image = new StoredImage(
                    radiograph,
                    ImageStore.expectedName(radiograph, imageFormat, revision),
                    imageFormat,
                    bits,
                    width,
                    height,
                    digested.size(),
                    digested.digest(),
                    modifiedAt(path),
                    viewing ? Purpose.VIEWING : Purpose.RECORD);
            List<String> wrong = ImageStore.faults(image);
            inWork(work -> work.store().ingest(image, LocalDateTime.now(), badge));
            out().println("stored " + image.filename() + " " + digested.size() + " bytes "
                    + digested.digest().substring(0, 12));
            if (!wrong.isEmpty()) {
                out().println(Render.bullets(wrong));
            }
            return Main.EXIT_OK;
        }

        /** When the file was last written, on this machine's clock, to the microsecond. */
        private static LocalDateTime modifiedAt(Path path) {
            try {
                return LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault())
                        .truncatedTo(ChronoUnit.MICROS);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }

    /** Every file a job has produced. */
    @Command(name = "list", description = "Every file a job has produced.")
    static final class ListCommand extends Leaf {

        /** The manifest's lines, and the exposures with no record copy among them. */
        private record Listing(List<List<String>> rows, List<String> without) {}

        @Parameters(paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            Listing listing = fromWork(work -> {
                Manifest manifest = work.store().manifestFor(JobNumber.parse(number));
                List<List<String>> rows = new TreeMap<>(manifest.entries())
                        .values().stream()
                                .map(image -> List.of(
                                        image.filename(),
                                        image.radiographNumber(),
                                        image.imageFormat().toString(),
                                        image.purpose().toString(),
                                        String.valueOf(image.bitDepth()),
                                        String.valueOf(image.byteSize()),
                                        image.digest().substring(0, 12)))
                                .toList();
                return new Listing(rows, manifest.radiographsWithoutARecordCopy());
            });
            if (listing.rows().isEmpty()) {
                out().println("no files stored against " + number);
                return Main.EXIT_OK;
            }
            out().println(Render.table(
                    List.of("file", "radiograph", "format", "purpose", "bits", "bytes", "sha256"),
                    listing.rows(),
                    List.of(4, 5)));
            if (!listing.without().isEmpty()) {
                out().println(Render.heading("no record copy"));
                out().println(Render.bullets(listing.without()));
            }
            return Main.EXIT_OK;
        }
    }

    /** Hash what is on the disk and compare it to the manifest. */
    @Command(name = "verify", description = "Hash what is on the disk and compare it to the manifest.")
    static final class VerifyCommand extends Leaf {

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "DIRECTORY", converter = ExistingPaths.DirectoryOnly.class)
        private Path directory;

        @Override
        public Integer call() {
            Map<String, String> seen = new LinkedHashMap<>();
            for (Path path : filesIn(directory)) {
                seen.put(path.getFileName().toString(), digestOfFile(path).digest());
            }
            Manifest manifest = fromWork(work -> work.store().manifestFor(JobNumber.parse(number)));
            Discrepancy found = manifest.verify(seen);
            out().println(manifest.size() + " in the manifest, " + seen.size() + " on the disk");
            return Gates.report(
                    out(),
                    found.describe(),
                    List.of(
                            Map.entry("missing", found.missing()),
                            Map.entry("not in the manifest", found.unexpected()),
                            Map.entry("changed since ingest", found.altered())),
                    found.describe());
        }

        /** The regular files directly in {@code directory}, by name. */
        private static List<Path> filesIn(Path directory) {
            try (Stream<Path> listed = Files.list(directory)) {
                return listed.sorted().filter(Files::isRegularFile).toList();
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
