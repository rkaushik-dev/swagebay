package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.ConflictError;
import io.github.rkaushikdev.swagebay.Context;
import io.github.rkaushikdev.swagebay.NotFoundError;
import io.github.rkaushikdev.swagebay.identity.JobNumber;
import io.github.rkaushikdev.swagebay.imaging.ImageFormat;
import io.github.rkaushikdev.swagebay.imaging.Manifest;
import io.github.rkaushikdev.swagebay.imaging.Purpose;
import io.github.rkaushikdev.swagebay.imaging.StoredImage;
import io.github.rkaushikdev.swagebay.persistence.models.RadiographRow;
import io.github.rkaushikdev.swagebay.persistence.models.StoredImageRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Storing, and reading back, the image files a job produced.
 *
 * <p>Ingest is the only write. There is no update and no delete, for the same reason the audit table
 * has none: a file whose digest can be edited proves nothing at all. A file that has to come out of
 * the store is superseded by a revision, and both stay.
 *
 * <p>The manifest is assembled here rather than in the domain, because the domain manifest works on
 * stored images and knows nothing about rows, and keeping the translation in one direction means
 * the rules only ever see the shape they were written against.
 */
public final class StoreRepository implements Repository {

    private final EntityManager entityManager;

    /** The repository on {@code entityManager}, which it shares with the rest of the unit of work. */
    public StoreRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager entityManager() {
        return entityManager;
    }

    /** A row back into the stored image, against the radiograph with that number. */
    public static StoredImage rowToImage(StoredImageRow row, String radiographNumber) {
        return new StoredImage(
                radiographNumber,
                row.getFilename(),
                ImageFormat.fromValue(row.getImageFormat()),
                row.getBitDepth(),
                row.getWidth(),
                row.getHeight(),
                row.getByteSize(),
                row.getDigest(),
                row.getCapturedAt(),
                Purpose.fromValue(row.getPurpose()));
    }

    /**
     * Put a file in the store against its exposure, stamped with who did it and when. The same file
     * ingested again lands once: the row already held comes back.
     *
     * @throws NotFoundError for an exposure nobody shot
     * @throws ConflictError for a name already in the store against different bytes
     */
    public StoredImageRow ingest(StoredImage image, LocalDateTime at, String by) {
        RadiographRow radiograph = radiograph(image.radiographNumber());
        Optional<StoredImageRow> held = find(image.filename());
        if (held.isPresent()) {
            if (held.get().getDigest().equals(image.digest())) {
                return held.get();
            }
            throw new ConflictError(
                    "that name is already in the store against different bytes",
                    Context.of("filename", image.filename()));
        }
        StoredImageRow row = new StoredImageRow();
        row.setRadiograph(radiograph);
        row.setFilename(image.filename());
        row.setImageFormat(image.imageFormat().value());
        row.setPurpose(image.purpose().value());
        row.setRevision(image.revision());
        row.setBitDepth(image.bitDepth());
        row.setWidth(image.width());
        row.setHeight(image.height());
        row.setByteSize(image.byteSize());
        row.setDigest(image.digest());
        row.setCapturedAt(image.capturedAt());
        row.setIngestedAt(at);
        row.setIngestedBy(by);
        entityManager.persist(row);
        return row;
    }

    /** The file with that name, if the store holds it. */
    public Optional<StoredImageRow> find(String filename) {
        return Queries.first(entityManager
                .createQuery("select i from StoredImageRow i where i.filename = :filename", StoredImageRow.class)
                .setParameter("filename", filename));
    }

    /**
     * The file with that name.
     *
     * @throws NotFoundError when the store does not hold it
     */
    public StoredImageRow get(String filename) {
        return find(filename).orElseThrow(() -> new NotFoundError("stored image", filename));
    }

    /**
     * Every file against one exposure, revision by revision.
     *
     * @throws NotFoundError for an exposure nobody shot
     */
    public List<StoredImage> forRadiograph(String number) {
        RadiographRow radiograph = radiograph(number);
        return entityManager
                .createQuery(
                        "select i from StoredImageRow i where i.radiograph = :radiograph order by i.revision",
                        StoredImageRow.class)
                .setParameter("radiograph", radiograph)
                .getResultList()
                .stream()
                .map(row -> rowToImage(row, number))
                .toList();
    }

    /** Every file the job produced, assembled into the domain manifest. */
    public Manifest manifestFor(JobNumber job) {
        Manifest manifest = new Manifest(job.toString());
        entityManager
                .createQuery(
                        "select i, r.number from StoredImageRow i join i.radiograph r join r.weld w join w.job j"
                                + " where j.year = :year and j.serial = :serial order by i.filename",
                        Tuple.class)
                .setParameter("year", job.year())
                .setParameter("serial", job.serial())
                .getResultList()
                .forEach(
                        pair -> manifest.add(rowToImage(pair.get(0, StoredImageRow.class), pair.get(1, String.class))));
        return manifest;
    }

    private RadiographRow radiograph(String number) {
        return Queries.first(entityManager
                        .createQuery("select r from RadiographRow r where r.number = :number", RadiographRow.class)
                        .setParameter("number", number))
                .orElseThrow(() -> new NotFoundError("radiograph", number));
    }
}
