"""Properties of the registers, where the interesting bugs are set operations.

The manifest verify is the one worth generating for. It partitions two sets of
file names into three answers and the whole point of it is that the three do
not overlap and nothing falls between them. That is a property, not an example,
and the examples all pass whether or not it holds.

The utilisation gap check is the same shape: a set of days a device was out
minus a set of days somebody wrote down. It reads as obviously right and it
would have an off by one at each end of every trip, which is exactly the kind
of thing a generator finds and a hand written case does not.
"""

from datetime import date, datetime, timedelta

from hypothesis import given, settings
from hypothesis import strategies as st

from qu_imager.imaging.store import (
    Format,
    Manifest,
    Purpose,
    StoredImage,
    digest_of,
    expected_name,
)
from qu_imager.nonconformance import Disposition, Register, Report
from qu_imager.safety.inventory import Trip
from qu_imager.safety.utilisation import Use, UtilisationLog

CAPTURED = date(2025, 7, 16)
names = st.integers(min_value=1, max_value=40).map(lambda n: f"24-0001-R{n:04d}")
digests = st.integers(min_value=0, max_value=200).map(lambda n: digest_of(str(n).encode()))
days = st.integers(min_value=0, max_value=30).map(lambda n: CAPTURED + timedelta(days=n))


def an_image(number, digest):
    return StoredImage(
        radiograph_number=number,
        filename=expected_name(number, Format.DICONDE),
        image_format=Format.DICONDE,
        bit_depth=16,
        width=2048,
        height=512,
        byte_size=512,
        digest=digest,
        captured_at=datetime(2025, 7, 16, 8, 0),
        purpose=Purpose.RECORD,
    )


def a_manifest(held):
    manifest = Manifest(job="24-0001")
    for number, digest in held.items():
        manifest.add(an_image(number, digest))
    return manifest


def a_use(day):
    return Use(
        used_on=day,
        device_serial="D-77",
        source_serial="IR-5512",
        site="site",
        radiographers=("0142",),
    )


def a_report(reference="NCR-1", raised_on=CAPTURED):
    return Report(
        reference=reference,
        job="24-0001",
        item="GW-104",
        requirement="API 1104",
        account="slag",
        raised_on=raised_on,
        raised_by="0142",
    )


@given(
    held=st.dictionaries(names, digests, max_size=8),
    seen=st.dictionaries(names.map(lambda n: f"{n}.dcm"), digests, max_size=8),
)
@settings(max_examples=200, deadline=None)
def test_the_three_answers_never_overlap(held, seen):
    found = a_manifest(held).verify(seen)
    assert not set(found.missing) & set(found.unexpected)
    assert not set(found.missing) & set(found.altered)
    assert not set(found.unexpected) & set(found.altered)


@given(
    held=st.dictionaries(names, digests, max_size=8),
    seen=st.dictionaries(names.map(lambda n: f"{n}.dcm"), digests, max_size=8),
)
@settings(max_examples=200, deadline=None)
def test_every_name_on_either_side_is_accounted_for(held, seen):
    manifest = a_manifest(held)
    found = manifest.verify(seen)
    every = set(manifest.entries) | set(seen)
    matched = {
        name
        for name in set(manifest.entries) & set(seen)
        if seen[name] == manifest.entries[name].digest
    }
    assert every == matched | set(found.missing) | set(found.unexpected) | set(found.altered)


@given(held=st.dictionaries(names, digests, min_size=1, max_size=8))
@settings(max_examples=100, deadline=None)
def test_a_store_that_matches_itself_is_always_clean(held):
    manifest = a_manifest(held)
    seen = {name: image.digest for name, image in manifest.entries.items()}
    assert manifest.verify(seen).is_clean


@given(held=st.dictionaries(names, digests, max_size=8))
@settings(max_examples=100, deadline=None)
def test_the_byte_total_is_the_sum_of_what_is_in_it(held):
    assert a_manifest(held).total_bytes() == 512 * len(a_manifest(held))


