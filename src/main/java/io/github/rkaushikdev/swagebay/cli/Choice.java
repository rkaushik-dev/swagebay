package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.util.Repr;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * A value that has to be one of a fixed list, exactly as written, which also serves as the list a
 * help page and a shell offer. Anything else is a usage error that names every value accepted.
 */
abstract class Choice implements ITypeConverter<String>, Iterable<String> {

    /** The accepted values, in the order they are offered. */
    abstract List<String> choices();

    @Override
    public final String convert(String value) {
        List<String> choices = choices();
        if (choices.contains(value)) {
            return value;
        }
        String listed = choices.stream().map(Repr::quote).collect(Collectors.joining(", "));
        String expected = choices.size() == 1 ? listed : "one of " + listed;
        throw new TypeConversionException(Repr.quote(value) + " is not " + expected + ".");
    }

    @Override
    public final Iterator<String> iterator() {
        return choices().iterator();
    }
}
