"""Mapping welds, radiographs and indications.

Two things here are more awkward than they look and both are worth a note.

A joint is stored across the weld row rather than in a table of its own. That is
a denormalisation and it is the right one, because a joint has no identity
without the weld it belongs to and a joint table would be a second row that can
only ever be joined one to one. The absence of a diameter is stored as a null,
which is what tells plate apart from pipe on the way back out.

An admissibility verdict is stored as a flag and a reason string rather than as
a structure. The structure is a thing this software made up: the gates that
ran, the evidence they ran against. What the client and the auditor need is
whether the film was admissible and if not why, in words. Storing the structure
would make the schema follow the software, and the schema outlives the
software.
"""

from __future__ import annotations

from qu_imager.errors import ConfigurationError
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import by_code
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.identity import ClockPosition, WeldId
from qu_imager.imaging.admissibility import Admissibility
from qu_imager.materials import material
from qu_imager.persistence.models.work import IndicationRow, RadiographRow, WeldRow
from qu_imager.records import Radiograph


def joint_to_row(joint: Joint, weld: WeldId, row: WeldRow) -> WeldRow:
    """Write a joint onto a weld row that already knows its identity."""
    row.prefix = weld.prefix
    row.number = weld.number
    row.repair_round = weld.repair
    row.material_key = joint.material.key
    row.outside_diameter = joint.outside_diameter
    row.wall = joint.wall
    row.reinforcement = joint.reinforcement
    return row


def row_to_joint(row: WeldRow) -> Joint:
    """A weld row back into the joint the planner needs."""
    form = JointForm.PLATE_BUTT if row.outside_diameter is None else JointForm.PIPE_GIRTH
    return Joint(
        form=form,
        wall=row.wall,
        material=material(row.material_key),
        outside_diameter=row.outside_diameter,
        reinforcement=row.reinforcement,
    )


def row_to_weld_id(row: WeldRow) -> WeldId:
    return WeldId(prefix=row.prefix, number=row.number, repair=row.repair_round)


def radiograph_to_row(radiograph: Radiograph, medium: str) -> RadiographRow:
    row = RadiographRow(
        number=radiograph.number,
        exposure_index=radiograph.exposure_index,
        exposed_at=radiograph.exposed_at,
        technique_reference=radiograph.technique_reference,
        medium=medium,
        admissible=radiograph.admissibility.admissible,
        inadmissible_reason=_reason_text(radiograph.admissibility),
        read_by=radiograph.read_by or None,
        read_at=radiograph.read_at,
    )
    for indication in radiograph.indications:
        row.indications.append(indication_to_row(indication))
    return row


def row_to_radiograph(row: RadiographRow) -> Radiograph:
    return Radiograph(
        number=row.number,
        weld=row_to_weld_id(row.weld),
        exposure_index=row.exposure_index,
        exposed_at=row.exposed_at,
        technique_reference=row.technique_reference,
        admissibility=_admissibility(row),
        indications=tuple(row_to_indication(i) for i in row.indications),
        read_by=row.read_by or "",
        read_at=row.read_at,
    )


def indication_to_row(indication: Indication) -> IndicationRow:
    return IndicationRow(
        kind=indication.kind.code,
        starts_at=indication.starts_at,
        length=indication.length,
        width=indication.width,
        depth_fraction=indication.depth_fraction,
        clock_minutes=indication.clock.minutes if indication.clock is not None else None,
    )


def row_to_indication(row: IndicationRow) -> Indication:
    try:
        kind = by_code(row.kind)
    except ValueError as unknown:
        raise ConfigurationError("stored indication code is not a kind", kind=row.kind) from unknown
    return Indication(
        kind=kind,
        starts_at=row.starts_at,
        length=row.length,
        width=row.width,
        depth_fraction=row.depth_fraction,
        clock=ClockPosition(row.clock_minutes) if row.clock_minutes is not None else None,
        radiograph=row.radiograph.number if row.radiograph is not None else "",
    )


def _reason_text(admissibility: Admissibility) -> str | None:
    if admissibility.admissible:
        return None
    return "; ".join(admissibility.reasons)


def _admissibility(row: RadiographRow) -> Admissibility:
    if row.admissible:
        return Admissibility(admissible=True, reasons=(), gates_run=())
    reasons = tuple(r for r in (row.inadmissible_reason or "").split("; ") if r)
    return Admissibility(admissible=False, reasons=reasons, gates_run=())
