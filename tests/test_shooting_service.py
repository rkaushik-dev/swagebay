from datetime import date, datetime
from decimal import Decimal

import pytest

from qu_imager.errors import ConflictError, NotFoundError, ValidationError
from qu_imager.evaluation.acceptance import Code
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.identity import JobNumber, WeldId
from qu_imager.imaging.admissibility import Evidence, IndicatorEvidence
from qu_imager.imaging.computed import plate
from qu_imager.imaging.density import Calibration, Reading, grade
from qu_imager.imaging.film import film as film_system
from qu_imager.imaging.indicators import HoleIndicator, HoleSize, WireIndicator
from qu_imager.imaging.noise import NoiseMeasurement
from qu_imager.imaging.selection import TechniqueClass
from qu_imager.imaging.system import ImagingSystem
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.length import mm
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, open_job
from qu_imager.services.shooting import (
    ShotRecord,
    exposures_recorded,
    missing_exposures,
    record_shot,
)

from conftest import database

OPENED = date(2024, 9, 16)
SHOT = datetime(2024, 9, 17, 23, 30)
CHECKED = datetime(2024, 9, 17, 18, 0)
ON_FILM = ImagingSystem.from_film(film_system("d7"))
ON_PLATE = ImagingSystem.from_plate(plate("ip1_hd"))


def prepared(work):
    number = open_job(
        work,
        NewJob(
            client="c",
            site="s",
            code=Code.API_1104,
            procedure_number="RT-001",
            opened_on=OPENED,
        ),
    )
    add_weld(
        work,
        number,
        NewWeld(
            weld=WeldId.parse("GW-104"),
            joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
            weld_length=mm(529),
            welder="w-19",
            welded_on=OPENED,
        ),
    )
    return number


def film_evidence(visible=True, density="2.50"):
    return Evidence(
        system=ON_FILM,
        penetrated=mm(26),
        technique_class=TechniqueClass.A,
        indicator=IndicatorEvidence(
            required_hole=HoleIndicator(30),
            required_hole_size=HoleSize.TWO_T,
            hole_visible=visible,
        ),
        density=grade(
            (
                Reading("indicator", Decimal(density), through_indicator=True),
                Reading("weld", Decimal(density)),
            ),
            gamma=True,
            calibration=Calibration(CHECKED, Decimal("2.00"), Decimal("2.01"), "X-Rite"),
            read_at=SHOT,
        ),
    )


def plate_evidence():
    return Evidence(
        system=ON_PLATE,
        penetrated=mm(26),
        technique_class=TechniqueClass.A,
        indicator=IndicatorEvidence(required_wire=WireIndicator(11), finest_wire_visible=12),
        noise=NoiseMeasurement("parent", Decimal(12000), Decimal(100), mm("0.040")),
    )


def a_shot(index=1, evidence=None):
    return ShotRecord(
        weld=WeldId.parse("GW-104"),
        exposure_index=index,
        exposed_at=SHOT,
        technique_reference="RT-001 rev 1",
        evidence=evidence if evidence is not None else film_evidence(),
    )


def test_a_shot_is_numbered_and_stored():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        result = record_shot(work, job, a_shot())
        assert result.number == "24-0001-R0001"
        assert not result.needs_reshooting


def test_the_numbers_run_on_for_the_job():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        first = record_shot(work, job, a_shot(1))
        second = record_shot(work, job, a_shot(2))
        assert first.number == "24-0001-R0001"
        assert second.number == "24-0001-R0002"


def test_the_same_exposure_twice_on_one_weld_is_refused():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        record_shot(work, job, a_shot(1))
        with pytest.raises(ConflictError) as caught:
            record_shot(work, job, a_shot(1))
        assert caught.value.context["exposure"] == 1


def test_an_inadmissible_shot_is_still_recorded_and_still_numbered():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        result = record_shot(work, job, a_shot(evidence=film_evidence(visible=False)))
        assert result.needs_reshooting
        assert result.number == "24-0001-R0001"
        stored = work.welds.radiograph(result.number)
        assert stored.admissible is False
        assert "not visible" in stored.inadmissible_reason


def test_the_density_reading_is_stored_with_the_film():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        result = record_shot(work, job, a_shot())
        stored = work.welds.radiograph(result.number)
        assert stored.density_through_indicator == Decimal("2.50")
        assert stored.normalised_snr is None
        assert stored.medium == "film"


def test_a_digital_shot_stores_the_noise_figure_instead():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        result = record_shot(work, job, a_shot(evidence=plate_evidence()))
        stored = work.welds.radiograph(result.number)
        assert stored.density_through_indicator is None
        assert stored.normalised_snr > Decimal(100)
        assert stored.medium == "computed radiography"


def test_the_verdict_is_computed_here_and_not_handed_in():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        thin = record_shot(work, job, a_shot(evidence=film_evidence(density="1.40")))
        assert thin.needs_reshooting
        assert any("floor" in reason for reason in thin.admissibility.reasons)


def test_a_shot_against_a_weld_that_is_not_there_is_refused():
    made = database()
    with pytest.raises(NotFoundError), transaction(made) as work:
        job = prepared(work)
        record_shot(
            work,
            job,
            ShotRecord(
                weld=WeldId.parse("GW-999"),
                exposure_index=1,
                exposed_at=SHOT,
                technique_reference="RT-001 rev 1",
                evidence=film_evidence(),
            ),
        )


def test_a_shot_against_a_job_that_is_not_there_is_refused():
    made = database()
    with pytest.raises(NotFoundError), transaction(made) as work:
        prepared(work)
        record_shot(work, JobNumber(24, 99), a_shot())


def test_a_shot_needs_an_exposure_number_and_a_technique():
    with pytest.raises(ValidationError):
        a_shot(index=0)
    with pytest.raises(ValidationError):
        ShotRecord(
            weld=WeldId.parse("GW-104"),
            exposure_index=1,
            exposed_at=SHOT,
            technique_reference="  ",
            evidence=film_evidence(),
        )


def test_the_exposures_recorded_so_far_come_back_in_order():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        record_shot(work, job, a_shot(3))
        record_shot(work, job, a_shot(1))
        assert exposures_recorded(work, job, WeldId.parse("GW-104")) == (1, 3)


def test_the_missing_exposures_are_the_gaps_up_to_the_expected_count():
    made = database()
    with transaction(made) as work:
        job = prepared(work)
        record_shot(work, job, a_shot(1))
        record_shot(work, job, a_shot(3))
        assert missing_exposures(work, job, WeldId.parse("GW-104"), 4) == (2, 4)
        assert missing_exposures(work, job, WeldId.parse("GW-104"), 3) == (2,)


def test_an_expected_count_of_nothing_is_refused():
    made = database()
    with pytest.raises(ValidationError), transaction(made) as work:
        job = prepared(work)
        missing_exposures(work, job, WeldId.parse("GW-104"), 0)
