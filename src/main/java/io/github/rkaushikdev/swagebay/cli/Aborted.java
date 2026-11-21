package io.github.rkaushikdev.swagebay.cli;

/**
 * The operator stopped a command before it finished, at the keyboard or by walking away from it.
 *
 * <p>Not a failure of the command and not a refusal by the domain, so it carries no code. The entry
 * point says {@code aborted} on standard error and leaves with {@link Main#EXIT_OTHER}, rather than
 * printing a stack trace at somebody who only wanted to stop.
 */
public final class Aborted extends RuntimeException {

    /** The command was stopped. */
    public Aborted() {
        super("aborted");
    }
}
