package io.github.rkaushikdev.swagebay.cli;

import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_CONFIGURATION;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_CONFLICT;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_NOT_FOUND;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_OTHER;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_TECHNIQUE;
import static io.github.rkaushikdev.swagebay.cli.Main.EXIT_VALIDATION;
import static io.github.rkaushikdev.swagebay.cli.Main.exitStatus;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.ConfigurationError;
import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.SafetyError;
import io.github.rkaushikdev.swagebay.Swagebay;
import io.github.rkaushikdev.swagebay.SwagebayError;
import io.github.rkaushikdev.swagebay.TechniqueError;
import io.github.rkaushikdev.swagebay.ValidationError;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliMainTest {

    @TempDir
    Path tmp;

    private final Shell runner = new Shell();

    @Test
    void theVersionIsReported() {
        Shell.Result result = runner.run("--version");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains(Swagebay.VERSION);
    }

    @Test
    void theHelpNamesWhatTheToolIs() {
        Shell.Result result = runner.run("--help");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("technique planning");
    }

    @Test
    void theSchemaIsBuiltIntoANamedDatabase() {
        String url = Shell.fileUrl(tmp, "shop");
        Shell.Result result = runner.run("--database", url, "init");
        assertThat(result.code()).isZero();
        assertThat(result.out()).contains("schema built");
        assertThat(tmp.resolve("shop.mv.db")).exists();
    }

    @Test
    void theDatabaseCanComeFromTheEnvironment() {
        String url = Shell.fileUrl(tmp, "env");
        Shell.Result result =
                runner.exporting(Map.of(Main.DATABASE_VARIABLE, url)).run("init");
        assertThat(result.code()).isZero();
        assertThat(tmp.resolve("env.mv.db")).exists();
    }

    @Test
    void eachClassOfFailureHasItsOwnExitStatus() {
        assertThat(exitStatus(new NotFoundError("source", "IR-1"))).isEqualTo(EXIT_NOT_FOUND);
        assertThat(exitStatus(new ConflictError("busy"))).isEqualTo(EXIT_CONFLICT);
        assertThat(exitStatus(new ValidationError("bad"))).isEqualTo(EXIT_VALIDATION);
        assertThat(exitStatus(new TechniqueError("impossible"))).isEqualTo(EXIT_TECHNIQUE);
        assertThat(exitStatus(new ConfigurationError("ours"))).isEqualTo(EXIT_CONFIGURATION);
    }

    @Test
    void anUnclassifiedFailureGetsTheGeneralStatus() {
        assertThat(exitStatus(new SwagebayError("something"))).isEqualTo(EXIT_OTHER);
        assertThat(exitStatus(new SafetyError("over the limit"))).isEqualTo(EXIT_OTHER);
    }

    @Test
    void theStatusesAreAllDifferent() {
        Set<Integer> statuses =
                Set.of(EXIT_VALIDATION, EXIT_NOT_FOUND, EXIT_CONFLICT, EXIT_TECHNIQUE, EXIT_CONFIGURATION, EXIT_OTHER);
        assertThat(statuses).hasSize(6).doesNotContain(0);
    }

    @Test
    void aCommandThatDoesNotExistIsAUsageError() {
        assertThat(runner.run("not-a-command").code()).isNotZero();
    }

    @Test
    void theMoreSpecificStatusWins() {
        assertThat(exitStatus(new NotFoundError("weld", "GW-1"))).isNotEqualTo(exitStatus(new ValidationError("x")));
    }
}
