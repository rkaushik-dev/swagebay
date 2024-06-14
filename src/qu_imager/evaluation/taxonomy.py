"""The discontinuities a weld can contain, and the one distinction that matters.

Codes classify by shape before they classify by cause, and they do it for a
reason that is worth stating plainly. A planar discontinuity is a crack or an
unfused surface: it has essentially no thickness, it has a sharp edge, and the
sharp edge concentrates stress. A volumetric one is a bubble or a lump of slag:
it has a rounded boundary and it concentrates almost nothing.

That is why a crack a millimetre long is rejected and a pore three millimetres
across is accepted. It looks arbitrary on the film and it is not arbitrary at
all. It is also why the aggregate length rules apply to some kinds and not
others, and why the code has more to say about incomplete penetration than
about porosity.

The abbreviations are the ones written on the report because they are the ones
written in API 1104, and a client reading IPD knows what it means without a
legend. They are not renamed to be readable.
"""

from __future__ import annotations

from enum import Enum


class Kind(Enum):
    """A discontinuity, named the way the code names it."""

    IP = "inadequate penetration without high low"
    IPD = "inadequate penetration due to high low"
    ICP = "inadequate cross penetration"
    IF = "incomplete fusion"
    IFD = "incomplete fusion due to cold lap"
    IC = "internal concavity"
    BT = "burn through"
    ESI = "elongated slag inclusion"
    ISI = "isolated slag inclusion"
    P = "porosity"
    CP = "cluster porosity"
    HB = "hollow bead"
    C = "crack"
    U = "undercut"
    AB = "arc burn"

    @property
    def is_planar(self) -> bool:
        """Sharp edged, stress concentrating, and judged far harder."""
        return self in {
            Kind.IP,
            Kind.IPD,
            Kind.ICP,
            Kind.IF,
            Kind.IFD,
            Kind.C,
            Kind.HB,
        }

    @property
    def is_volumetric(self) -> bool:
        return not self.is_planar

    @property
    def is_rounded(self) -> bool:
        return self in {Kind.P, Kind.CP}

    @property
    def is_surface_breaking(self) -> bool:
        return self in {Kind.U, Kind.BT, Kind.AB, Kind.IC}

    @property
    def is_aggregated(self) -> bool:
        """Whether several of these add up against a length of weld."""
        return self in {
            Kind.IP,
            Kind.IPD,
            Kind.ICP,
            Kind.IF,
            Kind.IFD,
            Kind.IC,
            Kind.ESI,
            Kind.HB,
            Kind.U,
        }

    @property
    def is_always_rejectable(self) -> bool:
        """A crack is a crack. There is no length that makes one acceptable."""
        return self is Kind.C

    @property
    def code(self) -> str:
        return self.name

    def __str__(self) -> str:
        return self.name


PLANAR = tuple(k for k in Kind if k.is_planar)
VOLUMETRIC = tuple(k for k in Kind if k.is_volumetric)


def by_code(code: str) -> Kind:
    """Read a report abbreviation back into a kind."""
    try:
        return Kind[code.strip().upper()]
    except KeyError:
        raise ValueError(f"no discontinuity code {code!r}") from None


def describe(kind: Kind) -> str:
    shape = "planar" if kind.is_planar else "volumetric"
    return f"{kind.code}, {kind.value}, {shape}"
