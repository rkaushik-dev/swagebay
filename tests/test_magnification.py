from decimal import Decimal

import pytest

from qu_imager.errors import TechniqueError, ValidationError
from qu_imager.geometry.magnification import (
    MICROFOCUS_CEILING,
    PRACTICAL_CEILING,
    USEFUL_MAGNIFICATION,
    Setup,
    distances_for,
    is_microfocus,
    optimum_magnification,
    theoretical_optimum,
)
from qu_imager.quantities.length import Length, mm


def setup(sod=300, ofd=300, focal="0.1", detector="0.2"):
    return Setup(
        source_to_object=mm(sod),
        object_to_film=mm(ofd),
        focal_size=mm(focal),
        detector_unsharpness=mm(detector),
    )


def test_the_magnification_is_the_ratio_of_the_two_distances():
    assert setup(sod=300, ofd=300).magnification == Decimal(2)
    assert setup(sod=300, ofd=600).magnification == Decimal(3)


def test_a_film_hard_against_the_part_is_not_magnified():
    assert setup(sod=600, ofd=0).magnification == Decimal(1)
    assert not setup(sod=600, ofd=0).is_worth_doing


def test_a_small_air_gap_is_not_worth_calling_magnification():
    assert not setup(sod=600, ofd=10).is_worth_doing
    assert setup(sod=600, ofd=200).is_worth_doing
    assert USEFUL_MAGNIFICATION == Decimal("1.2")


def test_the_detector_blur_shrinks_when_it_is_referred_back_to_the_part():
    magnified = setup(sod=300, ofd=300)
    assert magnified.effective_detector_unsharpness == mm("0.1")
    assert magnified.effective_detector_unsharpness < magnified.detector_unsharpness


def test_magnifying_a_coarse_detector_beats_shooting_it_in_contact():
    contact = setup(sod=600, ofd=1)
    magnified = setup(sod=300, ofd=300)
    assert magnified.total_unsharpness < contact.total_unsharpness


def test_magnifying_too_far_makes_it_worse_again():
    best = setup(sod=300, ofd=1200)
    too_far = setup(sod=100, ofd=2900)
    assert too_far.magnification > best.magnification
    assert too_far.total_unsharpness > best.total_unsharpness


def test_the_optimum_is_where_the_two_blurs_balance():
    assert theoretical_optimum(mm("0.1"), mm("0.2")) == Decimal(5)
    assert theoretical_optimum(mm("0.1"), mm("0.1")) == Decimal(2)


def test_a_finer_spot_earns_a_higher_optimum():
    fine = theoretical_optimum(mm("0.02"), mm("0.2"))
    coarse = theoretical_optimum(mm("0.1"), mm("0.2"))
    assert fine > coarse


def test_the_practical_optimum_is_capped_where_the_exposure_stops_being_worth_it():
    assert theoretical_optimum(mm("0.02"), mm("0.2")) > PRACTICAL_CEILING
    assert optimum_magnification(mm("0.02"), mm("0.2")) == PRACTICAL_CEILING
    assert optimum_magnification(mm("0.1"), mm("0.2")) == Decimal(5)


def test_a_coarser_detector_earns_a_higher_optimum_too():
    coarse = theoretical_optimum(mm("0.1"), mm("0.4"))
    fine = theoretical_optimum(mm("0.1"), mm("0.1"))
    assert coarse > fine


def test_the_optimum_really_is_near_the_minimum_of_the_total():
    focal, detector = mm("0.1"), mm("0.2")
    best = theoretical_optimum(focal, detector)
    sod = mm(300)

    def total(magnification):
        ofd = Length(int(sod.um * (magnification - 1)))
        return Setup(sod, ofd, focal, detector).total_unsharpness

    assert total(best) <= total(best - Decimal(2))
    assert total(best) <= total(best + Decimal(2))


def test_an_iridium_pellet_is_not_a_microfocus_source():
    assert not is_microfocus(mm(3))
    assert is_microfocus(mm("0.1"))
    assert is_microfocus(MICROFOCUS_CEILING)


def test_asking_for_an_optimum_on_a_gamma_source_is_refused():
    with pytest.raises(TechniqueError) as caught:
        theoretical_optimum(mm(3), mm("0.2"))
    assert caught.value.context["ceiling_mm"] == "0.4"


def test_both_unsharpness_terms_have_to_be_real():
    with pytest.raises(ValidationError):
        theoretical_optimum(Length.ZERO, mm("0.2"))
    with pytest.raises(ValidationError):
        theoretical_optimum(mm("0.1"), Length.ZERO)


def test_a_fixed_distance_splits_to_reach_a_wanted_magnification():
    sod, ofd = distances_for(Decimal(2), mm(600))
    assert sod == mm(300)
    assert ofd == mm(300)
    assert Setup(sod, ofd, mm("0.1"), mm("0.2")).magnification == Decimal(2)


def test_a_magnification_of_one_puts_the_film_against_the_part():
    sod, ofd = distances_for(Decimal(1), mm(600))
    assert sod == mm(600)
    assert ofd == Length.ZERO


def test_a_magnification_under_one_or_no_distance_is_refused():
    with pytest.raises(ValidationError):
        distances_for(Decimal("0.5"), mm(600))
    with pytest.raises(ValidationError):
        distances_for(Decimal(2), Length.ZERO)


def test_a_setup_needs_a_source_a_spot_and_a_detector():
    with pytest.raises(ValidationError):
        setup(sod=0)
    with pytest.raises(ValidationError):
        setup(focal="0")
    with pytest.raises(ValidationError):
        setup(detector="0")
    with pytest.raises(ValidationError):
        Setup(mm(300), Length(-1), mm("0.1"), mm("0.2"))
