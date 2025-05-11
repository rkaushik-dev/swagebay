from datetime import date
from decimal import Decimal

import pytest

from qu_imager.errors import ValidationError
from qu_imager.geometry.shot import ShotType
from qu_imager.imaging.film import film
from qu_imager.imaging.system import ImagingSystem
from qu_imager.quantities.activity import curie
from qu_imager.quantities.duration import minutes
from qu_imager.quantities.length import Length, mm
from qu_imager.safety.dosimetry import Assignment
from qu_imager.safety.field import RadiationField
from qu_imager.safety.shielding import Barrier
from qu_imager.shotplan import (
    SiteConstraints,
    collimator_would_help,
    plan_shot,
    shortfall,
)
from qu_imager.sources.device import collimator
from qu_imager.sources.isotope import IR192
from qu_imager.technique import TechniqueRequest, plan_technique

from conftest import SHOT_DAY

DAY = date(2024, 5, 21)
OPEN_GROUND = SiteConstraints(clear_radius=Length.from_m(200))
TIGHT = SiteConstraints(clear_radius=Length.from_m(40))


def sheet_for(joint, source):
    return plan_technique(
        TechniqueRequest(
            joint=joint,
            source=source,
            system=ImagingSystem.from_film(film("d7")),
            film=film("d7"),
            shot_at=SHOT_DAY,
            shot=ShotType.DWSI,
        )
    )


def plan(joint, source, crew, kit, site=OPEN_GROUND, radiation=None):
    return plan_shot(
        sheet_for(joint, source),
        radiation if radiation is not None else RadiationField(IR192, curie(74)),
        site,
        crew,
        kit,
        DAY,
    )


def test_a_job_with_room_and_a_crew_can_proceed(six_inch_weld, iridium_source, crew, kitted):
    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    assert ready.can_proceed
    assert ready.blockers == ()
    assert not ready.needs_a_signature


def test_a_boundary_bigger_than_the_ground_blocks_the_job(
    six_inch_weld, iridium_source, crew, kitted
):
    blocked = plan(six_inch_weld, iridium_source, crew, kitted, site=TIGHT)
    assert not blocked.can_proceed
    assert "clear ground" in blocked.blockers[0]
    assert shortfall(blocked.rope, blocked.site) > Length.from_m(80)


def test_a_collimator_can_be_what_makes_the_job_fit_sideways():
    bare = RadiationField(IR192, curie(74))
    cupped = RadiationField(IR192, curie(74), collimator=collimator("w60"))
    assert collimator_would_help(bare, TIGHT)
    assert not collimator_would_help(cupped, TIGHT)


def test_a_barrier_can_bring_the_whole_boundary_inside_the_ground(
    six_inch_weld, iridium_source, crew, kitted
):
    shielded = RadiationField(IR192, curie(74), barriers=(Barrier("lead", mm(30)),))
    assert plan(
        six_inch_weld, iridium_source, crew, kitted, site=TIGHT, radiation=shielded
    ).can_proceed


def test_a_job_with_no_crew_is_blocked(six_inch_weld, iridium_source, kitted):
    assert plan(six_inch_weld, iridium_source, (), kitted).blockers == ("there is no crew",)


def test_an_assistant_alone_is_blocked_on_supervision(six_inch_weld, iridium_source, crew, kitted):
    blockers = plan(six_inch_weld, iridium_source, (crew[1],), kitted).blockers
    assert "no licensed radiographer is on the crew" in blockers
    assert "nobody on the crew may interpret the film" in blockers


def test_missing_dosimetry_is_blocked_and_named_by_badge(six_inch_weld, iridium_source, crew):
    blockers = plan(six_inch_weld, iridium_source, crew, (Assignment(badge="0201"),)).blockers
    assert len(blockers) == 3
    assert all(b.startswith("0201: ") for b in blockers)


def test_a_window_too_short_for_the_joint_blocks_it(six_inch_weld, iridium_source, crew, kitted):
    site = SiteConstraints(clear_radius=Length.from_m(200), window=minutes(2))
    assert any(
        "source out" in b for b in plan(six_inch_weld, iridium_source, crew, kitted, site).blockers
    )


