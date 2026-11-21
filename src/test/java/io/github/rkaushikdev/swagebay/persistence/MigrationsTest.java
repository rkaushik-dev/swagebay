package io.github.rkaushikdev.swagebay.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.persistence.models.Models;
import jakarta.persistence.Index;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The migrations and the mapping have to agree, and the check has to be run.
 *
 * <p>A schema built straight from the entities and a schema built by the migrations are two
 * different artifacts, and they drift the moment somebody adds a column to an entity and forgets
 * the migration. The only way to catch that is to build both and compare, which is what this does:
 * one database migrated, one built by the persistence provider's own schema export, compared table
 * by table through the information schema on columns, nullability, types, primary keys, unique
 * keys, foreign keys, indexes and check constraints.
 */
class MigrationsTest {

    private static final String COLUMNS = "select column_name, is_nullable from information_schema.columns"
            + " where table_schema = 'PUBLIC' and table_name = ?";

    private static final String TYPES = "select column_name, data_type, character_maximum_length,"
            + " numeric_precision, numeric_scale, datetime_precision from information_schema.columns"
            + " where table_schema = 'PUBLIC' and table_name = ?";

    private static final String PRIMARY_KEY = "select k.column_name, k.ordinal_position"
            + " from information_schema.table_constraints c join information_schema.key_column_usage k"
            + " on c.constraint_name = k.constraint_name and c.table_name = k.table_name"
            + " where c.table_schema = 'PUBLIC' and c.constraint_type = 'PRIMARY KEY' and c.table_name = ?";

    private static final String UNIQUES = "select c.constraint_name,"
            + " listagg(k.column_name, ',') within group (order by k.ordinal_position)"
            + " from information_schema.table_constraints c join information_schema.key_column_usage k"
            + " on c.constraint_name = k.constraint_name and c.table_name = k.table_name"
            + " where c.table_schema = 'PUBLIC' and c.constraint_type = 'UNIQUE' and c.table_name = ?"
            + " group by c.constraint_name";

    private static final String FOREIGN_KEYS = "select r.constraint_name, k.column_name, u.table_name, u.column_name"
            + " from information_schema.referential_constraints r"
            + " join information_schema.key_column_usage k on r.constraint_name = k.constraint_name"
            + " join information_schema.key_column_usage u on r.unique_constraint_name = u.constraint_name"
            + " and k.position_in_unique_constraint = u.ordinal_position"
            + " where k.table_schema = 'PUBLIC' and k.table_name = ?";

    private static final String INDEXES = "select i.index_name,"
            + " listagg(c.column_name, ',') within group (order by c.ordinal_position)"
            + " from information_schema.indexes i join information_schema.index_columns c"
            + " on i.index_name = c.index_name and i.table_name = c.table_name"
            + " where i.table_schema = 'PUBLIC' and i.table_name = ? and i.index_type_name = 'INDEX'"
            + " and i.index_name not like 'FK%' group by i.index_name";

    private static final String CHECKS = "select c.constraint_name, k.check_clause"
            + " from information_schema.table_constraints c join information_schema.check_constraints k"
            + " on c.constraint_schema = k.constraint_schema and c.constraint_name = k.constraint_name"
            + " where c.table_schema = 'PUBLIC' and c.constraint_type = 'CHECK' and c.table_name = ?";

    /** What is compared, by name. */
    private static final Map<String, String> ASPECTS = aspects();

    private static Database migrated;
    private static Database built;
    private static DataSource builtPool;
    private static LocalContainerEntityManagerFactoryBean exported;

    private static Map<String, String> aspects() {
        Map<String, String> aspects = new LinkedHashMap<>();
        aspects.put("columns", COLUMNS);
        aspects.put("types", TYPES);
        aspects.put("primary key", PRIMARY_KEY);
        aspects.put("unique keys", UNIQUES);
        aspects.put("foreign keys", FOREIGN_KEYS);
        aspects.put("indexes", INDEXES);
        aspects.put("checks", CHECKS);
        return aspects;
    }

