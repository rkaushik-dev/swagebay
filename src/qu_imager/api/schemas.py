"""What goes over the wire, and how the value objects cross it.

A length is whole micrometres inside this package and it is not micrometres to
anybody else. A client asking for a technique sheet wants millimetres, and a
client in Texas wants inches, so both go out and neither is the store. The
schema carries a length as an object with both readings on it rather than as a
bare number, which costs a few bytes and removes an entire class of unit
confusion from every integration anybody ever writes against this.

Requests take the value as text rather than as a number, because a client
sending a wall thickness should not have to know which unit this package
happens to prefer. Parsing is done by the same parser the rest of the package
uses, so "0.375 in" works in a request body exactly as it works in a procedure.

Nothing here reaches into the database and nothing computes. A schema that
computes is a rule that lives in two places.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from qu_imager.quantities.activity import Activity
from qu_imager.quantities.duration import Duration
from qu_imager.quantities.length import Length


class LengthOut(BaseModel):
    """A length, in both systems, so nobody has to convert."""

    model_config = ConfigDict(frozen=True)

    um: int
    mm: Decimal
    inch: Decimal

    @classmethod
    def of(cls, value: Length) -> LengthOut:
        return cls(um=value.um, mm=value.mm, inch=value.inch)


class ActivityOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    bq: int
    curie: Decimal
    tbq: Decimal

    @classmethod
    def of(cls, value: Activity) -> ActivityOut:
        return cls(bq=value.bq, curie=value.curie, tbq=value.tbq)


class DurationOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    ms: int
    seconds: Decimal
    timer: str

    @classmethod
    def of(cls, value: Duration) -> DurationOut:
        return cls(ms=value.ms, seconds=value.seconds, timer=value.as_timer())


class LengthIn(BaseModel):
    """A length as a client writes it, parsed by the package's own parser."""

    model_config = ConfigDict(frozen=True)

    text: str = Field(min_length=1, max_length=40, examples=["9.53 mm", "0.375 in"])

    @field_validator("text")
    @classmethod
    def parses(cls, value: str) -> str:
        Length.parse(value)
        return value

    def to_length(self) -> Length:
        return Length.parse(self.text)


class JointIn(BaseModel):
    """A joint as a work order describes it."""

    model_config = ConfigDict(frozen=True)

    material: str = Field(min_length=1, max_length=40)
    wall: LengthIn
    outside_diameter: LengthIn | None = None
    reinforcement: LengthIn | None = None


class NewJobIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    client: str = Field(min_length=1, max_length=120)
    site: str = Field(min_length=1, max_length=120)
    acceptance_code: str = Field(min_length=1, max_length=40)
    procedure_number: str = Field(min_length=1, max_length=40)
    opened_on: date


class JobOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    number: str
    client: str
    site: str
    acceptance_code: str
    procedure_number: str
    opened_on: date
    closed_on: date | None
    welds: int


class NewWeldIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    weld: str = Field(min_length=1, max_length=20, examples=["GW-104", "GW-104R1"])
    joint: JointIn
    weld_length: LengthIn
    welder: str = Field(min_length=1, max_length=40)
    welded_on: date


class WeldOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    weld: str
    material: str
    wall: LengthOut
    outside_diameter: LengthOut | None
    reinforcement: LengthOut
    weld_length: LengthOut
    welder: str
    welded_on: date
    radiographs: int


class RadiographOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    number: str
    weld: str
    exposure_index: int
    exposed_at: datetime
    technique_reference: str
    medium: str
    admissible: bool
    inadmissible_reason: str | None
    read_by: str | None
    read_at: datetime | None
    indications: int


class FindingOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    clause: str
    kind: str
    reason: str


class VerdictOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    code: str
    disposition: str
    findings: tuple[DoctorFindingOut, ...]


class OutstandingOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    job: str
    items: tuple[str, ...]

    @property
    def is_clear(self) -> bool:
        return not self.items


class NewSourceIn(BaseModel):
    """A source as its certificate reads."""

    model_config = ConfigDict(frozen=True)

    serial: str = Field(min_length=1, max_length=40)
    isotope: str = Field(min_length=1, max_length=20)
    assay_curies: Decimal = Field(gt=0)
    assay_at: datetime
    manufacturer: str = Field(min_length=1, max_length=80)
    model: str = Field(min_length=1, max_length=80)
    active_diameter: LengthIn
    active_length: LengthIn
    working_floor_curies: Decimal = Field(default=Decimal(15), gt=0)


class SourceOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    serial: str
    isotope: str
    assay_activity: ActivityOut
    assay_at: datetime
    state: str
    location: str
    focal_size: LengthOut


class FitnessOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    serial: str
    on: date
    fit: bool
    reasons: tuple[str, ...]


class BookingIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    device_serial: str = Field(min_length=1, max_length=40)
    crew_badge: str = Field(min_length=1, max_length=20)
    site: str = Field(min_length=1, max_length=120)
    went_out: date


class ReturnIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    came_back: date
    exposures: int = Field(default=0, ge=0)


class PlanIn(BaseModel):
    """The decisions a plan needs that are not on the record."""

    model_config = ConfigDict(frozen=True)

    source_serial: str = Field(min_length=1, max_length=40)
    film: str = Field(min_length=1, max_length=20)
    shot_at: datetime
    shot: str | None = None
    indicator_side: str = Field(default="source side")
    window_minutes: int | None = Field(default=None, gt=0)
    density: Decimal = Field(default=Decimal("2.0"), gt=0)


class CoverageOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    exposures: int
    separation_degrees: Decimal
    overlap_degrees: Decimal


class SheetOut(BaseModel):
    """The technique sheet, as a client reads it."""

    model_config = ConfigDict(frozen=True)

    weld: str
    shot: str
    penetrated: LengthOut
    source_to_film: LengthOut
    geometric_unsharpness: LengthOut
    indicator: str
    coverage: CoverageOut
    exposure: DurationOut
    total_source_out: DurationOut
    activity: ActivityOut
    warnings: tuple[str, ...]
    clean: bool


class IndicatorEvidenceIn(BaseModel):
    """What the reader could see of the indicator."""

    model_config = ConfigDict(frozen=True)

    hole_identity: int | None = None
    hole_visible: bool = False
    wire_number: int | None = None
    finest_wire_visible: int | None = None


class DensityReadingIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    label: str = Field(min_length=1, max_length=80)
    density: Decimal = Field(gt=0)
    through_indicator: bool = False


class CalibrationIn(BaseModel):
    """The densitometer check behind the readings."""

    model_config = ConfigDict(frozen=True)

    checked_at: datetime
    step_nominal: Decimal = Field(gt=0)
    step_read: Decimal = Field(gt=0)
    instrument: str = Field(min_length=1, max_length=80)


class ShotIn(BaseModel):
    """One exposure, as the crew reports it."""

    model_config = ConfigDict(frozen=True)

    exposure_index: int = Field(ge=1)
    exposed_at: datetime
    technique_reference: str = Field(min_length=1, max_length=80)
    medium: str = Field(default="film")
    film: str = Field(default="d7")
    penetrated: LengthIn
    technique_class: str = Field(default="A")
    indicator: IndicatorEvidenceIn
    densities: tuple[DensityReadingIn, ...] = ()
    calibration: CalibrationIn | None = None
    gamma: bool = True
    mean_grey: Decimal | None = None
    noise: Decimal | None = None
    resolution: LengthIn | None = None


class ShotOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    number: str
    admissible: bool
    reasons: tuple[str, ...]
    gates_run: tuple[str, ...]


class IndicationIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    kind: str = Field(min_length=1, max_length=8)
    starts_at: LengthIn
    length: LengthIn
    width: LengthIn | None = None
    depth_fraction: Decimal | None = None
    clock: str | None = None


class ReadingIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    reader_badge: str = Field(min_length=1, max_length=20)
    read_at: datetime
    indications: tuple[IndicationIn, ...] = ()


class BoundaryIn(BaseModel):
    """A source and what is round it, for working out the rope."""

    model_config = ConfigDict(frozen=True)

    isotope: str = Field(min_length=1, max_length=20)
    curies: Decimal = Field(gt=0)
    collimator: str | None = None
    barrier_material: str | None = None
    barrier_thickness: LengthIn | None = None


class BoundaryOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    kind: str
    rate_mr_per_hour: Decimal
    down_beam: LengthOut
    off_beam: LengthOut


class RopeOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    boundaries: tuple[BoundaryOut, ...]
    directional: bool


class SurveyPointIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    label: str = Field(min_length=1, max_length=80)
    distance: LengthIn
    bearing_degrees: int = 0
    mr_per_hour: Decimal = Field(ge=0)
    at_device_surface: bool = False


class SurveyIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    stage: str = Field(pattern="^(before|after)$")
    performed_at: datetime
    performed_by: str = Field(min_length=1, max_length=40)
    instrument: str = Field(min_length=1, max_length=80)
    calibration_due: datetime
    points: tuple[SurveyPointIn, ...] = ()


class SurveyOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    radiograph: str
    stage: str
    clear: bool
    faults: tuple[str, ...]
    hottest_label: str
    hottest_mr_per_hour: Decimal


class WeldSummaryOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    weld: str
    wall_mm: Decimal
    welder: str
    radiographs: int
    disposition: str
    clauses: tuple[str, ...]


class PackageOut(BaseModel):
    """The report package, as a client reads it."""

    model_config = ConfigDict(frozen=True)

    job: str
    client: str
    site: str
    code: str
    procedure: str
    opened_on: date
    closed_on: date | None
    complete: bool
    faults: tuple[str, ...]
    welds_shot: int
    welds_repaired: int
    repair_rate: Decimal
    repair_rate_reportable: bool
    radiographs: int
    welds: tuple[WeldSummaryOut, ...]


