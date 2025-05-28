"""Digital image files, their names, and proving none of them moved.

A shop that shoots film ends up with a bag of film. A shop that shoots plates
or a panel ends up with a directory, and a directory is much easier to lose
quietly. A file that was renamed by somebody tidying up, a file that was
resaved through a viewer and came back eight bit, a file that is simply gone
because a card was reused: none of those announce themselves.

So three rules.

The name is derived, never typed. A stored image's name comes from the
radiograph number it belongs to, which means a file that turns up with a name
nobody can derive belongs to nothing and gets found on the first check.

The record copy is lossless. A jpeg of a weld is a picture of a radiograph, not
a radiograph, and the difference matters at the point where somebody is trying
to decide whether a faint line is a lack of fusion. Lossy files are allowed in
the store as viewing copies and are never the evidence.

Nothing is trusted without its digest. The manifest carries a hash per file,
taken at ingest, and verification tells apart the three things that can be
wrong: a file that is missing, a file nobody expected, and a file whose
contents are not what they were. Those have three different causes and lumping
them into one count sends everybody looking in the wrong place.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

from qu_imager.errors import ValidationError

DIGEST = re.compile(r"^[0-9a-f]{64}$")
EVALUATION_BIT_DEPTH = 12
NAME = re.compile(r"^(?P<stem>[A-Za-z0-9-]+?)(?:-v(?P<revision>\d+))?\.(?P<suffix>[a-z0-9]+)$")


class Format(Enum):
    """What the bytes are, and whether they can be the record."""

    DICONDE = "dcm"
    TIFF = "tif"
    PNG = "png"
    JPEG = "jpg"

    @property
    def suffix(self) -> str:
        return self.value

    @property
    def is_lossless(self) -> bool:
        return self is not Format.JPEG

    @property
    def carries_technique(self) -> bool:
        """Only DICONDE has somewhere to put the technique with the pixels."""
        return self is Format.DICONDE

    def __str__(self) -> str:
        return self.name.lower()


class Purpose(Enum):
    RECORD = "record"
    VIEWING = "viewing"

    def __str__(self) -> str:
        return self.value


def digest_of(data: bytes) -> str:
    """The hash the manifest stores. One algorithm, named in one place."""
    return hashlib.sha256(data).hexdigest()


def expected_name(radiograph_number: str, image_format: Format, revision: int = 0) -> str:
    """The only name a stored image is allowed to have.

    A revision is a second file for the same exposure, which happens when a
    plate is rescanned at a different pitch. It is a suffix rather than a new
    number, because the exposure did not happen twice.
    """
    if not radiograph_number.strip():
        raise ValidationError("a stored image belongs to a radiograph")
    if revision < 0:
        raise ValidationError("a revision counts up from zero", value=str(revision))
    stem = radiograph_number.strip().upper()
    tail = "" if revision == 0 else f"-v{revision}"
    return f"{stem}{tail}.{image_format.suffix}"


@dataclass(frozen=True, slots=True)
class StoredImage:
    """One file in the store, and everything the manifest needs about it."""

    radiograph_number: str
    filename: str
    image_format: Format
    bit_depth: int
    width: int
    height: int
    byte_size: int
    digest: str
    captured_at: datetime
    purpose: Purpose = Purpose.RECORD

    def __post_init__(self) -> None:
        if not DIGEST.match(self.digest):
            raise ValidationError("a digest is sixty four hex characters", value=self.digest)
        if self.bit_depth not in (8, 12, 14, 16):
            raise ValidationError("that is not a bit depth", value=str(self.bit_depth))
        if self.width <= 0 or self.height <= 0:
            raise ValidationError("an image has both dimensions")
        if self.byte_size <= 0:
            raise ValidationError("an empty file is not an image")
        if self.filename != expected_name(self.radiograph_number, self.image_format, self.revision):
            raise ValidationError(
                "that name cannot be derived from the radiograph it claims",
                filename=self.filename,
                number=self.radiograph_number,
            )

    @property
    def revision(self) -> int:
        match = NAME.match(self.filename)
        if match is None or match.group("revision") is None:
            return 0
        return int(match.group("revision"))

    @property
    def pixels(self) -> int:
        return self.width * self.height

    def describe(self) -> str:
        return (
            f"{self.filename} {self.width}x{self.height} {self.bit_depth} bit "
            f"{self.byte_size} bytes {self.purpose}"
        )


def faults(image: StoredImage) -> tuple[str, ...]:
    """Everything wrong with one file, for what it says it is."""
    found = []
    if image.purpose is Purpose.RECORD and not image.image_format.is_lossless:
        found.append(f"{image.image_format} is lossy and cannot be the record copy")
    if image.purpose is Purpose.RECORD and image.bit_depth < EVALUATION_BIT_DEPTH:
        found.append(
            f"{image.bit_depth} bit is below the {EVALUATION_BIT_DEPTH} bit "
            "an evaluation is made at"
        )
    if image.purpose is Purpose.VIEWING and image.revision == 0:
        found.append("a viewing copy is a revision of the record, not the record")
    return tuple(found)


def is_admissible_file(image: StoredImage) -> bool:
    return not faults(image)


@dataclass(frozen=True, slots=True)
class Discrepancy:
    """What verification found, kept apart by cause rather than counted."""

    missing: tuple[str, ...] = ()
    unexpected: tuple[str, ...] = ()
    altered: tuple[str, ...] = ()

    @property
    def is_clean(self) -> bool:
        return not (self.missing or self.unexpected or self.altered)

    def describe(self) -> str:
        if self.is_clean:
            return "every file is where it was and is what it was"
        parts = []
        if self.missing:
            parts.append(f"{len(self.missing)} missing")
        if self.unexpected:
            parts.append(f"{len(self.unexpected)} not in the manifest")
        if self.altered:
            parts.append(f"{len(self.altered)} changed since ingest")
        return ", ".join(parts)


@dataclass(slots=True)
class Manifest:
    """Every file the store is supposed to hold, with what it hashed to."""

    job: str
    entries: dict[str, StoredImage] = field(default_factory=dict)

    def add(self, image: StoredImage) -> None:
        held = self.entries.get(image.filename)
        if held is not None and held.digest != image.digest:
            raise ValidationError(
                "that name is already in the manifest against different bytes",
                filename=image.filename,
            )
        self.entries[image.filename] = image

    def __len__(self) -> int:
        return len(self.entries)

    def __contains__(self, filename: object) -> bool:
        return filename in self.entries

    def for_radiograph(self, radiograph_number: str) -> tuple[StoredImage, ...]:
        wanted = radiograph_number.strip().upper()
        found = [i for i in self.entries.values() if i.radiograph_number.upper() == wanted]
        return tuple(sorted(found, key=lambda i: i.revision))

    def record_copy(self, radiograph_number: str) -> StoredImage | None:
        for image in self.for_radiograph(radiograph_number):
            if image.purpose is Purpose.RECORD:
                return image
        return None

    def verify(self, seen: dict[str, str]) -> Discrepancy:
        """Compare the manifest against what is actually on the disk.

        The argument is a mapping of file name to digest rather than a
        directory path, because the thing being checked is a set of bytes and
        walking the disk belongs to the caller. That also makes this testable
        without writing files, which is why every case below has a test.
        """
        missing = tuple(sorted(name for name in self.entries if name not in seen))
        unexpected = tuple(sorted(name for name in seen if name not in self.entries))
        altered = tuple(
            sorted(
                name
                for name, image in self.entries.items()
                if name in seen and seen[name] != image.digest
            )
        )
        return Discrepancy(missing=missing, unexpected=unexpected, altered=altered)

    def faults(self) -> tuple[str, ...]:
        """Every file level fault across the manifest, named by file."""
        found = []
        for name in sorted(self.entries):
            for reason in faults(self.entries[name]):
                found.append(f"{name}: {reason}")
        return tuple(found)

    def radiographs_without_a_record_copy(self) -> tuple[str, ...]:
        numbers = {i.radiograph_number.upper() for i in self.entries.values()}
        return tuple(sorted(n for n in numbers if self.record_copy(n) is None))

    def total_bytes(self) -> int:
        return sum(image.byte_size for image in self.entries.values())
