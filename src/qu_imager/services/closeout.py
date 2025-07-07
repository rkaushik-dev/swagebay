"""What has to be true before a job is signed off.

The close check used to live inside the job service and it asked about welds
and films. That was right when welds and films were all there was. It is not
right now, because a job can have every film read and still be nowhere near
finished: an open non conformance, a rejected spool with no removal reference,
an image store that does not match its manifest, a device that went out and
never got its utilisation entry.

So the check is here, in one place, with the categories kept apart. The order
is what somebody can act on soonest, because the list is read top down by a
coordinator with a phone in their hand.

Nothing here writes. It is asked, and the job service refuses on the answer,
which is what lets the same list be printed as a checklist before anybody tries
to close anything.
"""

from __future__ import annotations

from dataclasses import dataclass

from qu_imager.identity import JobNumber
from qu_imager.persistence.unit_of_work import UnitOfWork
from qu_imager.services.jobs import outstanding


@dataclass(frozen=True, slots=True)
class Checklist:
    """Everything between the job and a signature, kept apart by kind."""

    job: JobNumber
    work_reasons: tuple[str, ...]
    report_reasons: tuple[str, ...]
    image_reasons: tuple[str, ...]

    @property
    def reasons(self) -> tuple[str, ...]:
        return self.work_reasons + self.report_reasons + self.image_reasons

    @property
    def is_clear(self) -> bool:
        return not self.reasons

    def describe(self) -> str:
        if self.is_clear:
            return f"{self.job} is ready to close"
        return "; ".join(self.reasons)


def checklist(
    work: UnitOfWork,
    number: JobNumber,
    expected_files: dict[str, str] | None = None,
) -> Checklist:
    """Ask every question at once, the way the gate for a truck does.

    The expected file digests are passed in rather than read off a disk here,
    for the same reason the manifest verify takes them: walking a directory is
    the caller's business and this has to be answerable without one.
    """
    return Checklist(
        job=number,
        work_reasons=outstanding(work, number),
        report_reasons=_report_reasons(work, number),
        image_reasons=_image_reasons(work, number, expected_files),
    )


def _report_reasons(work: UnitOfWork, number: JobNumber) -> tuple[str, ...]:
    register = work.nonconformances.register_for(str(number))
    return register.blocks_closing(str(number))


def _image_reasons(
    work: UnitOfWork, number: JobNumber, expected_files: dict[str, str] | None
) -> tuple[str, ...]:
    manifest = work.store.manifest_for(number)
    if not manifest.entries:
        return ()
    reasons = list(manifest.faults())
    for radiograph in manifest.radiographs_without_a_record_copy():
        reasons.append(f"{radiograph} has no record copy in the store")
    if expected_files is not None:
        found = manifest.verify(expected_files)
        for name in found.missing:
            reasons.append(f"{name} is missing from the store")
        for name in found.altered:
            reasons.append(f"{name} has changed since ingest")
        for name in found.unexpected:
            reasons.append(f"{name} is on the disk and not in the manifest")
    return tuple(reasons)
