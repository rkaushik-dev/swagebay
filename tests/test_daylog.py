from datetime import date, datetime

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import WeldId
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.dose import mr_per_hour
from qu_imager.quantities.length import Length, mm
from qu_imager.safety.survey import SurveyStage
from qu_imager.services.daylog import (
    DayLog,
    Note,
    gaps_on,
    shots_on,
    summary,
    surveys_on,
    welds_shot_on,
)
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.shooting import ShotRecord, record_shot
from qu_imager.services.surveys import Point, record_survey

from conftest import database, film_evidence

DAY = date(2025, 3, 3)
SHOT = datetime(2025, 3, 3, 23, 30)
WELD = WeldId.parse("GW-104")


def a_log(crew=("0142", "0201")):
    return DayLog(
        day=DAY,
        site="Come By Chance",
        crew=crew,
        device_serial="880D-221",
        source_serial="IR-5512",
        weather="minus eight, clear",
    )


def prepared(work, shots=1, visible=True):
    job = open_job(
        work,
        NewJob(
            client="c",
            site="s",
            code=Code.API_1104,
            procedure_number="RT-001",
            opened_on=date(2025, 3, 1),
        ),
    )
    add_weld(
        work,
        job,
        NewWeld(
            weld=WELD,
            joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
            weld_length=mm(529),
            welder="w-19",
            welded_on=date(2025, 3, 1),
        ),
    )
    numbers = []
    for index in range(1, shots + 1):
        numbers.append(
            record_shot(
                work,
                job,
                ShotRecord(
                    weld=WELD,
                    exposure_index=index,
                    exposed_at=SHOT,
                    technique_reference="RT-001 rev 1",
                    evidence=film_evidence(visible=visible, at=SHOT),
                ),
            ).number
        )
    return job, numbers


def test_a_log_names_the_site_and_the_crew():
    with pytest.raises(ValidationError):
        DayLog(DAY, "  ", ("0142",), "880D-221", "IR-5512")
    with pytest.raises(ValidationError):
        DayLog(DAY, "site", (), "880D-221", "IR-5512")


def test_a_log_is_open_until_it_is_closed():
    log = a_log()
    assert not log.is_closed
    log.close(datetime(2025, 3, 4, 6, 0))
    assert log.is_closed


def test_a_log_is_only_closed_once():
    log = a_log()
    log.close(datetime(2025, 3, 4, 6, 0))
    with pytest.raises(ConflictError):
        log.close(datetime(2025, 3, 4, 7, 0))


def test_a_closed_log_is_not_edited():
    log = a_log()
    log.set_account("quiet night")
    log.close(datetime(2025, 3, 4, 6, 0))
    with pytest.raises(ConflictError):
        log.set_account("actually not quiet")
    assert log.account == "quiet night"


def test_an_addition_after_the_close_is_a_note_with_its_own_time():
    log = a_log()
    log.close(datetime(2025, 3, 4, 6, 0))
    log.add_note(Note(datetime(2025, 3, 4, 9, 0), "0142", "the meter was recalibrated"))
    assert len(log.notes) == 1
    assert log.notes[0].written_at > log.closed_at


def test_a_note_cannot_be_backdated_before_the_close():
    log = a_log()
    log.close(datetime(2025, 3, 4, 6, 0))
    with pytest.raises(ValidationError):
        log.add_note(Note(datetime(2025, 3, 4, 5, 0), "0142", "sneaking this in"))


def test_a_note_is_signed_and_says_something():
    with pytest.raises(ValidationError):
        Note(datetime(2025, 3, 4, 9, 0), "  ", "text")
    with pytest.raises(ValidationError):
        Note(datetime(2025, 3, 4, 9, 0), "0142", "  ")


def test_the_shots_on_a_day_come_back_in_time_order():
    made = database()
    with transaction(made) as work:
        prepared(work, shots=3)
        numbers = [row.number for row in shots_on(work, DAY)]
        assert len(numbers) == 3
        assert numbers == sorted(numbers)
        assert shots_on(work, date(2025, 3, 4)) == ()


def test_the_welds_shot_that_day_are_listed_once_each():
    made = database()
    with transaction(made) as work:
        prepared(work, shots=3)
        assert welds_shot_on(work, DAY) == ("GW-104",)


def test_a_shot_with_no_survey_is_a_gap_the_log_has_to_explain():
    made = database()
    with transaction(made) as work:
        _job, numbers = prepared(work)
        assert f"{numbers[0]} has no survey" in gaps_on(work, DAY)


def test_a_surveyed_shot_leaves_no_gap():
    made = database()
    with transaction(made) as work:
        _job, numbers = prepared(work)
        record_survey(
            work,
            numbers[0],
            SurveyStage.AFTER,
            SHOT,
            "0142",
            "Ludlum 3",
            datetime(2025, 12, 1),
            (Point("device", Length.from_m(1), 0, mr_per_hour(1), at_device_surface=True),),
        )
        assert gaps_on(work, DAY) == ()
        assert len(surveys_on(work, DAY)) == 1


def test_an_inadmissible_shot_is_a_gap_too():
    made = database()
    with transaction(made) as work:
        _job, numbers = prepared(work, visible=False)
        assert any("not admissible" in gap for gap in gaps_on(work, DAY))


def test_the_summary_is_assembled_and_the_rest_is_typed():
    made = database()
    with transaction(made) as work:
        prepared(work, shots=2)
        log = a_log()
        log.set_account("source hung once, cleared on the second crank")
        body = summary(work, log)
        assert body["exposures"] == 2
        assert body["welds"] == ("GW-104",)
        assert body["crew"] == ("0142", "0201")
        assert body["closed"] is False
        assert log.account.startswith("source hung")


def test_a_log_prints_the_way_the_handover_reads():
    assert a_log().describe() == "2025-03-03 Come By Chance, 2 on crew, open"
