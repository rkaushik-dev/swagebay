package io.github.rkaushikdev.swagebay.services;

import static io.github.rkaushikdev.swagebay.quantities.Units.curie;
import static io.github.rkaushikdev.swagebay.quantities.Units.mm;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import io.github.rkaushikdev.swagebay.sources.Isotopes;
import io.github.rkaushikdev.swagebay.sources.SealedSource;
import io.github.rkaushikdev.swagebay.sources.SourceCertificate;
import io.github.rkaushikdev.swagebay.testing.TestDatabase;
import io.github.rkaushikdev.swagebay.testing.WorkFixtures;
import io.github.rkaushikdev.swagebay.util.Text;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How many round trips the listings take, which is a thing a test can hold.
 *
 * <p>Nothing here measures time. A timing assertion on a laptop is a flaky test wearing a suit.
 * What is stable is the number of statements, and the defects worth catching are the ones where a
 * listing goes from a fixed number of queries to one per row without anybody noticing on a database
 * with four rows in it.
 *
 * <p>The counts are ceilings and they are deliberately loose. They are there to fail when a listing
 * starts scaling with the fleet, not to pin the exact statement count of a mapping that is allowed to
 * change its mind. The bounds are the ones the listings have always been held to; the listings run
 * well inside them.
 *
 * <p>The unit of work runs on a session that hands every statement it prepares to a statement
 * inspector, which counts the ones that read. A write flushed on the way through is not a round trip
 * these listings make, and none is made here.
 */
class QueryCountsTest {

    private static final LocalDateTime ASSAY = LocalDateTime.of(2025, 7, 1, 0, 0);
    private static final LocalDate DAY = LocalDate.of(2025, 7, 22);

    private final Statements statements = new Statements();
    private UnitOfWork yard;

    /** How many reads the session has run since this counter was attached. */
    private static final class Counter {
        private int statements;
    }

    /** Passes every statement through unchanged, counting each read against every counter attached so far. */
    private static final class Statements implements StatementInspector {

        private final List<Counter> counters = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            if (Text.strip(sql).toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                counters.forEach(counter -> counter.statements++);
            }
            return sql;
        }

        Counter counted() {
            Counter counter = new Counter();
            counters.add(counter);
            return counter;
        }
    }

    /**
     * A unit of work on the test database whose session reports every statement to {@code inspector}.
     * Every other question put to the factory goes to the database's own factory unchanged.
     */
    private static UnitOfWork inspected(StatementInspector inspector) {
        EntityManagerFactory factory = TestDatabase.fresh().entityManagerFactory();
        SessionFactory sessions = factory.unwrap(SessionFactory.class);
        EntityManagerFactory inspecting = (EntityManagerFactory) Proxy.newProxyInstance(
                QueryCountsTest.class.getClassLoader(),
                new Class<?>[] {EntityManagerFactory.class},
                (proxy, method, arguments) ->
                        method.getName().equals("createEntityManager") && method.getParameterCount() == 0
                                ? sessions.withOptions()
                                        .autoJoinTransactions(true)
                                        .statementInspector(inspector)
                                        .openSession()
                                : method.invoke(factory, arguments));
        return new UnitOfWork(inspecting).open();
    }

    private static SealedSource aSource(String serial) {
        return new SealedSource(
                new SourceCertificate(serial, Isotopes.IR192, curie(90), ASSAY, "QSA", "A424-9", mm(3), mm(3)),
                curie(15));
    }

    private static void inspectedDevice(UnitOfWork work, int index) {
        String serial = String.format(Locale.ROOT, "D-%02d", index);
        SourceService.registerDevice(work, serial, "880d");
        WorkFixtures.passInspections(work, serial, DAY);
    }

    /** Six cameras inspected for the day and four wiped sources, none of it committed. */
    @BeforeEach
    void stockTheYard() {
        yard = inspected(statements);
        for (int index = 1; index <= 6; index++) {
            inspectedDevice(yard, index);
        }
        for (int index = 1; index <= 4; index++) {
            String serial = String.format(Locale.ROOT, "IR-%04d", index);
            SourceService.registerSource(yard, aSource(serial));
            WipeService.recordWipe(yard, serial, ASSAY, BigDecimal.ZERO, "rp");
        }
        yard.flush();
    }

    @AfterEach
    void closeTheYard() {
        yard.close();
    }

    @Test
    void theMorningSheetDoesNotReadOncePerDevice() {
        Counter counter = statements.counted();
        MorningService.sheetFor(yard, DAY);
        assertThat(counter.statements).isPositive().isLessThanOrEqualTo(10);
    }

    @Test
    void theReadyLoadsDoNotReadOncePerPairing() {
        Counter counter = statements.counted();
        List<Load> pairings = MorningService.loadsReady(yard, DAY);
        assertThat(pairings).hasSize(24);
        assertThat(counter.statements).isPositive().isLessThanOrEqualTo(40);
    }

    @Test
    void theClearedDevicesReadTheRegisterOnce() {
        Counter counter = statements.counted();
        assertThat(DispatchService.clearedDevices(yard, "IR-0001", DAY)).hasSize(6);
        assertThat(counter.statements).isPositive().isLessThanOrEqualTo(20);
    }

    @Test
    void theCountsDoNotGrowWithTheFleet() {
        Counter before = statements.counted();
        MorningService.sheetFor(yard, DAY);
        int first = before.statements;
        for (int index = 7; index <= 12; index++) {
            inspectedDevice(yard, index);
        }
        yard.flush();
        Counter after = statements.counted();
        MorningService.sheetFor(yard, DAY);
        assertThat(after.statements).isPositive().isLessThanOrEqualTo(first + 2);
    }
}
