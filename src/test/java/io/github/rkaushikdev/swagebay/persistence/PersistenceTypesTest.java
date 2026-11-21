package io.github.rkaushikdev.swagebay.persistence;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static io.github.rkaushikdev.swagebay.quantities.Units.seconds;
import static io.github.rkaushikdev.swagebay.quantities.Units.usv;
import static io.github.rkaushikdev.swagebay.quantities.Units.usvPerHour;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.quantities.Activity;
import io.github.rkaushikdev.swagebay.quantities.Dose;
import io.github.rkaushikdev.swagebay.quantities.DoseRate;
import io.github.rkaushikdev.swagebay.quantities.Duration;
import io.github.rkaushikdev.swagebay.quantities.Length;
import jakarta.persistence.EntityManager;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The value columns, written to a real table and read back, and the conventions the schema is built
 * on.
 *
 * <p>The sample table holds one column of each value type and lives in a database of its own,
 * built straight from its mapping.
 */
class PersistenceTypesTest {

    private static Database database;
    private static LocalContainerEntityManagerFactoryBean factory;

    @BeforeAll
    static void buildTheSampleTable() {
        database = Database.inMemory();
        factory = Database.entityManagerFactoryBean(database.dataSource(), "create", List.of(SampleRow.class));
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void dropTheSampleTable() {
        factory.destroy();
        database.close();
    }

    private static EntityManager entityManager() {
        return factory.getNativeEntityManagerFactory().createEntityManager();
    }

    /** Write one sample row with {@code values} set, and read it back from the database. */
    private static SampleRow roundTrip(Consumer<SampleRow> values) {
        EntityManager writer = entityManager();
        try {
            writer.getTransaction().begin();
            writer.createQuery("delete from SampleRow").executeUpdate();
            SampleRow row = new SampleRow();
            row.id = 1;
            values.accept(row);
            writer.persist(row);
            writer.getTransaction().commit();
        } finally {
            writer.close();
        }
        EntityManager reader = entityManager();
        try {
            return reader.find(SampleRow.class, 1);
        } finally {
            reader.close();
        }
    }

    /** What the database holds in {@code column} of the sample row, as the database has it. */
    private static Object stored(String column) {
        EntityManager reader = entityManager();
        try {
            return reader.createNativeQuery("select " + column + " from sample where id = 1")
                    .getSingleResult();
        } finally {
            reader.close();
        }
    }

    /** Write {@code value} into {@code column} of row 99, the way a statement binds a value. */
    private static void writeOne(String column, Object value) {
        EntityManager writer = entityManager();
        try {
            writer.getTransaction().begin();
            writer.createQuery("update SampleRow s set s." + column + " = :value where s.id = 99")
                    .setParameter("value", value)
                    .executeUpdate();
        } finally {
            writer.getTransaction().rollback();
            writer.close();
        }
    }

    @Test
    void aLengthGoesInAsMicrometresAndComesBackALength() {
        SampleRow row = roundTrip(sample -> sample.wall = mm("9.53"));
        assertThat(row.wall).isEqualTo(mm("9.53"));
        assertThat(row.wall.um()).isEqualTo(9530);
        assertThat(stored("wall")).isEqualTo(9530L);
    }

    @Test
    void anActivitySurvivesTheRoundTripExactly() {
        SampleRow row = roundTrip(sample -> sample.load = curie(90));
        assertThat(row.load).isEqualTo(curie(90));
        assertThat(row.load.curie()).isEqualByComparingTo("90");
    }

    @Test
    void aDurationAndADoseComeBackAsThemselves() {
        SampleRow row = roundTrip(sample -> {
            sample.shot = seconds(93);
            sample.taken = usv(500);
            sample.rate = usvPerHour(20);
        });
        assertThat(row.shot).isEqualTo(seconds(93));
        assertThat(row.taken).isEqualTo(usv(500));
        assertThat(row.rate).isEqualTo(usvPerHour(20));
    }

    @Test
    void aDecimalKeepsItsLastDigit() {
        SampleRow row = roundTrip(sample -> sample.density = new BigDecimal("2.55"));
        assertThat(row.density).isEqualByComparingTo("2.55");
        assertThat(row.density).hasToString("2.55");
    }

    @Test
    void aDecimalThatAFloatWouldRuinSurvives() {
        BigDecimal awkward = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        SampleRow row = roundTrip(sample -> sample.density = awkward);
        assertThat(row.density).isEqualByComparingTo("0.3");
        assertThat(row.density).hasToString("0.3");
    }

    @Test
    void nullsStayNullRatherThanBecomingZero() {
        SampleRow row = roundTrip(sample -> {});
        assertThat(row.wall).isNull();
        assertThat(row.load).isNull();
        assertThat(row.density).isNull();
    }

    /**
     * A bare number for a quantity column, and a float for a decimal one, are refused rather than
     * coerced. On a row the field's type already rules them out at compile time; a statement's bound
     * value is the one place an untyped value can reach a column, and it is refused there too, naming
     * the type the column holds.
     */
    @Test
    void theWrongTypeIsRefusedRatherThanCoerced() {
        Map<String, Object> wrong = new LinkedHashMap<>();
        wrong.put("wall", 9530);
        wrong.put("load", 90);
        wrong.put("shot", 93);
        wrong.put("taken", 500);
        wrong.put("rate", 20);
        wrong.put("density", 2.55);
        Map<String, Class<?>> expected = Map.of(
                "wall", Length.class,
                "load", Activity.class,
                "shot", Duration.class,
                "taken", Dose.class,
                "rate", DoseRate.class,
                "density", BigDecimal.class);
        wrong.forEach((column, value) -> assertThatThrownBy(() -> writeOne(column, value))
                .as(column)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expected.get(column).getName()));
    }

