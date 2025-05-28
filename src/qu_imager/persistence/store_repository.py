"""Storing and reading back the image files a job produced.

Ingest is the only write. There is no update and no delete, for the same reason
the audit table has none: a file whose digest can be edited proves nothing at
all. A file that has to come out of the store is superseded by a revision, and
both stay.

The manifest is assembled here rather than in the domain, because the domain
manifest works on stored images and knows nothing about rows, and keeping the
translation in one direction means the rules only ever see the shape they were
written against.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from qu_imager.errors import ConflictError, NotFoundError
from qu_imager.identity import JobNumber
from qu_imager.imaging.store import Format, Manifest, Purpose, StoredImage
from qu_imager.persistence.models.store import StoredImageRow
from qu_imager.persistence.models.work import JobRow, RadiographRow, WeldRow


def row_to_image(row: StoredImageRow, radiograph_number: str) -> StoredImage:
    return StoredImage(
        radiograph_number=radiograph_number,
        filename=row.filename,
        image_format=Format(row.image_format),
        bit_depth=row.bit_depth,
        width=row.width,
        height=row.height,
        byte_size=row.byte_size,
        digest=row.digest,
        captured_at=row.captured_at,
        purpose=Purpose(row.purpose),
    )


class StoreRepository:
    """Ingest, and the two questions the store is asked."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def _radiograph(self, number: str) -> RadiographRow:
        row = self.session.scalars(
            select(RadiographRow).where(RadiographRow.number == number)
        ).first()
        if row is None:
            raise NotFoundError("radiograph", number)
        return row

    def ingest(self, image: StoredImage, at: datetime, by: str) -> StoredImageRow:
        radiograph = self._radiograph(image.radiograph_number)
        held = self.find(image.filename)
        if held is not None:
            if held.digest == image.digest:
                return held
            raise ConflictError(
                "that name is already in the store against different bytes",
                filename=image.filename,
            )
        row = StoredImageRow(
            radiograph_id=radiograph.id,
            filename=image.filename,
            image_format=image.image_format.value,
            purpose=image.purpose.value,
            revision=image.revision,
            bit_depth=image.bit_depth,
            width=image.width,
            height=image.height,
            byte_size=image.byte_size,
            digest=image.digest,
            captured_at=image.captured_at,
            ingested_at=at,
            ingested_by=by,
        )
        self.session.add(row)
        return row

    def find(self, filename: str) -> StoredImageRow | None:
        return self.session.scalars(
            select(StoredImageRow).where(StoredImageRow.filename == filename)
        ).first()

    def get(self, filename: str) -> StoredImageRow:
        row = self.find(filename)
        if row is None:
            raise NotFoundError("stored image", filename)
        return row

    def for_radiograph(self, number: str) -> tuple[StoredImage, ...]:
        radiograph = self._radiograph(number)
        rows = self.session.scalars(
            select(StoredImageRow)
            .where(StoredImageRow.radiograph_id == radiograph.id)
            .order_by(StoredImageRow.revision)
        ).all()
        return tuple(row_to_image(row, number) for row in rows)

    def manifest_for(self, job: JobNumber) -> Manifest:
        """Every file the job produced, assembled into the domain manifest."""
        pairs = self.session.execute(
            select(StoredImageRow, RadiographRow.number)
            .join(RadiographRow, RadiographRow.id == StoredImageRow.radiograph_id)
            .join(WeldRow, WeldRow.id == RadiographRow.weld_id)
            .join(JobRow, JobRow.id == WeldRow.job_id)
            .where(JobRow.year == job.year, JobRow.serial == job.serial)
            .order_by(StoredImageRow.filename)
        ).all()
        manifest = Manifest(job=str(job))
        for row, number in pairs:
            manifest.add(row_to_image(row, number))
        return manifest
