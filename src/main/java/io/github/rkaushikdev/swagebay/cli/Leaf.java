package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import java.io.PrintWriter;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * A command at the end of the line, which does one thing and answers its exit status.
 *
 * <p>It reaches the root for the terminal and the database, so every command prints to the same
 * place and works against the database {@code --database} named.
 */
abstract class Leaf implements Subcommand {

    @Spec
    private CommandSpec spec;

    /** The root of the command line this command was given on. */
    final Main main() {
        return (Main) spec.root().userObject();
    }

    /** Where the command prints what it found or did. */
    final PrintWriter out() {
        return main().terminal().out();
    }

    /** Run {@code body} in one unit of work, committed when it returns. */
    final void inWork(Consumer<? super UnitOfWork> body) {
        main().database().transaction(body);
    }

    /** {@link #inWork(Consumer)} for a body with an answer, returned once the work is committed. */
    final <T> T fromWork(Function<? super UnitOfWork, ? extends T> body) {
        return main().database().transactionResult(body);
    }

    /**
     * {@link #fromWork(Function)} against the database opened for reading only, for a command that
     * promises to change nothing.
     */
    final <T> T fromReading(Function<? super UnitOfWork, ? extends T> body) {
        return main().readingDatabase().transactionResult(body);
    }

    /** A usage error against this command, which leaves with {@link Main#EXIT_VALIDATION}. */
    final ParameterException misused(String message) {
        return new ParameterException(spec.commandLine(), message);
    }
}