    @Test
    void aLengthColumnWillNotTakeAnActivity() {
        assertThatThrownBy(() -> writeOne("wall", curie(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("of type [" + Activity.class.getName() + "]")
                .hasMessageContaining(Length.class.getName());
    }

    /**
     * The convention is written down once, and every unique key, check and foreign key on the
     * mapping carries the name it gives.
     */
    @Test
    void theNamingConventionIsSetOnTheSchema() {
        assertThat(Schema.NAMING_CONVENTION)
                .containsExactly(
                        Map.entry("ix", "ix_{table}_{column}"),
                        Map.entry("uq", "uq_{table}_{column}"),
                        Map.entry("ck", "ck_{table}_{name}"),
                        Map.entry("fk", "fk_{table}_{column}_{referred_table}"),
                        Map.entry("pk", "pk_{table}"));
        assertThat(Schema.NAMING_CONVENTION.get("pk")).isEqualTo("pk_{table}");
        assertThat(Schema.primaryKey("counter")).isEqualTo("pk_counter");
        int uniques = 0;
        int checks = 0;
        int keys = 0;
        for (Class<?> entity : Mapping.entities()) {
            String table = Mapping.table(entity);
            for (UniqueConstraint unique : Mapping.uniques(entity)) {
                assertThat(unique.name()).isEqualTo(Schema.unique(table, unique.columnNames()[0]));
                uniques++;
            }
            for (Check check : Mapping.checks(entity)) {
                assertThat(check.name()).startsWith(Schema.check(table, "")).isNotEqualTo(Schema.check(table, ""));
                checks++;
            }
            for (Mapping.ForeignKey key : Mapping.foreignKeys(entity)) {
                assertThat(key.name()).isEqualTo(Schema.foreignKey(table, key.column(), key.referred()));
                keys++;
            }
        }
        assertThat(List.of(uniques, checks, keys)).allMatch(count -> count > 0);
    }

    @Test
    void theTableNamesComeBackSorted() {
        List<String> names = Schema.tableNames();
        assertThat(names).hasSize(30).isSorted();
    }
}