@given(went=days, span=st.integers(min_value=0, max_value=10), logged=st.sets(days, max_size=12))
@settings(max_examples=200, deadline=None)
def test_a_gap_is_a_day_on_the_trip_that_is_not_in_the_log(went, span, logged):
    trip = Trip("IR-5512", "D-77", "0142", "site", went, went + timedelta(days=span))
    log = UtilisationLog()
    for day in logged:
        log.record(a_use(day))
    found = {day for _, day in log.unlogged_days([trip])}
    covered = {went + timedelta(days=n) for n in range(span + 1)}
    assert found == covered - logged


@given(went=days, span=st.integers(min_value=0, max_value=10))
@settings(max_examples=100, deadline=None)
def test_a_fully_logged_trip_never_has_a_gap(went, span):
    log = UtilisationLog()
    for n in range(span + 1):
        log.record(a_use(went + timedelta(days=n)))
    trip = Trip("IR-5512", "D-77", "0142", "site", went, went + timedelta(days=span))
    assert log.unlogged_days([trip]) == ()


@given(
    raised=days,
    closed_after=st.integers(min_value=0, max_value=90),
    disposition=st.sampled_from([Disposition.REWORK, Disposition.USE_AS_IS, Disposition.REJECT]),
)
@settings(max_examples=200, deadline=None)
def test_a_closed_report_never_reads_as_overdue_afterwards(raised, closed_after, disposition):
    report = a_report(raised_on=raised)
    report.decide(disposition, concession="CON-1", removal="SCRAP-1")
    closed = raised + timedelta(days=closed_after)
    report.close(closed, "0303")
    for extra in (0, 1, 400):
        assert not report.is_overdue(closed + timedelta(days=extra))
        assert report.days_open(closed + timedelta(days=extra)) == closed_after


@given(references=st.sets(st.integers(min_value=1, max_value=12), min_size=1, max_size=12))
@settings(max_examples=100, deadline=None)
def test_the_open_list_and_the_closed_ones_add_up_to_the_register(references):
    register = Register()
    closed = 0
    for index in sorted(references):
        reference = f"NCR-{index}"
        register.raise_report(a_report(reference))
        if index % 2 == 0:
            report = register.get(reference)
            report.decide(Disposition.REWORK)
            report.close(CAPTURED + timedelta(days=1), "0303")
            closed += 1
    assert len(register.open_reports()) + closed == len(register)


@given(raised=days, asked=days)
@settings(max_examples=100, deadline=None)
def test_days_open_is_never_negative(raised, asked):
    assert a_report(raised_on=raised).days_open(asked) >= 0


maintenance_days = st.integers(min_value=-10, max_value=120).map(
    lambda n: CAPTURED + timedelta(days=n)
)


@given(stripped=days, asked=maintenance_days)
@settings(max_examples=300, deadline=None)
def test_a_device_goes_out_exactly_while_the_quarterly_still_runs(stripped, asked):
    from qu_imager.safety.maintenance import (
        QUARTERLY_DAYS,
        MaintenanceRegister,
        pre_use,
        quarterly,
    )

    register = MaintenanceRegister()
    register.record(quarterly("D-77", stripped, "0303"))
    register.record(pre_use("D-77", asked, "0142"))
    inside = stripped <= asked < stripped + timedelta(days=QUARTERLY_DAYS)
    assert register.may_go_out("D-77", asked) == inside


@given(stripped=days, asked=maintenance_days)
@settings(max_examples=200, deadline=None)
def test_a_device_never_goes_out_without_a_check_that_morning(stripped, asked):
    from qu_imager.safety.maintenance import MaintenanceRegister, quarterly

    register = MaintenanceRegister()
    register.record(quarterly("D-77", stripped, "0303"))
    assert not register.may_go_out("D-77", asked)


@given(stripped=days, asked=maintenance_days)
@settings(max_examples=200, deadline=None)
def test_a_broken_safety_item_that_morning_always_stops_it(stripped, asked):
    from qu_imager.safety.maintenance import (
        Defect,
        Item,
        MaintenanceRegister,
        pre_use,
        quarterly,
    )

    register = MaintenanceRegister()
    register.record(quarterly("D-77", stripped, "0303"))
    register.record(pre_use("D-77", asked, "0142", (Defect(Item.GUIDE_TUBE, "split"),)))
    assert not register.may_go_out("D-77", asked)
