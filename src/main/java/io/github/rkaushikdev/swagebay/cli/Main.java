package io.github.rkaushikdev.swagebay.cli;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.Swagebay;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import io.github.rkaushikdev.swagebay.persistence.Database;
import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/**
 * The command line entry point.
 *
 * <p>Two things this does that are worth saying out loud.
 *
 * <p>Every command opens its own unit of work and closes it. There is no persistent session and no
 * connection held between commands, because the tool is run from a site laptop over a link that
 * drops, and a held connection is a hung command that somebody kills with the transaction half
 * done.
 *
 * <p>Domain errors are caught here and printed as one line with the code on it, rather than traced.
 * A radiographer at three in the morning does not want a stack trace, and the code on the line is
 * enough for the office to find the problem the next day. The exit status carries the class of
 * failure so a script can branch on it without parsing the message. Anything that is not a domain
 * error, a usage error or an abort is a fault in the tool, and it is left to escape with its stack
 * trace.
 */
@Command(
        name = "swagebay",
        version = "swagebay, version " + Swagebay.VERSION,
        description = "Radiographic technique planning and image quality qualification.",
        subcommands = {CatalogueCommands.class, Main.Init.class, SafetyCommands.class})
public final class Main extends Group {

    /** The command did what it was asked. */
    public static final int EXIT_OK = 0;

    /** A value handed in was refused, or the command line itself was wrong. */
    public static final int EXIT_VALIDATION = 2;

    /** A record asked for by identity does not exist. */
    public static final int EXIT_NOT_FOUND = 3;

    /** The request disagreed with what is already on record. */
    public static final int EXIT_CONFLICT = 4;

    /** The shot as described cannot be made. */
    public static final int EXIT_TECHNIQUE = 5;

    /** The tables or settings the tool reads are wrong, which is the office's problem. */
    public static final int EXIT_CONFIGURATION = 6;

    /** Any other failure, and an abort. */
    public static final int EXIT_OTHER = 1;

    /** The environment variable that names the database when {@code --database} is not given. */
    public static final String DATABASE_VARIABLE = "SWAGEBAY_DATABASE";

    private static final String HELP = "Show this message and exit.";

    private final Terminal terminal;

    @Option(
            names = "--database",
            paramLabel = "TEXT",
            defaultValue = Database.DEFAULT_URL,
            showDefaultValue = Help.Visibility.ALWAYS,
            description = "JDBC url of the database to work against")
    private String database;

    @Option(names = "--version", versionHelp = true, description = "Show the version and exit.")
    private boolean versionRequested;

    private Database opened;

    private Main(Terminal terminal) {
        this.terminal = terminal;
    }

    /** The console script: run the command line and leave with its exit status. */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /** Run one command line against {@code terminal} and answer its exit status. */
    public static int run(Terminal terminal, String... args) {
        return run(terminal, List.of(), args);
    }

