package io.github.rkaushikdev.swagebay.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.rkaushikdev.swagebay.persistence.models.Models;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * One database: a connection pool, the migrations that build its schema, and the entity manager
 * factory every {@link UnitOfWork} is opened from.
 *
 * <p>The factory is built the first time something asks for it, and it checks the mapping against
 * the schema when it is: a database nobody has migrated can still be migrated, listed and dropped,
 * and one whose schema has drifted from the entities refuses to open a unit of work at all.
 *
 * <p>Two things about the mapping are fixed here rather than left to the provider's defaults. A
 * value handed to a query must already be the type of the column it is compared with or written
 * to: the provider would otherwise widen a float into a decimal and write digits nobody measured,
 * and the value columns refuse rather than coerce everywhere else. And moments are wall clock
 * times handed to the driver as they are, so half past two on the night the clocks go forward is
 * stored as half past two and not moved by the time zone of whatever machine wrote it.
 */
public final class Database implements AutoCloseable {

    /** The database used when none is named: {@code swagebay.mv.db} in the working directory. */
    public static final String DEFAULT_URL = "jdbc:h2:file:./swagebay";

    /** Where the migrations live, one per revision of the schema, applied in version order. */
    public static final String MIGRATIONS = "classpath:db/migration";

    /**
     * Column names the schema uses that H2 otherwise reads as keywords. They are ordinary names in
     * the domain, so the URL tells H2 so rather than the schema renaming them.
     */
    static final String H2_NON_KEYWORDS = "NON_KEYWORDS=KEY,YEAR";

    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final AtomicInteger MEMORY = new AtomicInteger();

    private final String url;
    private final HikariDataSource pool;
    private final boolean ownsMemory;
    private LocalContainerEntityManagerFactoryBean factory;

    private Database(String url, boolean ownsMemory) {
        this.url = url;
        this.pool = pool(url);
        this.ownsMemory = ownsMemory;
    }

    /** The database at {@code url}. Nothing connects until something is asked of it. */
    public static Database open(String url) {
        return new Database(url, false);
    }

    /** The database at {@link #DEFAULT_URL}. */
    public static Database open() {
        return open(DEFAULT_URL);
    }

    /**
     * A database of its own in memory, shared by every connection and every thread of this process
     * while it is open, and gone once it is closed. That is what a test or a throwaway schema wants.
     */
    public static Database inMemory() {
        return new Database("jdbc:h2:mem:swagebay-" + MEMORY.incrementAndGet() + ";DB_CLOSE_DELAY=-1", true);
    }

    /** The URL as the driver gets it: H2 is told the schema's column names are not keywords. */
    public static String jdbcUrl(String configured) {
        if (configured.startsWith("jdbc:h2:")
                && !configured.toUpperCase(Locale.ROOT).contains("NON_KEYWORDS")) {
            return configured + ";" + H2_NON_KEYWORDS;
        }
        return configured;
    }

    /** A pool for {@code url}, sized for one process and connecting lazily. */
    private static HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(url));
        config.setPoolName("swagebay");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setInitializationFailTimeout(-1);
        // A database that will not answer is reported within seconds, not after half a minute.
        config.setConnectionTimeout(3_000);
        return new HikariDataSource(config);
    }

    /**
     * An entity manager factory over {@code entities}, not yet built.
     *
     * @param ddl what the provider does to the schema as it starts: {@code validate} for every real
     *     use, {@code create} only to build a schema straight from the mapping for comparison
     */
    static LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(
            DataSource dataSource, String ddl, List<Class<?>> entities) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setManagedTypes(
                PersistenceManagedTypes.of(entities.stream().map(Class::getName).toList(), List.of()));
        bean.setPersistenceUnitName("swagebay");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddl);
        // Columns are laid out in the order they are declared, as the migrations lay them out,
        // rather than by the size of their type.
        properties.put("hibernate.column_ordering_strategy", "legacy");
        // Query parameters are not coerced into the type of the column they meet.
        properties.put("hibernate.jpa.compliance.load_by_id", "true");
        // Dates and times reach the driver as themselves, never through the machine's time zone.
        properties.put("hibernate.type.java_time_use_direct_jdbc", "true");
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    /** The URL as it was given, which is what a person reading a message recognises. */
    public String url() {
        return url;
    }

    /** The pool every connection to this database comes from. */
    public DataSource dataSource() {
        return pool;
    }

    /**
     * The factory units of work are opened from, built and checked against the schema the first
     * time it is asked for.
     */
    public synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            LocalContainerEntityManagerFactoryBean built = entityManagerFactoryBean(pool, "validate", Models.ENTITIES);
            built.afterPropertiesSet();
            factory = built;
        }
        return factory.getNativeEntityManagerFactory();
    }

    /** A unit of work on this database, open and with its transaction begun. */
    public UnitOfWork unitOfWork() {
        return new UnitOfWork(entityManagerFactory()).open();
    }

    /**
     * Run {@code body} in a unit of work and commit when it returns. Leaving it any other way, an
     * exception included, rolls the whole of it back.
     */
    public void transaction(Consumer<? super UnitOfWork> body) {
        try (UnitOfWork work = unitOfWork()) {
            body.accept(work);
            work.commit();
        }
    }

    /** {@link #transaction(Consumer)} for a body with an answer, which is returned once committed. */
    public <T> T transactionResult(Function<? super UnitOfWork, ? extends T> body) {
        try (UnitOfWork work = unitOfWork()) {
            T answer = body.apply(work);
            work.commit();
            return answer;
        }
    }

    /**
     * Build the schema in a database that has none, or bring an older one up to date, by applying
     * every migration it has not had yet. A database already at the latest revision is left alone.
     */
    public void migrate() {
        flyway().migrate();
    }

    /** Take the schema out again, migrations' bookkeeping and everything in the tables with it. */
    public void drop() {
        closeFactory();
        flyway().clean();
    }

    /** The tables in the database, lower case and sorted, the migrations' own bookkeeping aside. */
    public List<String> tableNames() {
        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            TreeSet<String> names = new TreeSet<>();
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String schema = tables.getString("TABLE_SCHEM");
                    if (schema == null || !schema.equalsIgnoreCase("INFORMATION_SCHEMA")) {
                        names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            names.remove(HISTORY_TABLE);
            return List.copyOf(names);
        } catch (SQLException trouble) {
            throw new DatabaseUnavailable(url, trouble);
        }
    }

    /**
     * Close the factory and the pool. A database made in memory by {@link #inMemory()} is gone
     * afterwards. Closing twice does nothing more.
     */
    @Override
    public synchronized void close() {
        closeFactory();
        if (pool.isClosed()) {
            return;
        }
        pool.close();
        if (ownsMemory) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(url));
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException trouble) {
                throw new DatabaseUnavailable(url, trouble);
            }
        }
    }

    private synchronized void closeFactory() {
        if (factory != null) {
            factory.destroy();
            factory = null;
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(pool)
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }
}
