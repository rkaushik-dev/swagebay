"""One job walked from end to end, the way the office actually does it.

Every other test file checks one thing in isolation, which is right and which
leaves one gap: whether the pieces still fit together. This walks a whole job
through the same services the api and the cli call, in the order a coordinator
would call them, and checks the state after each step.

It is deliberately one long test rather than fifteen short ones. The value is
in the sequence, and a sequence split into fifteen tests with a fixture in
between is fifteen tests that share a fixture and prove nothing about order.
"""

from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

from qu_imager.evaluation.acceptance import Code
from qu_imager.evaluation.assessment import Disposition
from qu_imager.evaluation.indication import Indication
from qu_imager.evaluation.taxonomy import Kind
from qu_imager.geometry import pipe_schedule as ps
from qu_imager.geometry.shot import ShotType
from qu_imager.identity import WeldId
from qu_imager.materials import STEEL
from qu_imager.persistence.unit_of_work import transaction
from qu_imager.quantities.activity import curie
from qu_imager.quantities.length import mm
from qu_imager.reporting.package import assemble
from qu_imager.reporting.traceability import broken_chains
from qu_imager.services.jobs import NewJob, NewWeld, add_weld, close_job, open_job, outstanding
from qu_imager.services.planning import PlanRequest, plan
from qu_imager.services.reading import ReadingRecord, record_reading, weld_verdict
from qu_imager.services.shooting import ShotRecord, missing_exposures, record_shot
from qu_imager.services.sources import (
    Booking,
    bring_back,
    register_device,
    register_source,
    send_out,
)
from qu_imager.services.wipes import record_wipe
from qu_imager.sources.isotope import IR192
from qu_imager.sources.sealed import SealedSource, SourceCertificate

from conftest import a_reader, database, film_evidence, pass_inspections

ASSAY = datetime(2025, 2, 1)
SHOT = datetime(2025, 2, 12, 23, 30)
READ = datetime(2025, 2, 13, 8, 0)
OPENED = date(2025, 2, 3)
WELD = WeldId.parse("GW-104")
REPAIR = WeldId.parse("GW-104R1")


def a_source():
    return SealedSource(
        certificate=SourceCertificate(
            serial="IR-5512",
            isotope=IR192,
            assay_activity=curie(90),
            assay_at=ASSAY,
            manufacturer="QSA",
            model="A424-9",
            active_diameter=mm(3),
            active_length=mm(3),
        ),
        working_floor=curie(15),
    )


def a_weld(weld=WELD):
    return NewWeld(
        weld=weld,
        joint=ps.girth_weld("6", "80", STEEL, reinforcement=mm(2)),
        weld_length=mm(529),
        welder="w-19",
        welded_on=OPENED,
    )


def test_a_job_walks_from_open_to_closed():
    made = database()

    with transaction(made) as work:
        register_source(work, a_source())
        register_device(work, "880D-221", "880d")
        record_wipe(work, "IR-5512", ASSAY, Decimal(0), "rp")
        job = open_job(
            work,
            NewJob(
                client="Maritime Pipelines",
                site="Come By Chance",
                code=Code.API_1104,
                procedure_number="RT-001",
                opened_on=OPENED,
            ),
        )
        add_weld(work, job, a_weld())

    with transaction(made) as work:
        pass_inspections(work, "880D-221", date(2025, 2, 12))
        send_out(work, Booking("IR-5512", "880D-221", "0142", "Come By Chance", date(2025, 2, 12)))
        sheet = plan(
            work,
            job,
            PlanRequest(
                weld=WELD,
                source_serial="IR-5512",
                film_key="d7",
                shot_at=SHOT,
                shot=ShotType.DWSI,
            ),
        )
        assert sheet.is_clean
        exposures = sheet.exposures

    with transaction(made) as work:
        assert missing_exposures(work, job, WELD, exposures) == tuple(range(1, exposures + 1))
        numbers = [
            record_shot(
                work,
                job,
                ShotRecord(
                    weld=WELD,
                    exposure_index=index,
                    exposed_at=SHOT,
                    technique_reference="RT-001 rev 1",
                    evidence=film_evidence(at=SHOT),
                ),
            ).number
            for index in range(1, exposures + 1)
        ]
        assert missing_exposures(work, job, WELD, exposures) == ()

    with transaction(made) as work:
        assert weld_verdict(work, job, WELD) is None
        for index, number in enumerate(numbers):
            found = (Indication(Kind.ESI, mm(10), mm(60), mm(1)),) if index == 0 else ()
            record_reading(work, ReadingRecord(number, a_reader(), READ, found))
        verdict = weld_verdict(work, job, WELD)
        assert verdict is not None
        assert verdict.disposition is Disposition.REPAIR
        assert verdict.clauses() == ("9.3.8",)

    with transaction(made) as work:
        bring_back(work, "IR-5512", date(2025, 2, 13), exposures=exposures)
        add_weld(work, job, a_weld(REPAIR))

    with transaction(made) as work:
        for index in range(1, exposures + 1):
            number = record_shot(
                work,
                job,
                ShotRecord(
                    weld=REPAIR,
                    exposure_index=index,
                    exposed_at=SHOT + timedelta(days=3),
                    technique_reference="RT-001 rev 1",
                    evidence=film_evidence(at=SHOT + timedelta(days=3)),
                ),
            ).number
            record_reading(work, ReadingRecord(number, a_reader(), READ + timedelta(days=3)))
        assert weld_verdict(work, job, REPAIR).accepted

    with transaction(made) as work:
        assert outstanding(work, job) == ()
        assert broken_chains(work, job.year, job.serial) == ()
        package = assemble(work, job)
        assert package.is_complete
        assert package.counts_agree()
        assert package.welds_shot == 2
        assert package.welds_repaired == 1
        assert package.repair_rate().repaired == 1
        assert package.repair_rate().shot == 2
        assert not package.repair_rate().is_reportable
        assert len(package.radiographs) == exposures * 2

    with transaction(made) as work:
        close_job(work, job, date(2025, 2, 20))
        assert work.jobs.get(job).closed_on == date(2025, 2, 20)

    with transaction(made) as work:
        assert assemble(work, job).closed_on == date(2025, 2, 20)
        assert ASSAY.replace(tzinfo=UTC).date() == date(2025, 2, 1)


