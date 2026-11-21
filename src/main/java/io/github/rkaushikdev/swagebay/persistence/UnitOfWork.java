package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ConflictError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

/**
 * One transaction, one entity manager, and every repository built on it.
 *
 * <p>The rule this enforces is that a piece of work either happens completely or not at all, and
 * the piece of work is decided by the caller rather than by whichever repository was called last.
 * Reading a weld, taking a radiograph number, writing the radiograph and appending the indications
 * are one thing. Committing after the number is taken and before the radiograph is written leaves a
 * number issued against nothing, which is exactly the hole in the sequence the counter design
 * exists to prevent.
 *
 * <pre>{@code
 * try (UnitOfWork work = database.unitOfWork()) {
 *     film.setNumber(work.numbering().takeRadiographNumber("24-0417"));
 *     weld.addRadiograph(film);
 *     work.commit();
 * }
 * }</pre>
 *
 * <p>Leaving the block by any route other than the commit rolls back: an exception the caller
 * catches outside, a return from inside the block, an explicit {@link #rollback()}. That is why the
 * commit is the last line of the body rather than something closing does.
 *
 * <p>A unit of work starts closed. {@link #open()} creates the entity manager, begins the
 * transaction and builds every repository on that one entity manager; {@link #close()} rolls back
 * whatever was not committed and puts them all away again. While it is closed the entity manager
 * and every repository refuse to be reached, so nothing can hold a repository that outlived its
 * transaction. A commit begins the next transaction straight away, so work can carry on in the
 * same unit, and what was loaded stays loaded.
 *
 * <p>The entity manager is not hidden. The migrations, the services' own queries and the tests
 * need it; everything else goes through the repositories so that queries stay in one place.
 */
public final class UnitOfWork implements AutoCloseable {

    private static final String NOT_OPEN = "this unit of work is not open";

    private final EntityManagerFactory factory;
    private Opened opened;

    /** A unit of work on {@code factory}, not open yet. */
    public UnitOfWork(EntityManagerFactory factory) {
        this.factory = factory;
    }

    /**
     * Open the unit: a fresh entity manager, its transaction begun, and every repository built on
     * it. Returns this unit, for a try-with-resources block.
     *
     * @throws ConflictError when the unit is open already
     */
    public UnitOfWork open() {
        if (opened != null) {
            throw new ConflictError("this unit of work is already open");
        }
        EntityManager entityManager = factory.createEntityManager();
        entityManager.getTransaction().begin();
        opened = Opened.on(entityManager);
        return this;
    }

    /** Whether the unit is open, between {@link #open()} and {@link #close()}. */
    public boolean isOpen() {
        return opened != null;
    }

    /**
     * The entity manager every repository of this unit shares.
     *
     * @throws ConflictError when the unit is not open
     */
    public EntityManager entityManager() {
        return opened().entityManager();
    }

    /**
     * Sealed sources, the devices they ride in, and their trips.
     *
     * @throws ConflictError when the unit is not open
     */
    public SourceRepository sources() {
        return opened().sources();
    }

    /**
     * Jobs, and the welds under them.
     *
     * @throws ConflictError when the unit is not open
     */
    public JobRepository jobs() {
        return opened().jobs();
    }

    /**
     * Welds and their radiographs.
     *
     * @throws ConflictError when the unit is not open
     */
    public WeldRepository welds() {
        return opened().welds();
    }

    /**
     * The gapless counters, advanced inside this unit's transaction.
     *
     * @throws ConflictError when the unit is not open
     */
    public Numbering numbering() {
        return opened().numbering();
    }

    /**
     * The incident register.
     *
     * @throws ConflictError when the unit is not open
     */
    public IncidentRepository incidents() {
        return opened().incidents();
    }

    /**
     * The calibration register.
     *
     * @throws ConflictError when the unit is not open
     */
    public CalibrationRepository calibrations() {
        return opened().calibrations();
    }

    /**
     * The film on the shelf.
     *
     * @throws ConflictError when the unit is not open
     */
    public StockRepository stock() {
        return opened().stock();
    }

