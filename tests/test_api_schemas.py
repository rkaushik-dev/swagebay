from datetime import date
from decimal import Decimal

import pytest
from pydantic import ValidationError as PydanticError

from qu_imager.api.schemas import (
    ActivityOut,
    DurationOut,
    JointIn,
    LengthIn,
    LengthOut,
    NewJobIn,
    NewWeldIn,
    OutstandingOut,
)
from qu_imager.quantities.activity import curie
from qu_imager.quantities.duration import seconds
from qu_imager.quantities.length import inch, mm


def test_a_length_goes_out_in_both_systems():
    out = LengthOut.of(inch("0.375"))
    assert out.um == 9525
    assert out.mm == Decimal("9.525")
    assert out.inch == Decimal("0.375")


def test_an_activity_goes_out_in_both_too():
    out = ActivityOut.of(curie(90))
    assert out.bq == 3_330_000_000_000
    assert out.curie == Decimal(90)
    assert out.tbq == Decimal("3.33")


def test_a_duration_carries_the_timer_string_the_crew_reads():
    out = DurationOut.of(seconds(93))
    assert out.ms == 93000
    assert out.timer == "1:33"


def test_a_length_comes_in_the_way_a_procedure_writes_it():
    assert LengthIn(text="9.53 mm").to_length() == mm("9.53")
    assert LengthIn(text="0.375 in").to_length() == inch("0.375")
    assert LengthIn(text="40 thou").to_length().um == 1016


def test_a_length_that_will_not_parse_is_refused_at_the_edge():
    for bad in ["", "nine mm", "9.53", "9.53 kg"]:
        with pytest.raises(PydanticError):
            LengthIn(text=bad)


def test_a_job_request_needs_every_field_filled_in():
    good = NewJobIn(
        client="c",
        site="s",
        acceptance_code="API 1104",
        procedure_number="RT-001",
        opened_on=date(2024, 9, 26),
    )
    assert good.client == "c"
    with pytest.raises(PydanticError):
        NewJobIn(
            client="",
            site="s",
            acceptance_code="API 1104",
            procedure_number="RT-001",
            opened_on=date(2024, 9, 26),
        )


def test_a_joint_may_have_no_diameter_because_plate_has_none():
    plate = JointIn(material="carbon_steel", wall=LengthIn(text="12 mm"))
    assert plate.outside_diameter is None
    assert plate.reinforcement is None


def test_a_pipe_joint_carries_its_diameter():
    pipe = JointIn(
        material="carbon_steel",
        wall=LengthIn(text="10.97 mm"),
        outside_diameter=LengthIn(text="168.3 mm"),
        reinforcement=LengthIn(text="2 mm"),
    )
    assert pipe.outside_diameter.to_length() == mm("168.3")
    assert pipe.reinforcement.to_length() == mm(2)


def test_a_weld_request_carries_its_joint():
    request = NewWeldIn(
        weld="GW-104",
        joint=JointIn(material="carbon_steel", wall=LengthIn(text="10.97 mm")),
        weld_length=LengthIn(text="529 mm"),
        welder="w-19",
        welded_on=date(2024, 9, 26),
    )
    assert request.weld == "GW-104"
    assert request.weld_length.to_length() == mm(529)


def test_the_schemas_are_frozen_so_a_handler_cannot_edit_a_request():
    request = LengthIn(text="9.53 mm")
    with pytest.raises(PydanticError):
        request.text = "10 mm"


def test_an_outstanding_list_knows_whether_it_is_clear():
    assert OutstandingOut(job="24-0001", items=()).is_clear
    assert not OutstandingOut(job="24-0001", items=("R-001 unread",)).is_clear


def test_a_length_out_serialises_to_json_without_losing_the_decimal():
    body = LengthOut.of(mm("9.53")).model_dump(mode="json")
    assert body["mm"] == "9.53"
    assert body["um"] == 9530


def test_a_welder_and_a_weld_identifier_cannot_be_blank():
    with pytest.raises(PydanticError):
        NewWeldIn(
            weld="",
            joint=JointIn(material="carbon_steel", wall=LengthIn(text="10 mm")),
            weld_length=LengthIn(text="529 mm"),
            welder="w-19",
            welded_on=date(2024, 9, 26),
        )
    with pytest.raises(PydanticError):
        NewWeldIn(
            weld="GW-104",
            joint=JointIn(material="carbon_steel", wall=LengthIn(text="10 mm")),
            weld_length=LengthIn(text="529 mm"),
            welder="",
            welded_on=date(2024, 9, 26),
        )