def prepared(made):
    """A job with a fit source and one weld on it, which is where the night starts."""
    with transaction(made) as work:
        register_source(work, a_source())
        register_device(work, "880D-221", "880d")
        record_wipe(work, "IR-5512", ASSAY, Decimal(0), "rp")
        job = open_job(
            work,
            NewJob(
                client="c",
                site="s",
                code=Code.API_1104,
                procedure_number="RT-001",
                opened_on=OPENED,
            ),
        )
        add_weld(work, job, a_weld())
    return job


def test_an_inadmissible_film_blocks_the_job_until_it_is_reshot():
    made = database()
    job = prepared(made)

    with transaction(made) as work:
        bad = record_shot(
            work,
            job,
            ShotRecord(
                weld=WELD,
                exposure_index=1,
                exposed_at=SHOT,
                technique_reference="RT-001 rev 1",
                evidence=film_evidence(visible=False, at=SHOT),
            ),
        )
        assert bad.needs_reshooting

    with transaction(made) as work:
        pending = outstanding(work, job)
        assert f"{bad.number} is not admissible" in pending
        assert weld_verdict(work, job, WELD) is None
        assert not assemble(work, job).is_complete

    with transaction(made) as work:
        chains = broken_chains(work, job.year, job.serial)
        assert len(chains) == 1
        assert any("admissible" in link.name for link in chains[0].broken())

    with transaction(made) as work:
        good = record_shot(
            work,
            job,
            ShotRecord(
                weld=WELD,
                exposure_index=2,
                exposed_at=SHOT + timedelta(hours=1),
                technique_reference="RT-001 rev 1",
                evidence=film_evidence(at=SHOT),
            ),
        )
        record_reading(work, ReadingRecord(good.number, a_reader(), READ))

    with transaction(made) as work:
        assert f"{bad.number} is not admissible" in outstanding(work, job)
        assert weld_verdict(work, job, WELD) is None


def test_a_job_will_not_close_while_anything_is_outstanding():
    import pytest

    from qu_imager.errors import ConflictError

    made = database()
    job = prepared(made)

    with transaction(made) as work, pytest.raises(ConflictError) as caught:
        close_job(work, job, date(2025, 2, 20))
    assert caught.value.context["outstanding"] >= 1

    with transaction(made) as work:
        number = record_shot(
            work,
            job,
            ShotRecord(
                weld=WELD,
                exposure_index=1,
                exposed_at=SHOT,
                technique_reference="RT-001 rev 1",
                evidence=film_evidence(at=SHOT),
            ),
        ).number

    with transaction(made) as work, pytest.raises(ConflictError):
        close_job(work, job, date(2025, 2, 20))

    with transaction(made) as work:
        record_reading(work, ReadingRecord(number, a_reader(), READ))
        assert outstanding(work, job) == ()
        close_job(work, job, date(2025, 2, 20))


def test_a_source_that_went_out_and_came_back_reads_as_the_shop_again():
    made = database()
    job = prepared(made)

    with transaction(made) as work:
        pass_inspections(work, "880D-221", date(2025, 2, 12))
        send_out(work, Booking("IR-5512", "880D-221", "0142", "site", date(2025, 2, 12)))
        assert work.sources.open_trip("IR-5512") is not None

    with transaction(made) as work:
        bring_back(work, "IR-5512", date(2025, 2, 13), exposures=8)
        assert work.sources.open_trip("IR-5512") is None
        trip = work.sources.get("IR-5512").trips[0]
        assert trip.exposures == 8
        assert trip.came_back == date(2025, 2, 13)
        assert job.serial == 1
