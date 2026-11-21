package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_CONFIGURATION;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_CONFLICT;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_NOT_FOUND;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_OTHER;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_TECHNIQUE;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_VALIDATION;
import static io.github.rkaushikdev.swagebay.cli.Main.exitStatus;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.QualificationError;
import io.github.rkaushikdev.swagebay.Swagebay;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

/**
 * The entry point itself, which every other command line test goes through and none of them looks
 * at. It is where the exit statuses are decided, and an exit status nobody checks is a shell script
 * that carries on after a failure. The commands planted here end in a chosen way, so each test sees
 * only what the entry point makes of that ending.
 */
class CliEntryPointTest {

    /** A command that ends the way it is told to. */
    @Command(name = "planted")
    static final class Planted extends Leaf {

        private final Function<Planted, Integer> ending;

        Planted(Function<Planted, Integer> ending) {
            this.ending = ending;
        }

        @Override
        public Integer call() {
            return ending.apply(this);
        }
    }

    private static Shell.Result ending(Function<Planted, Integer> ending) {
        return new Shell().planting(new Planted(ending)).run("planted");
    }

    @Test
    void everyErrorClassMapsToItsOwnStatus() {
        assertThat(exitStatus(new NotFoundError("weld", "GW-104"))).isEqualTo(EXIT_NOT_FOUND);
        assertThat(exitStatus(new ConflictError("out"))).isEqualTo(EXIT_CONFLICT);
        assertThat(exitStatus(new ValidationError("no"))).isEqualTo(EXIT_VALIDATION);
        assertThat(exitStatus(new TechniqueError("no"))).isEqualTo(EXIT_TECHNIQUE);
        assertThat(exitStatus(new ConfigurationError("no"))).isEqualTo(EXIT_CONFIGURATION);
    }

    @Test
    void anErrorClassWithNoMappingFallsBackRatherThanCrashing() {
        assertThat(exitStatus(new QualificationError("no"))).isEqualTo(EXIT_OTHER);
        assertThat(exitStatus(new SwagebayError("no"))).isEqualTo(EXIT_OTHER);
    }

    @Test
    void failingPrintsTheCodeAndLeavesWithItsStatus() {
        StringWriter err = new StringWriter();
        int status = Main.fail(new PrintWriter(err, true), new NotFoundError("weld", "GW-104"));
        assertThat(status).isEqualTo(EXIT_NOT_FOUND);
        assertThat(err.toString()).contains("qi.not_found");
    }

    @Test
    void aDomainErrorFromACommandBecomesItsExitStatus() {
        Shell.Result result = ending(command -> {
            throw new ConflictError("that source is already out", Context.of("serial", "IR-5512"));
        });
        assertThat(result.code()).isEqualTo(EXIT_CONFLICT);
        assertThat(result.err()).contains("IR-5512");
    }

    @Test
    void aUsageErrorPrintsTheUsageAndExitsTwo() {
        Shell.Result result = ending(command -> {
            throw command.misused("no such option");
        });
        assertThat(result.code()).isEqualTo(EXIT_VALIDATION);
        assertThat(result.err()).contains("no such option");
    }

    @Test
    void anAbortSaysSoRatherThanPrintingATraceback() {
        Shell.Result result = ending(command -> {
            throw new Aborted();
        });
        assertThat(result.code()).isEqualTo(EXIT_OTHER);
        assertThat(result.err()).contains("aborted");
    }

    @Test
    void aCommandThatWorkedExitsZero() {
        assertThat(ending(command -> EXIT_OK).code()).isEqualTo(EXIT_OK);
    }

    @Test
    void theVersionFlagPrintsTheVersion() {
        Shell.Result result = new Shell().run("--version");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains(Swagebay.VERSION);
    }

    @Test
    void theHelpListsTheGroups() {
        String output = new Shell().run("--help").out();
        assertThat(output).contains("morning", "ncr");
    }
}
