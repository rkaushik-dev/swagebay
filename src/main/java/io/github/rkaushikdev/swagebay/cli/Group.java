package io.github.rkaushikdev.swagebay.cli;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * A command that only holds other commands.
 *
 * <p>Named on its own it prints its help and leaves with {@link Main#EXIT_OK}, which is how a
 * person finds out what is in it. Given options but no command, it is a usage error, because
 * whatever those options were meant for has not been said.
 */
abstract class Group implements Subcommand {

    @Spec
    private CommandSpec spec;

    /** This command as the command line was built with it. */
    final CommandSpec spec() {
        return spec;
    }

    @Override
    public Integer call() {
        CommandLine line = spec.commandLine();
        if (!line.getParseResult().matchedArgs().isEmpty()) {
            throw new ParameterException(line, "Missing command.");
        }
        line.getOut().print(line.getUsageMessage());
        return Main.EXIT_OK;
    }
}