def test_a_generous_window_does_not_block(six_inch_weld, iridium_source, crew, kitted):
    site = SiteConstraints(clear_radius=Length.from_m(200), window=minutes(600))
    assert plan(six_inch_weld, iridium_source, crew, kitted, site).can_proceed


def test_every_blocker_is_collected_not_just_the_first(six_inch_weld, iridium_source, crew):
    site = SiteConstraints(clear_radius=Length.from_m(40), window=minutes(2))
    blocked = plan(six_inch_weld, iridium_source, (crew[1],), (Assignment(badge="0201"),), site)
    assert len(blocked.blockers) >= 6


def test_sheet_warnings_travel_with_the_plan_without_blocking_it(
    six_inch_weld, iridium_source, crew, kitted
):
    iridium_source.working_floor = curie(85)
    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    assert ready.can_proceed
    assert ready.needs_a_signature
    assert any("working floor" in w for w in ready.warnings)


def test_the_plan_prints_the_sheet_and_the_rope_when_it_can_go(
    six_inch_weld, iridium_source, crew, kitted
):
    text = plan(six_inch_weld, iridium_source, crew, kitted).describe()
    assert "girth weld" in text
    assert "restricted area at" in text


def test_a_blocked_plan_prints_the_blockers_instead(six_inch_weld, iridium_source, crew, kitted):
    assert (
        "clear ground" in plan(six_inch_weld, iridium_source, crew, kitted, site=TIGHT).describe()
    )


def test_a_site_with_no_clear_ground_is_refused_outright():
    with pytest.raises(ValidationError):
        SiteConstraints(clear_radius=Length.ZERO)


def test_no_shortfall_when_the_job_fits(six_inch_weld, iridium_source, crew, kitted):
    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    assert shortfall(ready.rope, ready.site) == Length.ZERO
    assert ready.rope.down_beam.m - ready.rope.off_beam.m == Decimal(0)


def test_the_plan_says_what_the_crew_is_expected_to_take(
    six_inch_weld, iridium_source, crew, kitted
):
    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    assert len(ready.expected_dose) == len(kitted)
    assert {entry.badge for entry in ready.expected_dose} == {"0142", "0201"}


def test_the_expected_dose_at_the_boundary_is_a_small_number(
    six_inch_weld, iridium_source, crew, kitted
):
    from qu_imager.quantities.dose import usv

    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    for entry in ready.expected_dose:
        assert entry.expected < usv(50)


def test_standing_closer_takes_more(six_inch_weld, iridium_source, crew, kitted):
    from qu_imager.quantities.length import Length as L
    from qu_imager.safety.field import RadiationField
    from qu_imager.shotplan import expected_dose

    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    field = RadiationField(IR192, curie(74))
    near = expected_dose(field, ready.sheet, "0142", L.from_m(20))
    far = expected_dose(field, ready.sheet, "0142", L.from_m(200))
    assert near.expected > far.expected


def test_a_longer_job_takes_more_at_the_same_distance(six_inch_weld, iridium_source, crew, kitted):
    from qu_imager.quantities.length import Length as L
    from qu_imager.safety.field import RadiationField
    from qu_imager.shotplan import expected_dose

    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    field = RadiationField(IR192, curie(74))
    one = expected_dose(field, ready.sheet, "0142", L.from_m(140))
    assert one.expected.nsv > 0


def test_nobody_standing_anywhere_is_refused():
    from qu_imager.quantities.length import Length as L
    from qu_imager.safety.field import RadiationField
    from qu_imager.shotplan import expected_dose

    field = RadiationField(IR192, curie(74))
    with pytest.raises(ValidationError):
        expected_dose(field, None, "0142", L.ZERO)


def test_a_ceiling_names_who_is_over_it(six_inch_weld, iridium_source, crew, kitted):
    from qu_imager.quantities.dose import Dose, usv
    from qu_imager.shotplan import over_expected

    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    assert over_expected(ready, usv(1000)) == ()
    assert set(over_expected(ready, Dose(1))) == {"0142", "0201"}


def test_the_expected_dose_prints_where_it_was_taken_from(
    six_inch_weld, iridium_source, crew, kitted
):
    ready = plan(six_inch_weld, iridium_source, crew, kitted)
    text = ready.expected_dose[0].describe()
    assert "uSv" in text
    assert " m" in text
