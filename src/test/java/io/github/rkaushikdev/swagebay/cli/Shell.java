package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A terminal for the command line tests: an environment, the arguments every line starts with, and
 * what the command printed on each stream.
 */
final class Shell {

    private final List<String> leading;
    private final Map<String, String> environment;
    private final List<Subcommand> planted;

    Shell() {
        this(List.of(), Map.of(), List.of());
    }

    private Shell(List<String> leading, Map<String, String> environment, List<Subcommand> planted) {
        this.leading = leading;
        this.environment = environment;
        this.planted = planted;
    }

    /** A shell whose every command line works against the database at {@code url}. */
    static Shell against(String url) {
        return new Shell(List.of("--database", url), Map.of(), List.of());
    }

    /** The same shell with {@code exported} in its environment. */
    Shell exporting(Map<String, String> exported) {
        return new Shell(leading, exported, planted);
    }

    /** The same shell with {@code commands} behind the entry point beside the built-in ones. */
    Shell planting(Subcommand... commands) {
        return new Shell(leading, environment, List.of(commands));
    }

    /** Run one command line and keep what it printed. */
    Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                environment::get,
                new BufferedReader(new StringReader("")),
                new PrintWriter(out, true),
                new PrintWriter(err, true));
        List<String> line = new ArrayList<>(leading);
        line.addAll(List.of(args));
        int code = Main.run(terminal, planted, line.toArray(String[]::new));
        return new Result(code, out.toString(), err.toString());
    }

    /** A file database under {@code directory}, named {@code name}. */
    static String fileUrl(Path directory, String name) {
        return "jdbc:h2:file:" + directory.resolve(name);
    }

    /** Open the database at {@code url}, let {@code work} change it, and commit. */
    static void seed(String url, Consumer<UnitOfWork> work) {
        try (Database database = Database.open(url)) {
            database.transaction(work);
        }
    }

    /** What one command line came back with. */
    record Result(int code, String out, String err) {

        /** Both streams, the way a terminal shows them. */
        String output() {
            return out + err;
        }

        /** The lines printed on standard output. */
        List<String> lines() {
            return out.lines().toList();
        }

        /** The code of the domain error the command failed with, off the one line it printed. */
        String failure() {
            int colon = err.indexOf(':');
            return colon < 0 ? "" : err.substring(0, colon);
        }
    }
}
