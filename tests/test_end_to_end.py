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

from conftest import a_reader, database, film_evidence

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
        assert package.repair_rate() == Decimal(1) / Decimal(2)
        assert len(package.radiographs) == exposures * 2

    with transaction(made) as work:
        close_job(work, job, date(2025, 2, 20))
        assert work.jobs.get(job).closed_on == date(2025, 2, 20)

    with transaction(made) as work:
        assert assemble(work, job).closed_on == date(2025, 2, 20)
        assert ASSAY.replace(tzinfo=UTC).date() == date(2025, 2, 1)
