from datetime import datetime

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.assessment import Disposition
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.admissibility import Admissibility
from qu_imager.quantities.length import mm
from qu_imager.records import Radiograph, WeldRecord

SHOT = datetime(2024, 7, 10, 23, 15)
READ = datetime(2024, 7, 11, 8, 0)
WELD = WeldId.parse("GW-104")

GOOD = Admissibility(admissible=True, reasons=(), gates_run=("indicator", "density"))
BAD = Admissibility(
    admissible=False, reasons=("the 2T hole is not visible",), gates_run=("indicator",)
)


def film(number="R-001", index=1, admissibility=GOOD, indications=(), read=True):
    return Radiograph(
        number=number,
        weld=WELD,
        exposure_index=index,
        exposed_at=SHOT,
        technique_reference="RT-001 rev 1",
        admissibility=admissibility,
        indications=indications,
        read_by="0142" if read else "",
        read_at=READ if read else None,
    )


def weld_record():
    return WeldRecord(
        job=JobNumber.parse("24-0417"),
        weld=WELD,
        weld_length=mm(500),
        wall=mm(10),
        code=Code.API_1104,
    )


def test_a_radiograph_carries_the_technique_it_was_shot_to():
    assert film().technique_reference == "RT-001 rev 1"
    assert film().is_admissible
    assert film().has_been_read


def test_a_radiograph_without_a_technique_reference_is_refused():
    with pytest.raises(ValidationError):
        film(number="R-001").__class__(
            number="R-001",
            weld=WELD,
            exposure_index=1,
            exposed_at=SHOT,
            technique_reference="  ",
            admissibility=GOOD,
        )


def test_a_reading_needs_both_a_reader_and_a_date():
    with pytest.raises(ValidationError):
        Radiograph(
            number="R-001",
            weld=WELD,
            exposure_index=1,
            exposed_at=SHOT,
            technique_reference="RT-001",
            admissibility=GOOD,
            read_by="0142",
        )


def test_indications_cannot_appear_without_somebody_having_read_the_film():
    with pytest.raises(ValidationError):
        Radiograph(
            number="R-001",
            weld=WELD,
            exposure_index=1,
            exposed_at=SHOT,
            technique_reference="RT-001",
            admissibility=GOOD,
            indications=(Indication(Kind.ESI, mm(10), mm(10), mm(1)),),
        )


def test_exposures_are_numbered_from_one():
    with pytest.raises(ValidationError):
        film(index=0)
    with pytest.raises(ValidationError):
        film(number="  ")


def test_a_weld_gathers_its_radiographs():
    record = weld_record()
    record.add(film("R-001", 1))
    record.add(film("R-002", 2))
    assert len(record.radiographs) == 2


def test_a_radiograph_for_another_weld_is_refused():
    record = weld_record()
    other = Radiograph(
        number="R-009",
        weld=WeldId.parse("GW-110"),
        exposure_index=1,
        exposed_at=SHOT,
        technique_reference="RT-001",
        admissibility=GOOD,
    )
    with pytest.raises(ValidationError) as caught:
        record.add(other)
    assert caught.value.context["expected"] == "GW-104"


def test_the_same_number_or_the_same_exposure_twice_is_refused():
    record = weld_record()
    record.add(film("R-001", 1))
    with pytest.raises(ConflictError):
        record.add(film("R-001", 2))
    with pytest.raises(ConflictError):
        record.add(film("R-002", 1))


def test_a_weld_with_every_exposure_read_reaches_a_verdict():
    record = weld_record()
    record.add(film("R-001", 1))
    record.add(film("R-002", 2))
    assert not record.is_incomplete(2)
    assert record.disposition(2) is Disposition.ACCEPT


def test_a_weld_short_of_exposures_reaches_no_verdict():
    record = weld_record()
    record.add(film("R-001", 1))
    assert record.is_incomplete(2)
    assert record.verdict(2) is None
    assert "1 of 2 exposures are not shot" in record.blockers(2)


def test_one_inadmissible_radiograph_stops_the_whole_weld():
    record = weld_record()
    record.add(film("R-001", 1))
    record.add(film("R-002", 2, admissibility=BAD))
    assert record.is_incomplete(2)
    assert record.verdict(2) is None
    assert "R-002 is not admissible and has to be reshot" in record.blockers(2)


def test_an_unread_radiograph_stops_it_too():
    record = weld_record()
    record.add(film("R-001", 1))
    record.add(film("R-002", 2, read=False))
    assert "R-002 has not been read" in record.blockers(2)


def test_indications_from_every_admissible_film_are_judged_together():
    record = weld_record()
    record.add(film("R-001", 1, indications=(Indication(Kind.ESI, mm(10), mm(30), mm(1)),)))
    record.add(film("R-002", 2, indications=(Indication(Kind.ESI, mm(60), mm(30), mm(1)),)))
    assert len(record.all_indications()) == 2
    assert record.disposition(2) is Disposition.REPAIR


def test_neither_film_would_have_failed_on_its_own():
    from qu_imager.evaluation.acceptance import judge
    from qu_imager.evaluation.indication import IndicationSet

    one = IndicationSet(mm(500), (Indication(Kind.ESI, mm(10), mm(30), mm(1)),))
    assert judge(Code.API_1104, one, mm(10)).accepted


def test_indications_on_an_inadmissible_film_are_not_counted():
    record = weld_record()
    record.add(film("R-001", 1))
    record.add(
        film(
            "R-002",
            2,
            admissibility=BAD,
            indications=(Indication(Kind.C, mm(60), mm(30)),),
        )
    )
    assert len(record.all_indications()) == 0


def test_an_exposure_count_of_nothing_is_refused():
    record = weld_record()
    with pytest.raises(ValidationError):
        record.is_incomplete(0)


def test_a_radiograph_prints_its_own_state():
    assert film().describe() == "R-001 read by 0142, 0 indications"
    assert film(read=False).describe() == "R-001 admissible, not yet read"
    assert "not admissible" in film(admissibility=BAD, read=False).describe()
