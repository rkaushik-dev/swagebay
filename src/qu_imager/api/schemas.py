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
    findings: tuple[FindingOut, ...]


class OutstandingOut(BaseModel):
    model_config = ConfigDict(frozen=True)

    job: str
    items: tuple[str, ...]

    @property
    def is_clear(self) -> bool:
        return not self.items
