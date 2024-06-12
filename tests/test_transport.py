from decimal import Decimal

import pytest

from qu_imager.errors import ConfigurationError, TechniqueError, ValidationError
from qu_imager.quantities.activity import Activity, curie
from qu_imager.quantities.dose import DoseRate, usv_per_hour
from qu_imager.safety.transport import (
    VEHICLE_INDEX_CEILING,
    Label,
    Package,
    PackageType,
    Shipment,
    a1_limit,
    label_for,
    transport_index,
)
from qu_imager.sources.isotope import CO60, IR192, SE75


def package(
    reference="880D-221",
    isotope=IR192,
    activity=None,
    package_type=PackageType.TYPE_B,
    surface="1800",
    metre="18",
):
    return Package(
        reference=reference,
        isotope=isotope,
        activity=activity if activity is not None else curie(90),
        package_type=package_type,
        surface_rate=usv_per_hour(surface),
        rate_at_one_metre=usv_per_hour(metre),
    )


def test_the_transport_index_is_millirem_at_a_metre_rounded_up():
    assert transport_index(usv_per_hour(18)) == Decimal("1.8")
    assert transport_index(usv_per_hour(1)) == Decimal("0.1")
    assert transport_index(usv_per_hour("10.1")) == Decimal("1.1")
    assert transport_index(DoseRate.ZERO) == Decimal(0)


def test_the_index_never_rounds_down():
    assert transport_index(usv_per_hour("10.01")) == Decimal("1.1")
    assert transport_index(usv_per_hour("0.01")) == Decimal("0.1")


def test_a_working_projector_carries_a_yellow_three_label():
    assert package().label is Label.YELLOW_III


def test_a_quiet_package_carries_a_white_one():
    quiet = package(surface="4", metre="0")
    assert quiet.label is Label.WHITE_I


def test_the_surface_reading_can_be_what_pushes_the_label_up():
    assert label_for(usv_per_hour(400), usv_per_hour(5)) is Label.YELLOW_II
    assert label_for(usv_per_hour(600), usv_per_hour(5)) is Label.YELLOW_III


def test_the_metre_reading_can_be_what_pushes_it_up_instead():
    assert label_for(usv_per_hour(4), usv_per_hour(0)) is Label.WHITE_I
    assert label_for(usv_per_hour(4), usv_per_hour(5)) is Label.YELLOW_II
    assert label_for(usv_per_hour(4), usv_per_hour(50)) is Label.YELLOW_III


def test_a_package_over_every_limit_has_no_label_at_all():
    with pytest.raises(TechniqueError) as caught:
        label_for(usv_per_hour(3000), usv_per_hour(200))
    assert caught.value.context["transport_index"] == "20.0"


def test_the_a1_limits_are_the_published_ones():
    assert a1_limit(IR192) == Activity.from_tbq(1)
    assert a1_limit(CO60) == Activity.from_tbq("0.4")
    assert a1_limit(SE75) == Activity.from_tbq(3)


def test_an_isotope_with_no_published_limit_is_refused():
    from qu_imager.sources.isotope import Isotope

    made_up = Isotope(
        key="xx99",
        name="x",
        symbol="X-99",
        half_life_days=Decimal(10),
        gamma_constant=Decimal("0.5"),
        energy_kev_low=100,
        energy_kev_high=200,
        steel_min=IR192.steel_min,
        steel_max=IR192.steel_max,
        equivalence_quality=None,
    )
    with pytest.raises(ConfigurationError):
        a1_limit(made_up)


def test_a_working_projector_load_is_over_the_type_a_limit_and_needs_a_type_b():
    assert curie(90) > a1_limit(IR192)
    assert package().faults() == ()
    as_type_a = package(package_type=PackageType.TYPE_A)
    assert len(as_type_a.faults()) == 1
    assert "A1 limit" in as_type_a.faults()[0]


def test_a_small_source_travels_type_a_quite_happily():
    small = package(activity=curie(20), package_type=PackageType.TYPE_A, surface="40", metre="4")
    assert curie(20) < a1_limit(IR192)
    assert small.faults() == ()


def test_cobalt_has_the_tightest_limit_of_the_lot():
    heavy = package(activity=curie(20), isotope=CO60, package_type=PackageType.TYPE_A)
    assert "A1 limit" in heavy.faults()[0]


def test_a_radiography_source_never_travels_excepted():
    excepted = package(package_type=PackageType.EXCEPTED)
    assert "never travels as an excepted package" in excepted.faults()[0]


def test_a_package_that_cannot_be_labelled_says_so_in_its_faults():
    over = package(surface="3000", metre="200")
    assert any("over the limits for every label" in f for f in over.faults())


def test_a_shipment_totals_the_indices():
    load = Shipment((package(), package(reference="880S-114")))
    assert load.total_index == Decimal("3.6")
    assert not load.over_vehicle_ceiling()
    assert load.faults() == ()


def test_a_shipment_over_the_vehicle_ceiling_is_the_roadside_finding():
    hot = package(metre="300")
    load = Shipment(tuple(package(reference=f"p{i}", metre="300") for i in range(2)))
    assert hot.transport_index == Decimal(30)
    assert load.total_index == Decimal(60)
    assert load.over_vehicle_ceiling()
    assert any("vehicle ceiling" in f for f in load.faults())
    assert VEHICLE_INDEX_CEILING == Decimal(50)


def test_shipment_faults_name_the_package_they_came_from():
    load = Shipment((package(reference="880D-221", package_type=PackageType.EXCEPTED),))
    assert load.faults()[0].startswith("880D-221: ")


def test_a_package_needs_a_reference():
    with pytest.raises(ValidationError):
        package(reference="   ")
    assert str(PackageType.TYPE_B) == "Type B package"
    assert str(Label.YELLOW_III) == "Yellow-III"
