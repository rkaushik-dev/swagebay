from datetime import date, timedelta

import pytest

from qu_imager.errors import ConflictError, ValidationError
from qu_imager.reporting.archive import Archive, Filed, Location, Retention

FILED = date(2024, 12, 1)


def location(site="Come By Chance", cabinet="A", box="14"):
    return Location(site=site, cabinet=cabinet, box=box)


def filed(number="24-0001-R0001", job="24-0001", retention=Retention.FIVE_YEARS, where=True):
    return Filed(
        radiograph=number,
        job=job,
        filed_on=FILED,
        retention=retention,
        location=location() if where else None,
    )


def test_a_five_year_retention_has_a_date():
    entry = filed()
    assert entry.retention.has_an_end_date
    assert entry.destroy_after == FILED + timedelta(days=1826)


def test_a_life_of_the_line_retention_has_no_date_rather_than_a_made_up_one():
    entry = filed(retention=Retention.INDEFINITE)
    assert not entry.retention.has_an_end_date
    assert entry.destroy_after is None
    assert entry.retention.days is None


def test_a_vessel_life_retention_has_no_date_either():
    assert filed(retention=Retention.VESSEL_LIFE).destroy_after is None


def test_nothing_indefinite_ever_comes_due():
    assert not filed(retention=Retention.INDEFINITE).is_due_on(date(2099, 1, 1))


def test_a_five_year_film_comes_due_on_the_day_and_not_before():
    entry = filed()
    assert not entry.is_due_on(FILED + timedelta(days=1825))
    assert entry.is_due_on(FILED + timedelta(days=1826))


def test_a_film_with_no_location_is_a_fault_rather_than_stored():
    assert filed(where=False).faults() == ("24-0001-R0001 is marked retained with no location",)
    assert filed().faults() == ()


def test_a_location_needs_every_part_filled_in():
    for missing in ("site", "cabinet", "box"):
        with pytest.raises(ValidationError) as caught:
            Location(
                site="" if missing == "site" else "s",
                cabinet="" if missing == "cabinet" else "A",
                box="" if missing == "box" else "14",
            )
        assert caught.value.context["field"] == missing


def test_a_filed_radiograph_needs_a_number():
    with pytest.raises(ValidationError):
        filed(number="  ")


def test_nothing_is_filed_twice():
    archive = Archive()
    archive.file(filed())
    with pytest.raises(ConflictError):
        archive.file(filed())


def test_a_film_is_found_by_its_number():
    archive = Archive()
    archive.file(filed())
    assert archive.find("24-0001-R0001").job == "24-0001"
    assert archive.find("24-0001-R9999") is None


def test_the_films_on_one_job_come_back_in_order():
    archive = Archive()
    archive.file(filed("24-0001-R0002"))
    archive.file(filed("24-0001-R0001"))
    archive.file(filed("24-0002-R0001", job="24-0002"))
    assert [f.radiograph for f in archive.for_job("24-0001")] == [
        "24-0001-R0001",
        "24-0001-R0002",
    ]


def test_the_destruction_list_only_names_what_has_a_date():
    archive = Archive()
    archive.file(filed("24-0001-R0001"))
    archive.file(filed("24-0001-R0002", retention=Retention.INDEFINITE))
    day = FILED + timedelta(days=2000)
    assert archive.due_for_destruction(day) == ("24-0001-R0001",)


def test_the_indefinite_films_are_listed_on_their_own():
    archive = Archive()
    archive.file(filed("24-0001-R0001"))
    archive.file(filed("24-0001-R0002", retention=Retention.VESSEL_LIFE))
    archive.file(filed("24-0001-R0003", retention=Retention.INDEFINITE))
    assert archive.kept_indefinitely() == ("24-0001-R0002", "24-0001-R0003")


def test_the_archive_reports_every_film_with_no_location():
    archive = Archive()
    archive.file(filed("24-0001-R0001", where=False))
    archive.file(filed("24-0001-R0002"))
    assert len(archive.faults()) == 1


def test_a_film_a_job_says_exists_and_the_cabinet_does_not_is_reported():
    archive = Archive()
    archive.file(filed("24-0001-R0001"))
    assert archive.missing_from(("24-0001-R0001", "24-0001-R0002")) == ("24-0001-R0002",)
    assert archive.missing_from(("24-0001-R0001",)) == ()


def test_a_location_prints_the_way_somebody_would_walk_to_it():
    assert location().describe() == "Come By Chance cabinet A box 14"
    assert str(Retention.INDEFINITE) == "life of the line"
