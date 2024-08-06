from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from qu_imager.persistence.base import Base, table_names
from qu_imager.persistence.models import FilmRow, IsotopeRow, MaterialRow
from qu_imager.quantities.length import mm

WHEN = date(2024, 1, 1)


def session():
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    return Session(engine)


def isotope_row(key="ir192", revision=0, low=206, high=612):
    return IsotopeRow(
        key=key,
        revision=revision,
        symbol="Ir-192",
        half_life_days=Decimal("73.83"),
        gamma_constant=Decimal("0.48"),
        energy_kev_low=low,
        energy_kev_high=high,
        steel_min=mm(12),
        steel_max=mm(63),
        effective_on=WHEN,
    )


def material_row(density=7850):
    return MaterialRow(
        key="carbon_steel",
        name="carbon steel",
        density_kg_m3=density,
        ferrous=True,
        effective_on=WHEN,
    )


def film_row(speed=Decimal(100)):
    return FilmRow(
        key="d7",
        name="Structurix D7",
        manufacturer="Agfa",
        film_class="II",
        speed_index=speed,
        granularity=Decimal("0.024"),
        effective_on=WHEN,
    )


def test_the_models_package_puts_every_table_on_the_metadata():
    names = table_names()
    assert "material" in names
    assert "isotope" in names
    assert "film" in names


def test_an_isotope_round_trips_with_its_lengths_intact():
    with session() as db:
        db.add(isotope_row())
        db.commit()
        found = db.scalars(select(IsotopeRow)).one()
        assert found.steel_min == mm(12)
        assert found.half_life_days == Decimal("73.83")


def test_a_revision_is_a_new_row_and_not_an_update():
    with session() as db:
        db.add(isotope_row(revision=0))
        db.add(isotope_row(revision=1))
        db.commit()
        assert len(db.scalars(select(IsotopeRow)).all()) == 2


def test_the_same_key_and_revision_twice_is_refused():
    with session() as db:
        db.add(isotope_row(revision=0))
        db.add(isotope_row(revision=0))
        with pytest.raises(IntegrityError):
            db.commit()


def test_an_energy_band_the_wrong_way_round_is_refused():
    with session() as db:
        db.add(isotope_row(low=600, high=200))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_half_life_of_nothing_is_refused_by_the_schema():
    with session() as db:
        row = isotope_row()
        row.half_life_days = Decimal(0)
        db.add(row)
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_material_needs_a_positive_density():
    with session() as db:
        db.add(material_row(density=0))
        with pytest.raises(IntegrityError):
            db.commit()


def test_a_material_round_trips():
    with session() as db:
        db.add(material_row())
        db.commit()
        found = db.scalars(select(MaterialRow)).one()
        assert found.density_kg_m3 == 7850
        assert found.ferrous is True
        assert found.revision == 0


def test_a_film_keeps_its_speed_as_a_decimal():
    with session() as db:
        db.add(film_row())
        db.commit()
        found = db.scalars(select(FilmRow)).one()
        assert found.speed_index == Decimal(100)
        assert found.granularity == Decimal("0.024")


def test_a_film_with_no_speed_is_refused():
    with session() as db:
        db.add(film_row(speed=Decimal(0)))
        with pytest.raises(IntegrityError):
            db.commit()


def test_every_constraint_carries_a_name_from_the_convention():
    for table in Base.metadata.tables.values():
        for constraint in table.constraints:
            assert constraint.name is not None
            assert str(constraint.name) != ""
