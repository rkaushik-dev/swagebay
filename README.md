# qu_imager

Radiographic technique planning, image quality qualification and shop records
for a code stamped NDT contractor. It plans the shot, decides whether the film
or the plate is admissible, judges the weld against the code, keeps the safety
and equipment registers, and produces the package the client is owed.

It is not a viewer and it does not touch pixels. It works on what the reader
measured and what the record says.

## What it covers

**Technique.** Radiographic equivalence factors and pipe schedules through to
penetrated thickness, source to object distance, geometric unsharpness and the
exposure. Single wall single image, double wall single and double image both
elliptical and perpendicular, and panoramic, with the coverage and the overlap
worked out rather than assumed.

**Image quality.** Hole and wire indicator selection with the essential hole
and essential wire, density range and variation, normalised signal to noise,
basic spatial resolution off a duplex gauge, computed radiography and detector
array classes, and one admissibility verdict with the reasons on it.

**Acceptance.** The discontinuity taxonomy, aggregate length in a sliding
twelve inch window, and the acceptance rules for API 1104 section 9.3 and ASME
Section VIII UW-51, with repairs and the repair rate.

**Safety.** The dose rate field, restricted and high radiation boundaries,
survey records, dosimetry with investigation levels, transport packages and
labels, source accountability and utilisation.

**Records.** Jobs, welds, radiographs and indications, gapless report numbers,
the five small registers, the image store with a digest per file, non
conformance reports, source exchanges, device inspections and an audit trail.

## Running it

```
pip install -e ".[dev]"
qu-imager init
qu-imager --help
```

Three commands worth knowing about:

```
qu-imager morning              # everything outstanding, worst first
qu-imager doctor               # rows that cannot be true at the same time
qu-imager store verify 24-0001 /media/card
```

All three exit non zero when they find something, so they can be the last line
of a script.

The http api is the same domain behind FastAPI:

```
uvicorn qu_imager.api.app:build_default_app --factory
```

## What it will refuse to do

Plan a shot whose unsharpness cannot meet the code at any distance. Record a
reading by somebody whose certification or eye exam has lapsed. Book a source
out that is not wiped, in a camera that is not inspected. Close a job with an
open non conformance. Close a report without somebody other than its author.
Take a file into the store under a name that cannot be derived from the
exposure it claims.

Every one of those is a refusal with the reason attached, not a warning.

## How it is built

Whole integers underneath and Decimal arithmetic on top. A wall is a `Length`
in micrometres, an activity is an `Activity` in becquerels, an exposure is a
`Duration` in milliseconds, a dose is a `Dose` in nanosieverts. Nothing takes a
bare number and nothing uses a float.

One unit of work per request and per command. SQLAlchemy 2.0 with the value
objects as column types, alembic for the schema, one transaction per piece of
work, and repositories that refuse when the unit of work is closed.

## Development

```
ruff check . && ruff format --check .
mypy --platform linux && mypy --platform win32
pytest --cov=qu_imager
alembic upgrade head && alembic downgrade base && alembic upgrade head
```

CI runs all of that on Linux and Windows against 3.11 and 3.12, plus a build of
the wheel and a check that the console script runs out of a clean environment.