class RadiographPageOut(BaseModel):
    """One page of radiographs, with where the next one starts."""

    model_config = ConfigDict(frozen=True)

    items: tuple[RadiographOut, ...]
    next_cursor: str | None
    last: bool


class IncidentIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    reference: str = Field(min_length=1, max_length=40)
    occurrence: str = Field(min_length=1, max_length=60)
    discovered_at: datetime
    site: str = Field(min_length=1, max_length=120)
    reported_by: str = Field(min_length=1, max_length=40)
    account: str = Field(min_length=1, max_length=2000)


class IncidentOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    reference: str
    occurrence: str
    discovered_at: datetime
    due_at: datetime
    site: str
    notified_at: datetime | None
    late: bool


class InstrumentCalibrationIn(BaseModel):
    """A calibration certificate against one instrument.

    Named for the instrument rather than just Calibration, because the
    densitometer check the shot endpoint takes is also a calibration and the
    two were briefly the same name in one file.
    """

    model_config = ConfigDict(frozen=True)

    serial: str = Field(min_length=1, max_length=40)
    instrument: str = Field(min_length=1, max_length=40)
    calibrated_on: date
    due_on: date
    laboratory: str = Field(min_length=1, max_length=120)
    certificate: str = Field(default="", max_length=60)


class CalibrationOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    serial: str
    instrument: str
    calibrated_on: date
    due_on: date
    laboratory: str


class RereadIn(BaseModel):
    """A second reading of the same film. The reason is not optional."""

    model_config = ConfigDict(frozen=True)

    reader_badge: str = Field(min_length=1, max_length=20)
    read_at: datetime
    reason: str = Field(min_length=1, max_length=200)
    indications: tuple[IndicationIn, ...] = ()


class RereadOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    radiograph: str
    readers: tuple[str, ...]
    changed: bool


class EntryOut(BaseModel):
    """One line of the audit trail as it goes over the wire."""

    model_config = ConfigDict(frozen=True)

    at: datetime
    actor: str
    action: str
    field_name: str
    was: str
    became: str
    reason: str


class HistoryOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    record: str
    identity: str
    entries: tuple[EntryOut, ...]
    after_signing: int


class DefectIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    item: str = Field(min_length=1, max_length=30)
    account: str = Field(min_length=1, max_length=400)


class InspectionIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    performed_on: date
    performed_by: str = Field(min_length=1, max_length=20)
    is_quarterly: bool = False
    defects: tuple[DefectIn, ...] = ()
    notes: str = Field(default="", max_length=400)


class InspectionOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    device_serial: str
    performed_on: date
    is_quarterly: bool
    passed: bool
    blocking: tuple[str, ...]
    noted: tuple[str, ...]
    expires_on: date | None


class ClearanceOut(BaseModel):
    """Why the truck is not moving, kept apart by what has to be fixed."""

    model_config = ConfigDict(frozen=True)

    device_serial: str
    source_serial: str
    day: date
    cleared: bool
    source_reasons: tuple[str, ...]
    device_reasons: tuple[str, ...]
    crew_reasons: tuple[str, ...]


class ReportIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    reference: str = Field(min_length=1, max_length=40)
    job: str = Field(min_length=1, max_length=20)
    item: str = Field(min_length=1, max_length=60)
    requirement: str = Field(min_length=1, max_length=120)
    account: str = Field(min_length=1)
    raised_on: date
    raised_by: str = Field(min_length=1, max_length=20)


class DecisionIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    disposition: str = Field(min_length=1, max_length=20)
    concession: str = Field(default="", max_length=60)
    removal: str = Field(default="", max_length=60)


class CloseIn(BaseModel):
    model_config = ConfigDict(frozen=True)

    closed_on: date
    closed_by: str = Field(min_length=1, max_length=20)


class ReportOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    reference: str
    job: str
    item: str
    requirement: str
    disposition: str | None
    concession: str
    removal: str
    raised_on: date
    raised_by: str
    closed_on: date | None
    closed_by: str | None
    days_open: int
    overdue: bool


class MorningOut(BaseModel):
    """The morning, in the order it has to be worked through.

    Not SheetOut. That name was already taken by the technique sheet, which
    is a different sheet entirely, and two response models with one name is
    an openapi document with one of them missing.
    """

    model_config = ConfigDict(frozen=True)

    day: date
    stops_today: tuple[str, ...]
    due_this_week: tuple[str, ...]
    running_late: tuple[str, ...]
    films_waiting: tuple[str, ...]
    loads_ready: tuple[str, ...]
    quiet: bool


class ChecklistOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    job: str
    clear: bool
    work_reasons: tuple[str, ...]
    report_reasons: tuple[str, ...]
    image_reasons: tuple[str, ...]


class DoctorFindingOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    check: str
    subject: str
    account: str


class DoctorOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    checks_run: tuple[str, ...]
    checks_available: tuple[str, ...]
    healthy: bool
    findings: tuple[DoctorFindingOut, ...]