    /**
     * {@link #run(Terminal, String...)} with {@code planted} commands beside the built-in ones, which
     * is how a test puts a command that ends in a chosen way behind the entry point.
     */
    static int run(Terminal terminal, List<? extends Subcommand> planted, String... args) {
        Main main = new Main(terminal);
        try {
            return main.dispatch(main.commandLine(planted), args);
        } finally {
            main.close();
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    /**
     * The exit status that names the class of {@code error}. The classes are disjoint, so the most
     * specific one that applies is the only one, and a class with no status of its own gets
     * {@link #EXIT_OTHER}.
     */
    public static int exitStatus(SwagebayError error) {
        return switch (error) {
            case NotFoundError missing -> EXIT_NOT_FOUND;
            case ConflictError disagreement -> EXIT_CONFLICT;
            case ValidationError refused -> EXIT_VALIDATION;
            case TechniqueError impossible -> EXIT_TECHNIQUE;
            case ConfigurationError misconfigured -> EXIT_CONFIGURATION;
            default -> EXIT_OTHER;
        };
    }

    /** Print one line with the code on it and answer the status that names the class of failure. */
    static int fail(PrintWriter err, SwagebayError error) {
        err.println(error.code() + ": " + error.getMessage());
        return exitStatus(error);
    }

    /** What the commands print to and read from. */
    Terminal terminal() {
        return terminal;
    }

    /**
     * The database to work against: {@code --database} when it was given, the environment's
     * {@value #DATABASE_VARIABLE} when that is set and not empty, and {@link Database#DEFAULT_URL}
     * otherwise.
     */
    String databaseUrl() {
        if (spec().commandLine().getParseResult().hasMatchedOption("--database")) {
            return database;
        }
        String exported = terminal.environment().apply(DATABASE_VARIABLE);
        return exported == null || exported.isEmpty() ? database : exported;
    }

    /** The database, opened the first time a command asks for it and closed when the command ends. */
    Database database() {
        if (opened == null) {
            opened = Database.open(databaseUrl());
        }
        return opened;
    }

    private CommandLine commandLine(List<? extends Subcommand> planted) {
        CommandLine line = new CommandLine(this);
        planted.forEach(line::addSubcommand);
        line.setExpandAtFiles(false)
                .setOverwrittenOptionsAllowed(true)
                .registerConverter(Integer.class, new Counts.Whole())
                .registerConverter(Integer.TYPE, new Counts.Whole())
                .setOut(terminal.out())
                .setErr(terminal.err());
        house(line);
        return line;
    }

    /**
     * Give {@code line} and everything under it the house layout: a {@code --help} after its own
     * options, the options in the order they are declared, and a synopsis that names the options as
     * one {@code [OPTIONS]} before the arguments.
     */
    private static void house(CommandLine line) {
        CommandSpec command = line.getCommandSpec();
        command.addOption(
                OptionSpec.builder("--help").usageHelp(true).description(HELP).build());
        command.usageMessage().sortOptions(false).abbreviateSynopsis(true);
        line.getSubcommands().values().forEach(Main::house);
    }

    private int dispatch(CommandLine line, String[] args) {
        ParseResult parsed;
        try {
            parsed = line.parseArgs(args);
        } catch (ParameterException misuse) {
            return misused(misuse);
        }
        ParseResult last = parsed;
        for (ParseResult level = parsed; level != null; level = level.subcommand()) {
            CommandLine command = level.commandSpec().commandLine();
            if (level.isUsageHelpRequested()) {
                terminal.out().print(command.getUsageMessage());
                return EXIT_OK;
            }
            if (level.isVersionHelpRequested()) {
                command.printVersionHelp(terminal.out());
                return EXIT_OK;
            }
            last = level;
        }
        Subcommand command = (Subcommand) last.commandSpec().userObject();
        try {
            return command.call();
        } catch (ParameterException misuse) {
            return misused(misuse);
        } catch (SwagebayError error) {
            return fail(terminal.err(), error);
        } catch (Aborted stopped) {
            terminal.err().println(stopped.getMessage());
            return EXIT_OTHER;
        }
    }

    /** Print where the command line went wrong the way every usage error is printed, and leave 2. */
    private int misused(ParameterException misuse) {
        CommandLine where = misuse.getCommandLine();
        Help help = where.getHelp();
        PrintWriter err = terminal.err();
        err.print(help.synopsisHeading());
        err.print(help.synopsis(help.synopsisHeadingLength()));
        err.println("Try '" + where.getCommandSpec().qualifiedName() + " --help' for help.");
        err.println();
        err.println("Error: " + misuse.getMessage());
        return EXIT_VALIDATION;
    }

    private void close() {
        if (opened != null) {
            opened.close();
        }
    }

    /** Build the schema in an empty database. */
    @Command(name = "init", description = "Build the schema in an empty database.")
    static final class Init extends Leaf {

        @Override
        public Integer call() {
            main().database().migrate();
            out().println("schema built in " + main().databaseUrl());
            return EXIT_OK;
        }
    }
}
