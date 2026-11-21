package io.github.rkaushikdev.swagebay.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.function.UnaryOperator;

/**
 * What a command talks to: the environment it was started in, the keyboard and the two streams.
 *
 * <p>Everything a command prints goes to {@code out}. A failure goes to {@code err} as one line
 * with its code on it, so a script reading {@code out} never has to step round an error message.
 *
 * @param environment the value of an environment variable by name, or null when it is not set
 * @param in what the operator types
 * @param out where a command prints what it found or did
 * @param err where a failure is reported
 */
public record Terminal(UnaryOperator<String> environment, BufferedReader in, PrintWriter out, PrintWriter err) {

    /** The process's own environment, keyboard and streams. */
    public static Terminal system() {
        Charset charset = Charset.defaultCharset();
        return new Terminal(
                System.getenv()::get,
                new BufferedReader(new InputStreamReader(System.in, charset)),
                new PrintWriter(System.out, true, charset),
                new PrintWriter(System.err, true, charset));
    }
}