    @BeforeAll
    static void buildBothSchemas() {
        migrated = Database.inMemory();
        migrated.migrate();
        built = Database.inMemory();
        builtPool = built.dataSource();
        exported = Database.entityManagerFactoryBean(builtPool, "create", Models.ENTITIES);
        exported.afterPropertiesSet();
    }

    @AfterAll
    static void closeBoth() {
        exported.destroy();
        built.close();
        migrated.close();
    }

    private static List<String> rows(DataSource source, String sql, String table) {
        List<String> found = new ArrayList<>();
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (table != null) {
                statement.setString(1, table.toUpperCase(Locale.ROOT));
            }
            try (ResultSet rows = statement.executeQuery()) {
                int width = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    List<String> row = new ArrayList<>();
                    for (int column = 1; column <= width; column++) {
                        row.add(Objects.toString(rows.getString(column)));
                    }
                    found.add(String.join(" | ", row).toLowerCase(Locale.ROOT));
                }
            }
        } catch (SQLException trouble) {
            throw new IllegalStateException(sql, trouble);
        }
        return found;
    }

    private static void execute(DataSource source, String... statements) throws SQLException {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    /** One aspect of one table, as a set of rows. */
    private static Set<String> read(DataSource source, String aspect, String table) {
        return new TreeSet<>(rows(source, ASPECTS.get(aspect), table));
    }

    /** Every aspect of one table. */
    private static Map<String, Set<String>> shape(DataSource source, String table) {
        Map<String, Set<String>> shape = new LinkedHashMap<>();
        for (String aspect : ASPECTS.keySet()) {
            shape.put(aspect, read(source, aspect, table));
        }
        return shape;
    }

    /** Every table and every aspect of it: the whole schema, comparable with another. */
    private static Map<String, Map<String, Set<String>>> schema(DataSource source) {
        Map<String, Map<String, Set<String>>> schema = new TreeMap<>();
        for (String table : tablesIn(source)) {
            schema.put(table, shape(source, table));
        }
        return schema;
    }

    private static Set<String> tablesIn(DataSource source) {
        return new TreeSet<>(rows(
                source,
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'"
                        + " and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'",
                null));
    }

    private static Set<String> namesIn(DataSource source, String aspect, String table) {
        return read(source, aspect, table).stream()
                .map(row -> row.substring(0, row.indexOf(" | ")))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Flyway flyway(DataSource source) {
        return Flyway.configure()
                .dataSource(source)
                .locations(Database.MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }

    private static List<MigrationInfo> revisions() {
        try (Database empty = Database.inMemory()) {
            return List.of(flyway(empty.dataSource()).info().pending());
        }
    }

    @Test
    void thereIsExactlyOneHead() {
        List<Integer> versions = revisions().stream()
                .map(revision -> Integer.valueOf(revision.getVersion().getVersion()))
                .toList();
        assertThat(versions).isNotEmpty().doesNotHaveDuplicates();
        assertThat(versions)
                .isEqualTo(IntStream.rangeClosed(1, versions.size()).boxed().toList());
    }

    /**
     * Every revision changes the schema, and the schema can be taken out again at every revision:
     * migrated up to each one in turn, the schema differs from the one before it, and cleaning it
     * leaves nothing behind.
     */
    @Test
    void everyRevisionCanBeTakenOutAgain() {
        Map<String, Map<String, Set<String>>> before = Map.of();
        for (MigrationInfo revision : revisions()) {
            try (Database database = Database.inMemory()) {
                Flyway upTo = Flyway.configure()
                        .dataSource(database.dataSource())
                        .locations(Database.MIGRATIONS)
                        .cleanDisabled(false)
                        .target(revision.getVersion().getVersion())
                        .load();
                upTo.migrate();
                Map<String, Map<String, Set<String>>> after = schema(database.dataSource());
                assertThat(after).as(revision.getDescription()).isNotEqualTo(before);
                upTo.clean();
                assertThat(schema(database.dataSource()))
                        .as(revision.getDescription())
                        .isEmpty();
                before = after;
            }
        }
    }

    @Test
    void theMigrationsBuildEveryTableTheModelsDeclare() {
        assertThat(migrated.tableNames()).isEqualTo(Schema.tableNames());
    }

    /**
     * Each aspect of each table matches between the two schemas. Comparisons that read nothing on
     * either side would match too, so the test also shows that each one reads something on both,
     * and that a change planted in a third migrated database is seen by the comparison it belongs to
     * while a table nobody touched still matches.
     */
    @Test
    void theMigratedSchemaMatchesTheModels() throws SQLException {
        SoftAssertions softly = new SoftAssertions();
        for (String table : Schema.tableNames()) {
            softly.assertThat(shape(migrated.dataSource(), table)).as(table).isEqualTo(shape(builtPool, table));
        }
        for (String aspect : ASPECTS.keySet()) {
            for (DataSource side : List.of(migrated.dataSource(), builtPool)) {
                softly.assertThat(Schema.tableNames().stream()
                                .mapToInt(table -> read(side, aspect, table).size())
                                .sum())
                        .as(aspect)
                        .isPositive();
            }
        }
        softly.assertAll();

        try (Database planted = Database.inMemory()) {
            planted.migrate();
            DataSource side = planted.dataSource();
            execute(
                    side,
                    "ALTER TABLE device ADD COLUMN planted INTEGER",
                    "CREATE INDEX ix_device_planted ON device (model_key)",
                    "ALTER TABLE film ADD CONSTRAINT uq_film_planted UNIQUE (name)",
                    "ALTER TABLE sighting DROP CONSTRAINT fk_sighting_source_id_source",
                    "ALTER TABLE counter DROP CONSTRAINT ck_counter_counter_starts_at_one",
                    "ALTER TABLE lot DROP CONSTRAINT pk_lot",
                    "ALTER TABLE lot ADD CONSTRAINT pk_lot PRIMARY KEY (lot_number)");
            assertThat(read(side, "columns", "device")).isNotEqualTo(read(builtPool, "columns", "device"));
            assertThat(read(side, "types", "device")).isNotEqualTo(read(builtPool, "types", "device"));
            assertThat(read(side, "indexes", "device")).isNotEqualTo(read(builtPool, "indexes", "device"));
            assertThat(read(side, "unique keys", "film")).isNotEqualTo(read(builtPool, "unique keys", "film"));
            assertThat(read(side, "foreign keys", "sighting"))
                    .isNotEqualTo(read(builtPool, "foreign keys", "sighting"));
            assertThat(read(side, "checks", "counter")).isNotEqualTo(read(builtPool, "checks", "counter"));
            assertThat(read(side, "primary key", "lot")).isNotEqualTo(read(builtPool, "primary key", "lot"));
            assertThat(shape(side, "job")).isEqualTo(shape(builtPool, "job"));
        }
    }

    @Test
    void theMigrationsComeApartAgain(@TempDir Path tmp) {
        try (Database database = Database.open("jdbc:h2:file:" + tmp.resolve("roundtrip"))) {
            database.migrate();
            assertThat(database.tableNames()).isEqualTo(Schema.tableNames());
            database.drop();
            assertThat(database.tableNames()).isEmpty();
        }
    }

    @Test
    void upgradeAndDowngradeCanBeRepeated(@TempDir Path tmp) {
        try (Database database = Database.open("jdbc:h2:file:" + tmp.resolve("twice"))) {
            for (int round = 0; round < 2; round++) {
                database.migrate();
                assertThat(database.tableNames()).isEqualTo(Schema.tableNames());
                database.drop();
            }
            assertThat(database.tableNames()).isEmpty();
        }
    }

    @Test
    void createAllAndTheMigrationsAgreeOnTheTableList() {
        assertThat(tablesIn(builtPool)).isNotEmpty().isEqualTo(tablesIn(migrated.dataSource()));
    }

    @Test
    void theCheckConstraintsSurviveTheMigration() {
        assertThat(namesIn(migrated.dataSource(), "checks", "source")).contains("ck_source_assay_positive");
    }

    @Test
    void theUniqueConstraintsSurviveToo() {
        assertThat(namesIn(migrated.dataSource(), "unique keys", "weld")).contains("uq_weld_job_id");
    }

    /**
     * The foreign keys carry the convention's names, and so does every other constraint: none in the
     * migrated schema was left for the database to name.
     */
    @Test
    void theForeignKeysAreNamedByTheConvention() {
        assertThat(namesIn(migrated.dataSource(), "foreign keys", "radiograph")).contains("fk_radiograph_weld_id_weld");
        List<String> constraints = rows(
                migrated.dataSource(),
                "select table_name || '.' || constraint_name from information_schema.table_constraints"
                        + " where table_schema = 'PUBLIC'",
                null);
        assertThat(constraints).hasSizeGreaterThan(100).noneMatch(name -> name.contains(".constraint_"));
        assertThat(constraints).contains("counter.pk_counter");
    }

    @Test
    void theMigrationsAreWhereTheBuildLooksForThem() throws URISyntaxException {
        assertThat(Database.MIGRATIONS).isEqualTo("classpath:db/migration");
        Path migrations =
                Path.of(getClass().getClassLoader().getResource("db/migration").toURI());
        assertThat(migrations.resolve("V1__baseline_schema.sql")).isRegularFile();
        assertThatThrownBy(() -> Files.readString(migrations.resolve("nothing.sql")))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void theHotLookupsAreIndexed() {
        Map<String, Set<String>> wanted = Map.of(
                "radiograph", Set.of("ix_radiograph_exposed_at", "ix_radiograph_read_at"),
                "survey", Set.of("ix_survey_radiograph_number", "ix_survey_performed_at"),
                "dose_entry", Set.of("ix_dose_entry_badge_year"),
                "weld", Set.of("ix_weld_job_id"),
                "trip", Set.of("ix_trip_source_id_came_back"),
                "indication", Set.of("ix_indication_radiograph_id"));
        wanted.forEach((table, expected) -> assertThat(namesIn(migrated.dataSource(), "indexes", table))
                .as(table)
                .containsAll(expected));
    }

    @Test
    void theIndexesComeOffAgainOnADowngrade(@TempDir Path tmp) {
        try (Database database = Database.open("jdbc:h2:file:" + tmp.resolve("indexes"))) {
            database.migrate();
            String indexes = "select index_name from information_schema.indexes where table_schema = 'PUBLIC'";
            assertThat(rows(database.dataSource(), indexes, null)).contains("ix_weld_job_id");
            database.drop();
            assertThat(database.tableNames()).isEmpty();
            assertThat(rows(database.dataSource(), indexes, null)).isEmpty();
        }
    }

    @Test
    void thereIsMoreThanOneRevisionNow() {
        List<MigrationInfo> revisions = revisions();
        assertThat(revisions).hasSizeGreaterThanOrEqualTo(2);
        assertThat(revisions.stream().map(MigrationInfo::getVersion).distinct()).hasSameSizeAs(revisions);
    }

    @Test
    void everyIndexTheModelsDeclareReachesTheDatabase() {
        int declared = 0;
        for (Class<?> entity : Mapping.entities()) {
            String table = Mapping.table(entity);
            Set<String> names =
                    Mapping.indexes(entity).stream().map(Index::name).collect(Collectors.toCollection(TreeSet::new));
            assertThat(namesIn(migrated.dataSource(), "indexes", table))
                    .as(table)
                    .containsAll(names);
            declared += names.size();
        }
        assertThat(declared).isPositive();
    }
}