    /**
     * The filing cabinet.
     *
     * @throws ConflictError when the unit is not open
     */
    public ArchiveRepository archive() {
        return opened().archive();
    }

    /**
     * Welder qualifications.
     *
     * @throws ConflictError when the unit is not open
     */
    public QualificationRepository qualifications() {
        return opened().qualifications();
    }

    /**
     * The audit trail.
     *
     * @throws ConflictError when the unit is not open
     */
    public AuditRepository audit() {
        return opened().audit();
    }

    /**
     * The image store.
     *
     * @throws ConflictError when the unit is not open
     */
    public StoreRepository store() {
        return opened().store();
    }

    /**
     * Device inspections.
     *
     * @throws ConflictError when the unit is not open
     */
    public MaintenanceRepository maintenance() {
        return opened().maintenance();
    }

    /**
     * The non conformance register.
     *
     * @throws ConflictError when the unit is not open
     */
    public NonconformanceRepository nonconformances() {
        return opened().nonconformances();
    }

    /**
     * Source exchanges and device uses.
     *
     * @throws ConflictError when the unit is not open
     */
    public ShopRepository shop() {
        return opened().shop();
    }

    /**
     * Make the work so far stick, and begin the next transaction.
     *
     * @throws ConflictError when the unit is not open
     */
    public void commit() {
        EntityTransaction transaction = opened().entityManager().getTransaction();
        transaction.commit();
        transaction.begin();
    }

    /**
     * Undo the work since the last commit, forget everything that was loaded, and begin again.
     *
     * @throws ConflictError when the unit is not open
     */
    public void rollback() {
        EntityManager entityManager = opened().entityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        if (transaction.isActive()) {
            transaction.rollback();
        }
        entityManager.clear();
        transaction.begin();
    }

    /**
     * Write what is pending to the database inside the transaction, without committing it.
     *
     * @throws ConflictError when the unit is not open
     */
    public void flush() {
        opened().entityManager().flush();
    }

    /** Roll back whatever was not committed, close the entity manager and put the repositories away. */
    @Override
    public void close() {
        if (opened == null) {
            return;
        }
        EntityManager entityManager = opened.entityManager();
        opened = null;
        try {
            EntityTransaction transaction = entityManager.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            entityManager.close();
        }
    }

    private Opened opened() {
        if (opened == null) {
            throw new ConflictError(NOT_OPEN);
        }
        return opened;
    }

    /**
     * What exists while the unit is open and not before: the entity manager and every repository
     * built on it. Each repository takes the entity manager when the unit opens, so they all share
     * it and all go away together when it closes. Holding them in one value that exists only while
     * the unit is open keeps the refusal in one place, and leaves opening and closing no list of
     * repositories to keep in step with each other.
     */
    private record Opened(
            EntityManager entityManager,
            SourceRepository sources,
            JobRepository jobs,
            WeldRepository welds,
            Numbering numbering,
            IncidentRepository incidents,
            CalibrationRepository calibrations,
            StockRepository stock,
            ArchiveRepository archive,
            QualificationRepository qualifications,
            AuditRepository audit,
            StoreRepository store,
            MaintenanceRepository maintenance,
            NonconformanceRepository nonconformances,
            ShopRepository shop) {

        static Opened on(EntityManager entityManager) {
            return new Opened(
                    entityManager,
                    new SourceRepository(entityManager),
                    new JobRepository(entityManager),
                    new WeldRepository(entityManager),
                    new Numbering(entityManager),
                    new IncidentRepository(entityManager),
                    new CalibrationRepository(entityManager),
                    new StockRepository(entityManager),
                    new ArchiveRepository(entityManager),
                    new QualificationRepository(entityManager),
                    new AuditRepository(entityManager),
                    new StoreRepository(entityManager),
                    new MaintenanceRepository(entityManager),
                    new NonconformanceRepository(entityManager),
                    new ShopRepository(entityManager));
        }
    }
}
