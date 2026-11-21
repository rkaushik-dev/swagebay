package io.github.rkaushikdev.swagebay.testing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.swagebay.persistence.Database;
import jakarta.persistence.PersistenceException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * The suite's database: one H2 in memory, migrated once for the whole run and emptied each time a
 * test asks for it.
 *
 * <p>Building the entity manager factory costs more than the tests do, so this keeps one database
 * and one factory and gives every caller a database of its own in effect: the schema with nothing
 * in it, and the generated keys counting from one again.
 */
public final class TestDatabase {

    /** Where the suite's database lives. */
    public static final String URL = "jdbc:h2:mem:swagebay-tests;DB_CLOSE_DELAY=-1";

    private static Database shared;

    private TestDatabase() {}

    /** A throwaway database with the schema in it and nothing else, per call. */
    public static synchronized Database fresh() {
        if (shared == null) {
            shared = Database.open(URL);
            shared.migrate();
        }
        empty(shared);
        return shared;
    }

    /**
     * Assert that {@code work} is refused by a constraint in the schema: a unique key, a check, a
     * foreign key or a column that may not be empty. A failure for any other reason does not count.
     */
    public static void assertTheSchemaRefuses(ThrowingCallable work) {
        assertThatThrownBy(work)
                .isInstanceOf(PersistenceException.class)
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
    }

    private static void empty(Database database) {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : database.tableNames()) {
                statement.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException trouble) {
            throw new IllegalStateException("could not empty the test database", trouble);
        }
    }
}
