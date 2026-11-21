package io.github.rkaushikdev.swagebay;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.inch;
import static io.github.rkaushikdev.swagebay.quantities.Units.minutes;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.identity.ClockPosition;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.identity.WeldId;
import io.github.rkaushikdev.swagebay.quantities.Length;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The names a consumer reaches through the front door, and that they keep meaning the same thing.
 *
 * <p>The front door is the only part of the package somebody else's code depends on by name, so it
 * has a test of its own. Moving a type is free until it is not, and the moment it is not is the
 * moment a consumer upgrades.
 */
class PublicSurfaceTest {

    private static final List<String> DATABASE_LIBRARIES =
            List.of("jakarta.persistence.", "org.hibernate.", "org.flywaydb.", "org.h2.", "org.springframework.");

    @Test
    void theVersionIsPublishedAndLooksLikeOne() {
        String[] parts = Swagebay.VERSION.split("\\.", -1);
        assertThat(parts).hasSize(3);
        assertThat(parts).allSatisfy(part -> assertThat(part).matches("[0-9]+"));
    }

    @Test
    void theVersionIsInThePublicNames() throws NoSuchFieldException {
        Field version = Swagebay.class.getField("VERSION");
        assertThat(Modifier.isPublic(version.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(version.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(version.getModifiers())).isTrue();
        assertThat(version.getType()).isEqualTo(String.class);
    }

    @Test
    void everyPublishedNameIsActuallyThere() {
        assertThat(Swagebay.PUBLISHED).isNotEmpty();
        for (Class<?> published : Swagebay.PUBLISHED) {
            assertThat(Modifier.isPublic(published.getModifiers()))
                    .as(published.getName())
                    .isTrue();
            assertThat(published.getEnclosingClass()).as(published.getName()).isNull();
        }
    }

    @Test
    void thePublishedNamesAreSortedSoADiffStaysReadable() {
        List<String> names =
                Swagebay.PUBLISHED.stream().map(Class::getSimpleName).toList();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(Comparator.naturalOrder());
        assertThat(names).isEqualTo(sorted);
        assertThat(new HashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    void theQuantityConstructorsAreAtTheFrontDoor() {
        assertThat(mm(10).mm()).isEqualByComparingTo("10");
        assertThat(inch(1)).isEqualTo(mm("25.4"));
        assertThat(curie(1).bq()).isEqualTo(37_000_000_000L);
        assertThat(seconds(90)).isEqualTo(minutes("1.5"));
        assertThat(usv(1000).msv()).isEqualByComparingTo("1");
    }

    @Test
    void theErrorHierarchyIsAtTheFrontDoor() {
        assertThat(SwagebayError.class).isAssignableFrom(ValidationError.class);
        assertThat(SwagebayError.class).isAssignableFrom(ConflictError.class);
        assertThat(SwagebayError.class).isAssignableFrom(NotFoundError.class);
        assertThatThrownBy(() -> {
                    throw new ValidationError("not a length");
                })
                .isInstanceOf(SwagebayError.class);
    }

    @Test
    void everyPublishedErrorCarriesAStableCode() throws ReflectiveOperationException {
        Set<String> codes = new HashSet<>();
        for (Class<?> published : Swagebay.PUBLISHED) {
            if (SwagebayError.class.isAssignableFrom(published)) {
                String code = (String) published.getField("CODE").get(null);
                assertThat(code).as(published.getName()).startsWith("qi.");
                codes.add(code);
            }
        }
        assertThat(codes).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void theIdentitiesAreAtTheFrontDoor() {
        assertThat(Swagebay.PUBLISHED).contains(JobNumber.class, WeldId.class, ClockPosition.class);
        assertThat(JobNumber.parse("24-0001")).hasToString("24-0001");
        assertThat(WeldId.parse("GW-104R1")).hasToString("GW-104R1");
        assertThat(ClockPosition.parse("3:00")).hasToString("3:00");
    }

    @Test
    void theFrontDoorNamesTheTypesRatherThanRedefiningThem() {
        assertThat(Swagebay.PUBLISHED).containsOnlyOnce(Length.class);
        assertThat(Swagebay.PUBLISHED.stream()
                        .filter(type -> type.getSimpleName().equals("Length")))
                .containsExactly(Length.class);
    }

    /**
     * The front door and its quantities load from the package's own classes alone. A loader that
     * sees nothing but those classes records every name it is asked for, and none of them is a
     * database or framework library. The same loader asked for the database does reach for one,
     * so the check is seen to bite.
     */
    @Test
    void reachingTheFrontDoorDoesNotPullInTheDatabase() throws Exception {
        URL classes = Swagebay.class.getProtectionDomain().getCodeSource().getLocation();
        try (Recording loader = new Recording(classes)) {
            Class<?> door = Class.forName(Swagebay.class.getName(), true, loader);
            Object published = door.getField("PUBLISHED").get(null);
            assertThat(published).isInstanceOf(List.class);
            assertThat((List<?>) published).hasSize(Swagebay.PUBLISHED.size());
            Class<?> units = Class.forName("io.github.rkaushikdev.swagebay.quantities.Units", true, loader);
            assertThat(units.getMethod("mm", long.class).invoke(null, 10L)).hasToString("10.00 mm");
            assertThat(loader.asked()).noneMatch(Recording::isDatabaseLibrary);
        }
        try (Recording control = new Recording(classes)) {
            assertThatThrownBy(() -> Class.forName("io.github.rkaushikdev.swagebay.persistence.Database", true, control)
                            .getMethods())
                    .isInstanceOf(LinkageError.class);
            assertThat(control.asked()).anyMatch(Recording::isDatabaseLibrary);
        }
    }

    /** A loader over the package's classes only, remembering every name it was asked to load. */
    private static final class Recording extends URLClassLoader {

        private final List<String> asked = Collections.synchronizedList(new ArrayList<>());

        Recording(URL classes) {
            super(new URL[] {classes}, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            asked.add(name);
            return super.loadClass(name, resolve);
        }

        List<String> asked() {
            return List.copyOf(asked);
        }

        static boolean isDatabaseLibrary(String name) {
            return DATABASE_LIBRARIES.stream().anyMatch(name::startsWith);
        }
    }
}
