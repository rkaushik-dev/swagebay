package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Paths on the command line that have to be there already, and be the right kind of thing. Anything
 * else is a usage error naming the path, before the command touches the database.
 */
final class ExistingPaths {

    private ExistingPaths() {}

    /** A file that exists and is not a directory. */
    static final class FileOnly implements ITypeConverter<Path> {

        @Override
        public Path convert(String text) {
            Path path = Path.of(text);
            if (!Files.exists(path)) {
                throw new TypeConversionException("File " + Repr.quote(text) + " does not exist.");
            }
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File '" + text + "' is a directory.");
            }
            return path;
        }
    }

    /** A directory that exists. */
    static final class DirectoryOnly implements ITypeConverter<Path> {

        @Override
        public Path convert(String text) {
            Path path = Path.of(text);
            if (!Files.exists(path)) {
                throw new TypeConversionException("Directory " + Repr.quote(text) + " does not exist.");
            }
            if (!Files.isDirectory(path)) {
                throw new TypeConversionException("Directory '" + text + "' is a file.");
            }
            return path;
        }
    }
}
