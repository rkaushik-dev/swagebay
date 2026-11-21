package io.github.rkaushikdev.swagebay;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.testing.BuildFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What has to be true of the package itself, checked from inside the tests.
 *
 * <p>Two of these go wrong in projects and show up in no other test: a resource the program needs
 * at run time that was left in the wrong directory, so it is in the repository and missing from
 * the jar; and an entry point the build names that moved, so the jar builds and fails on the
 * first line the user types. The version check is here rather than in a release script because a
 * version that does not match the build is a version that will be wrong in a bug report.
 */
class PackagingTest {

    private static final Pattern EXACT = Pattern.compile("[0-9]+(\\.[0-9]+)*");

    private final BuildFile build = BuildFile.ofThisProject();

    @Test
    void theSchemaShipsBesideTheClasses() {
        ClassLoader loader = Swagebay.class.getClassLoader();
        List<String> missing = IntStream.rangeClosed(1, 9)
                .mapToObj(n -> "db/migration/V" + n + "__")
                .filter(prefix ->
                        Stream.of(migrations()).noneMatch(name -> ("db/migration/" + name).startsWith(prefix)))
                .toList();
        assertThat(missing).isEmpty();
        for (String name : migrations()) {
            assertThat(loader.getResource("db/migration/" + name)).as(name).isNotNull();
        }
    }

    @Test
    void theRuntimeResourcesAreDeclaredWithTheMainSources() {
        assertThat(build.file("src/main/resources/db/migration")).isDirectory();
        assertThat(build.file("src/main/resources/logback.xml")).isRegularFile();
        assertThat(build.file("src/main/resources/application.properties")).isRegularFile();
        assertThat(build.file("src/test/resources/db")).doesNotExist();
    }

    @Test
    void theEntryPointTheBuildNamesExistsAndCanBeCalled() throws ReflectiveOperationException {
        String target = build.property("main.class").orElseThrow();
        Method main = Class.forName(target).getMethod("main", String[].class);
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(build.pluginSetting("spring-boot-maven-plugin", "mainClass")).contains("${main.class}");
    }

    @Test
    void theDeclaredVersionMatchesThePackage() {
        assertThat(build.value("version")).contains(Swagebay.VERSION);
        assertThat(build.value("artifactId")).contains("swagebay");
    }

    @Test
    void everyRuntimeDependencyIsPinnedExactly() {
        assertThat(build.parentVersion())
                .hasValueSatisfying(version -> assertThat(version).matches(EXACT));
        assertThat(build.dependencies()).isNotEmpty();
        for (BuildFile.Coordinates dependency : build.dependencies()) {
            dependency.resolvedIn(build).ifPresent(version -> assertThat(version)
                    .as(dependency.artifactId())
                    .matches(EXACT));
            if (dependency.version().isEmpty()) {
                assertThat(dependency.groupId())
                        .as(dependency.artifactId() + " takes its version from the pinned parent")
                        .isIn("org.springframework.boot", "org.flywaydb", "com.h2database", "org.junit.platform");
            }
        }
    }

    @Test
    void theBuildPluginsArePinnedToo() {
        assertThat(build.plugins()).isNotEmpty();
        for (BuildFile.Coordinates plugin : build.plugins()) {
            boolean managedByTheParent = plugin.groupId().equals("org.apache.maven.plugins")
                    || plugin.groupId().equals("org.springframework.boot");
            if (!managedByTheParent) {
                assertThat(plugin.resolvedIn(build)).as(plugin.artifactId()).isPresent();
            }
            plugin.resolvedIn(build)
                    .ifPresent(version ->
                            assertThat(version).as(plugin.artifactId()).matches(EXACT));
        }
    }

    /**
     * The command line answers its version in a fresh JVM started in an empty directory with no
     * database configured, and leaves the directory empty: nothing is opened or created to answer.
     */
    @Test
    void thePackageRunsWithoutADatabaseOrAConfigFile(@TempDir Path empty) throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder launch = new ProcessBuilder(
                        java.toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        build.property("main.class").orElseThrow(),
                        "--version")
                .directory(empty.toFile())
                .redirectErrorStream(true);
        launch.environment().remove("SWAGEBAY_DATABASE");
        Process child = launch.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        child.getInputStream().transferTo(output);
        assertThat(child.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(child.exitValue())
                .as(output.toString(StandardCharsets.UTF_8))
                .isZero();
        assertThat(output.toString(StandardCharsets.UTF_8).strip()).endsWith(Swagebay.VERSION);
        try (Stream<Path> left = Files.list(empty)) {
            assertThat(left).isEmpty();
        }
    }

    /** When the classes run from the built jar, its manifest carries the same version. */
    @Test
    void theJarMetadataAgreesWhenItIsInstalled() {
        String installed = Swagebay.class.getPackage().getImplementationVersion();
        if (installed == null) {
            return;
        }
        assertThat(installed).isEqualTo(Swagebay.VERSION);
    }

    private static String[] migrations() {
        try (Stream<Path> files = Files.list(BuildFile.root().resolve("src/main/resources/db/migration"))) {
            return files.filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .toArray(String[]::new);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
