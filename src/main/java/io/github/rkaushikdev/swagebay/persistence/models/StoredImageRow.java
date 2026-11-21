package io.github.rkaushikdev.swagebay.persistence.models;

import io.github.rkaushikdev.swagebay.persistence.Keyed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One file in the image store, against the exposure it holds.
 *
 * <p>A stored image hangs off the radiograph rather than off the job, because the question asked of
 * it is always about one exposure. The file name is unique across the whole store and not merely
 * within the job, since the name is derived from the radiograph number and radiograph numbers are
 * already unique. Making the constraint global means a file misfiled under the wrong job is caught
 * by the database rather than by whoever notices the count is wrong.
 *
 * <p>The digest is not indexed. It is looked up by name and compared, never searched, and an index
 * on a sixty four character column that is never a predicate is a page cache nobody asked for.
 */
@Entity
@Table(
        name = "stored_image",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_stored_image_filename",
                    columnNames = {"filename"}),
            @UniqueConstraint(
                    name = "uq_stored_image_radiograph_id",
                    columnNames = {"radiograph_id", "revision"})
        },
        indexes = {@Index(name = "ix_stored_image_radiograph_id", columnList = "radiograph_id")})
@Check(name = "ck_stored_image_revisions_count_from_zero", constraints = "revision >= 0")
@Check(name = "ck_stored_image_an_empty_file_is_not_an_image", constraints = "byte_size > 0")
@Check(name = "ck_stored_image_an_image_has_both_dimensions", constraints = "width > 0 AND height > 0")
@Check(name = "ck_stored_image_a_digest_is_sixty_four_characters", constraints = "length(digest) = 64")
public class StoredImageRow extends Keyed {

    @Column(name = "filename", length = 80, nullable = false)
    private String filename;

    @Column(name = "image_format", length = 8, nullable = false)
    private String imageFormat;

    @Column(name = "purpose", length = 10, nullable = false)
    private String purpose;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "bit_depth", nullable = false)
    private Integer bitDepth;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "digest", length = 64, nullable = false)
    private String digest;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Column(name = "ingested_by", length = 20, nullable = false)
    private String ingestedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "radiograph_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stored_image_radiograph_id_radiograph"))
    private RadiographRow radiograph;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getImageFormat() {
        return imageFormat;
    }

    public void setImageFormat(String imageFormat) {
        this.imageFormat = imageFormat;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public void setBitDepth(int bitDepth) {
        this.bitDepth = bitDepth;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public LocalDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(LocalDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getIngestedBy() {
        return ingestedBy;
    }

    public void setIngestedBy(String ingestedBy) {
        this.ingestedBy = ingestedBy;
    }

    /** The row this one hangs off, once it has been attached to one. */
    public Optional<RadiographRow> getRadiograph() {
        return Optional.ofNullable(radiograph);
    }

    public void setRadiograph(RadiographRow radiograph) {
        this.radiograph = radiograph;
    }
}
