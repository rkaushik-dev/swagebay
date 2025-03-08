from datetime import date

import pytest

from qu_imager.errors import ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.identity import JobNumber
from qu_imager.reporting.package import Package
from qu_imager.reporting.submittal import (
    DEFAULT_REQUIRED,
    Enclosure,
    Submittal,
    check,
    revise,
)

PREPARED = date(2025, 3, 10)


def a_package(faults=()):
    return Package(
        job=JobNumber(25, 1),
        client="Maritime Pipelines",
        site="Come By Chance",
        code=Code.API_1104,
        procedure_number="RT-001",
        opened_on=date(2025, 3, 1),
        closed_on=None,
        welds=(),
        radiographs=(),
        faults=faults,
    )


def a_submittal(enclosures=None, revision=0):
    return Submittal(
        job="25-0001",
        client="Maritime Pipelines",
        prepared_on=PREPARED,
        prepared_by="0142",
        enclosures=frozenset(enclosures if enclosures is not None else DEFAULT_REQUIRED),
        revision=revision,
    )


def test_a_complete_parcel_is_ready():
    parcel = a_submittal()
    assert parcel.is_ready()
    assert parcel.missing() == ()


def test_a_missing_enclosure_is_named():
    parcel = a_submittal(enclosures=set(DEFAULT_REQUIRED) - {Enclosure.PROCEDURE})
    assert not parcel.is_ready()
    assert parcel.missing() == (Enclosure.PROCEDURE,)


def test_every_missing_enclosure_is_named_not_just_the_first():
    parcel = a_submittal(enclosures={Enclosure.REPORT})
    assert len(parcel.missing()) == 4


def test_something_enclosed_that_was_not_asked_for_is_not_a_fault():
    parcel = a_submittal(enclosures=[*DEFAULT_REQUIRED, Enclosure.WELD_MAP])
    assert parcel.is_ready()
    assert parcel.extra() == (Enclosure.WELD_MAP,)


def test_a_client_that_asks_for_less_gets_less():
    lighter = (Enclosure.REPORT, Enclosure.RADIOGRAPHS)
    parcel = a_submittal(enclosures={Enclosure.REPORT, Enclosure.RADIOGRAPHS})
    assert parcel.is_ready(lighter)
    assert not parcel.is_ready()


def test_a_client_that_asks_for_more_is_checked_against_that():
    heavier = (*DEFAULT_REQUIRED, Enclosure.WELD_MAP, Enclosure.REPAIR_RECORD)
    parcel = a_submittal()
    assert parcel.is_ready()
    assert parcel.missing(heavier) == (Enclosure.WELD_MAP, Enclosure.REPAIR_RECORD)


def test_a_submittal_is_signed_and_has_a_sane_revision():
    with pytest.raises(ValidationError):
        Submittal("25-0001", "c", PREPARED, "  ", frozenset())
    with pytest.raises(ValidationError):
        Submittal("25-0001", "c", PREPARED, "0142", frozenset(), revision=-1)


def test_a_sound_package_in_a_complete_parcel_is_ready_to_go():
    readiness = check(a_package(), a_submittal())
    assert readiness.is_ready
    assert readiness.describe() == "ready to go"


def test_a_package_fault_stops_a_complete_parcel():
    readiness = check(a_package(faults=("GW-104 has no radiographs",)), a_submittal())
    assert not readiness.is_ready
    assert "GW-104 has no radiographs" in readiness.describe()


def test_a_missing_enclosure_stops_a_sound_package():
    parcel = a_submittal(enclosures=set(DEFAULT_REQUIRED) - {Enclosure.CALIBRATIONS})
    readiness = check(a_package(), parcel)
    assert not readiness.is_ready
    assert "no equipment calibration certificates enclosed" in readiness.describe()


def test_both_kinds_of_fault_are_reported_together():
    parcel = a_submittal(enclosures={Enclosure.REPORT})
    readiness = check(a_package(faults=("R-001 has not been read",)), parcel)
    assert len(readiness.package_faults) == 1
    assert len(readiness.missing_enclosures) == 4


def test_a_resubmittal_is_a_new_revision_rather_than_an_edit():
    first = a_submittal()
    second = revise(first, frozenset(DEFAULT_REQUIRED), date(2025, 4, 1))
    assert first.revision == 0
    assert second.revision == 1
    assert second.prepared_on == date(2025, 4, 1)
    assert second.job == first.job


def test_revisions_keep_stacking():
    parcel = a_submittal()
    for expected in (1, 2, 3):
        parcel = revise(parcel, parcel.enclosures, PREPARED)
        assert parcel.revision == expected


def test_the_covering_note_lists_what_is_in_the_parcel():
    note = a_submittal().covering_note()
    assert "Job 25-0001 for Maritime Pipelines" in note
    assert "Prepared 2025-03-10 by 0142" in note
    assert "the radiographs" in note
    assert "Submittal revision" not in note


def test_a_revised_parcel_says_so_on_the_note():
    note = a_submittal(revision=2).covering_note()
    assert "Submittal revision 2" in note
