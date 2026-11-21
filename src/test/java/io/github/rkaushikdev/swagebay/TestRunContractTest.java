package io.github.rkaushikdev.swagebay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.rkaushikdev.swagebay.testing.BuildFile;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * What the test run has to report, which is not the same as what it has to do.
 *
 * <p>Anything that reads this suite from the outside reads its per case results: a verifier, a CI
 * annotator, a flaky test tracker. None of them can tell a run that reported no cases from a run
 * that found no cases; both look like an empty list, and both look like a suite that passed. A
 * build file that quietly prepends a quiet flag to every run produces exactly that. So the
 * contract is checked from the outside, by launching a sample class and reading what the run
 * actually reported, as well as by reading the configuration that shapes the run.
 */
class TestRunContractTest {

    private static final String SAMPLE = "io.github.rkaushikdev.swagebay.naming.NamingTest";

    private final BuildFile build = BuildFile.ofThisProject();

    @Test
    void aRunReportsAResultForEveryCase() {
        Run run = Run.of(SAMPLE);
        assertThat(run.finished()).hasSizeGreaterThan(10);
        assertThat(run.outcomes()).containsOnly(TestExecutionResult.Status.SUCCESSFUL);
    }

    @Test
    void theIdsAreFullyQualifiedBecauseThatIsWhatGetsKeyedOn() {
        List<TestIdentifier> cases = Run.of(SAMPLE).finished();
        assertThat(cases).isNotEmpty();
        for (TestIdentifier identifier : cases) {
            MethodSource source = (MethodSource) identifier.getSource().orElseThrow();
            assertThat(source.getClassName()).isEqualTo(SAMPLE).endsWith("Test");
            assertThat(identifier.getUniqueId()).contains("[class:" + SAMPLE + "]", "[method:");
        }
    }

    @Test
    void theIdsMatchWhatDiscoverySaysTheSuiteHolds() {
        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(Run.request(SAMPLE));
        Set<String> discovered = plan.getRoots().stream()
                .flatMap(root -> plan.getDescendants(root).stream())
                .filter(TestIdentifier::isTest)
                .map(TestIdentifier::getUniqueId)
                .collect(Collectors.toSet());
        Set<String> reported = Run.of(SAMPLE).finished().stream()
                .map(TestIdentifier::getUniqueId)
                .collect(Collectors.toSet());
        assertThat(discovered).isNotEmpty();
        assertThat(discovered).isEqualTo(reported);
    }

    @Test
    void theShortFormStillLeavesThePerCaseLinesOutWhenItIsAskedFor() {
        Run run = Run.of(SAMPLE);
        assertThat(run.quiet()).doesNotContain(run.caseLines().getFirst());
        assertThat(run.quiet()).contains(String.valueOf(run.finished().size()));
    }

    /** Quiet has to be the caller's decision, or asking for the per case lines cannot undo it. */
    @Test
    void theDefaultRunIsNotForcedQuiet() {
        Run run = Run.of(SAMPLE);
        assertThat(run.verbose()).isNotEqualTo(run.quiet());
        assertThat(run.verbose()).contains(run.caseLines());
    }

    @Test
    void theConfigDeclaresNoVerbosityOption() throws IOException {
        Path config = build.file(".mvn/maven.config");
        String declared = Files.isRegularFile(config) ? Files.readString(config) : "";
        assertThat(declared).doesNotContain("-q", "--quiet", "-X", "--debug", "--errors");
        assertThat(build.pluginSetting("maven-surefire-plugin", "printSummary")).isNotEqualTo(Optional.of("false"));
        assertThat(build.pluginSetting("maven-surefire-plugin", "disableXmlReport"))
                .isNotEqualTo(Optional.of("true"));
    }

    @Test
    void theSettingsThatDoBelongInTheConfigAreStillThere() {
        assertThat(build.pluginSetting("maven-surefire-plugin", "forkCount")).contains("1");
        assertThat(build.pluginSetting("maven-surefire-plugin", "reuseForks")).contains("true");
        assertThat(build.pluginSetting("maven-surefire-plugin", "trimStackTrace"))
                .contains("false");
    }

    @Test
    void ciAsksForBatchModeRatherThanRelyingOnTheConfig() throws IOException {
        List<String> lines = Files.readAllLines(build.file(".github/workflows/ci.yml")).stream()
                .filter(line -> line.contains("./mvnw"))
                .toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines).allSatisfy(line -> assertThat(line).contains(" -B "));
    }

    /** One launch of a test class, with what each kind of listener reported. */
    private record Run(
            List<TestIdentifier> finished, List<TestExecutionResult.Status> outcomes, String verbose, String quiet) {

        static LauncherDiscoveryRequest request(String sample) {
            return LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(sample))
                    .build();
        }

        static Run of(String sample) {
            List<TestIdentifier> finished = Collections.synchronizedList(new ArrayList<>());
            List<TestExecutionResult.Status> outcomes = Collections.synchronizedList(new ArrayList<>());
            StringWriter verbose = new StringWriter();
            PrintWriter lines = new PrintWriter(verbose, true);
            TestExecutionListener perCase = new TestExecutionListener() {
                @Override
                public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                    if (identifier.isTest()) {
                        finished.add(identifier);
                        outcomes.add(result.getStatus());
                        lines.println(line(identifier, result));
                    }
                }
            };
            SummaryGeneratingListener summary = new SummaryGeneratingListener();
            LauncherFactory.create().execute(request(sample), perCase, summary);
            StringWriter quiet = new StringWriter();
            summary.getSummary().printTo(new PrintWriter(quiet, true));
            lines.flush();
            return new Run(List.copyOf(finished), List.copyOf(outcomes), verbose + quiet.toString(), quiet.toString());
        }

        List<String> caseLines() {
            return verbose.lines().filter(line -> line.contains("::")).toList();
        }

        private static String line(TestIdentifier identifier, TestExecutionResult result) {
            MethodSource source = (MethodSource) identifier.getSource().orElseThrow();
            return source.getClassName() + "::" + source.getMethodName() + " " + result.getStatus();
        }
    }
}
