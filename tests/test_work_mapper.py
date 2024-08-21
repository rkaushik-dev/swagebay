from datetime import date, datetime
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from qu_imager.errors import ConfigurationError
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.joint import Joint, JointForm
from qu_imager.identity import ClockPosition, WeldId
from qu_imager.imaging.admissibility import Admissibility
from qu_imager.materials import STEEL
from qu_imager.persistence.base import Base
from qu_imager.persistence.models import IndicationRow, JobRow, RadiographRow, WeldRow
from qu_imager.persistence.work_mapper import (
    joint_to_row,
    radiograph_to_row,
    row_to_indication,
    row_to_joint,
    row_to_radiograph,
    row_to_weld_id,
)
from qu_imager.quantities.length import Length, mm
from qu_imager.records import Radiograph

SHOT = datetime(2024, 8, 16, 23, 15)
READ = datetime(2024, 8, 17, 8, 0)
WELD = WeldId.parse("GW-104R1")
GOOD = Admissibility(True, (), ("indicator", "density"))
BAD = Admissibility(False, ("the 2T hole is not visible", "density is thin"), ("indicator",))


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def blank_weld_row():
    return WeldRow(
        prefix="GW",
        number=1,
        material_key="carbon_steel",
        wall=mm(1),
        reinforcement=Length.ZERO,
        weld_length=mm(500),
        welder="w-19",
        welded_on=date(2024, 8, 1),
    )


def persisted(radiograph, joint):
    with session() as db:
        job = JobRow(
            year=24,
            serial=417,
            client="c",
            site="s",
            acceptance_code="API 1104",
            procedure_number="RT-001",
            opened_on=date(2024, 8, 1),
        )
        weld = joint_to_row(joint, WELD, blank_weld_row())
        weld.weld_length = mm(529)
        weld.radiographs.append(radiograph_to_row(radiograph, "film"))
        job.welds.append(weld)
        db.add(job)
        db.commit()
        return row_to_radiograph(db.scalars(select(RadiographRow)).one())


def a_radiograph(admissibility=GOOD, indications=(), read=True):
    return Radiograph(
        number="R-001",
        weld=WELD,
        exposure_index=2,
        exposed_at=SHOT,
        technique_reference="RT-001 rev 1",
        admissibility=admissibility,
        indications=indications,
        read_by="0142" if read else "",
        read_at=READ if read else None,
    )


def test_a_pipe_joint_survives_the_round_trip():
    joint = ps.girth_weld("6", "80", STEEL, reinforcement=mm(2))
    row = joint_to_row(joint, WELD, blank_weld_row())
    recovered = row_to_joint(row)
    assert recovered.od == joint.od
    assert recovered.wall == joint.wall
    assert recovered.reinforcement == mm(2)
    assert recovered.form is JointForm.PIPE_GIRTH


def test_a_plate_joint_comes_back_as_plate_because_the_diameter_is_null():
    plate = Joint(form=JointForm.PLATE_BUTT, wall=mm(12), material=STEEL)
    row = joint_to_row(plate, WELD, blank_weld_row())
    assert row.outside_diameter is None
    assert row_to_joint(row).form is JointForm.PLATE_BUTT


def test_the_weld_identifier_keeps_its_repair_round():
    row = joint_to_row(ps.girth_weld("6", "80", STEEL), WELD, blank_weld_row())
    assert row.repair_round == 1
    assert row_to_weld_id(row) == WELD
    assert str(row_to_weld_id(row)) == "GW-104R1"


def test_a_radiograph_survives_the_database():
    recovered = persisted(a_radiograph(), ps.girth_weld("6", "80", STEEL))
    assert recovered.number == "R-001"
    assert recovered.exposure_index == 2
    assert recovered.weld == WELD
    assert recovered.read_by == "0142"


def test_an_unread_radiograph_comes_back_unread():
    recovered = persisted(a_radiograph(read=False), ps.girth_weld("6", "80", STEEL))
    assert not recovered.has_been_read
    assert recovered.read_by == ""
    assert recovered.read_at is None


def test_an_admissible_radiograph_stores_no_reason():
    row = radiograph_to_row(a_radiograph(), "film")
    assert row.admissible is True
    assert row.inadmissible_reason is None


def test_the_reasons_survive_as_words_and_come_back_split():
    recovered = persisted(
        a_radiograph(admissibility=BAD, read=False), ps.girth_weld("6", "80", STEEL)
    )
    assert not recovered.is_admissible
    assert recovered.admissibility.reasons == (
        "the 2T hole is not visible",
        "density is thin",
    )


def test_indications_survive_with_their_kind_and_position():
    found = (
        Indication(Kind.ESI, mm(100), mm(20), mm(1), clock=ClockPosition.parse("3:00")),
        Indication(Kind.P, mm(200), Length.ZERO, mm(2)),
    )
    recovered = persisted(a_radiograph(indications=found), ps.girth_weld("6", "80", STEEL))
    assert [i.kind for i in recovered.indications] == [Kind.ESI, Kind.P]
    assert recovered.indications[0].clock == ClockPosition.parse("3:00")
    assert recovered.indications[1].width == mm(2)


def test_an_undercut_depth_survives_as_a_decimal():
    found = (Indication(Kind.U, mm(100), mm(20), depth_fraction=Decimal("0.125")),)
    recovered = persisted(a_radiograph(indications=found), ps.girth_weld("6", "80", STEEL))
    assert recovered.indications[0].depth_fraction == Decimal("0.125")


def test_an_indication_with_no_clock_position_comes_back_without_one():
    found = (Indication(Kind.ESI, mm(100), mm(20), mm(1)),)
    recovered = persisted(a_radiograph(indications=found), ps.girth_weld("6", "80", STEEL))
    assert recovered.indications[0].clock is None


def test_the_indication_remembers_which_film_it_was_seen_on():
    found = (Indication(Kind.ESI, mm(100), mm(20), mm(1)),)
    recovered = persisted(a_radiograph(indications=found), ps.girth_weld("6", "80", STEEL))
    assert recovered.indications[0].radiograph == "R-001"


def test_a_stored_code_that_is_not_a_kind_is_refused():
    row = IndicationRow(kind="ZZ", starts_at=mm(1), length=mm(1), width=mm(1))
    with pytest.raises(ConfigurationError):
        row_to_indication(row)
