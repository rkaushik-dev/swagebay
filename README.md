# swagebay

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

## What it will refuse to do

Plan a shot whose unsharpness cannot meet the code at any distance. Record a
reading by somebody whose certification or eye exam has lapsed. Book a source
out that is not wiped, in a camera that is not inspected. Close a job with an
open non conformance. Close a report without somebody other than its author.
Take a file into the store under a name that cannot be derived from the
exposure it claims.

Every one of those is a refusal with the reason attached, not a warning.

## Tech stack

- Java 21, built with Maven through the wrapper (`./mvnw`).
- Spring Boot 3.5 (Web MVC, Bean Validation) for the HTTP API, with springdoc
  serving the OpenAPI document.
- JPA with Hibernate on H2 for the records, Flyway for the schema.
- picocli for the command line.
- JUnit 5 and AssertJ for the tests, JaCoCo for the coverage gate and Spotless
  (Palantir Java Format) for formatting.

## Building and testing

```
./mvnw -B verify
```

That compiles with every lint warning treated as an error, runs the whole test
suite, checks the formatting and fails if line coverage drops below 80 percent.
`./mvnw spotless:apply` fixes the formatting. One test class runs with
`./mvnw test -Dtest=AcceptanceTest`.

The build produces one runnable jar, `target/swagebay-1.0.0.jar`, whose entry
point is the command line.

## The command line

```
java -jar target/swagebay-1.0.0.jar init
java -jar target/swagebay-1.0.0.jar --help
```

`init` builds the schema in the database named by `--database` (a JDBC url),
or by the `SWAGEBAY_DATABASE` environment variable, or `jdbc:h2:file:./swagebay`
in the working directory when neither is given.

Three commands worth knowing about:

```
swagebay morning              # everything outstanding, worst first
swagebay doctor               # rows that cannot be true at the same time
swagebay store verify 24-0001 /media/card
```

All three exit non zero when they find something, so they can be the last line
of a script. A refusal prints one line with its stable code on it, and the exit
status names the class of failure:

| status | meaning |
| --- | --- |
| 0 | done |
| 1 | anything else |
| 2 | a value outside what the domain allows, or a usage error |
| 3 | a record asked for by identity does not exist |
| 4 | a write would break an invariant, or a gate found something |
| 5 | the shot as described cannot be made |
| 6 | a rule table or procedure is missing or contradicts itself |

The command groups are `catalogue`, `device`, `doctor`, `history`, `init`,
`job`, `morning`, `ncr`, `register`, `report`, `safety`, `shop`, `source`,
`store`, `technique` and `weld`; `--help` on any of them lists its commands.

## The HTTP API

The same domain behind Spring Web MVC. Start it against the default database,
from the built jar or from the source tree:

```
java -cp target/swagebay-1.0.0.jar \
  -Dloader.main=io.github.rkaushikdev.swagebay.api.SwagebayApplication \
  org.springframework.boot.loader.launch.PropertiesLauncher --server.port=8000

./mvnw spring-boot:run -Dspring-boot.run.main-class=io.github.rkaushikdev.swagebay.api.SwagebayApplication
```

The service does not build the schema; `swagebay init` does.

The OpenAPI document is at `/openapi.json` and the interactive page at `/docs`.
Domain refusals come back as `{"code", "message", "context"}`: 404 for a record
that is not there, 409 for a conflict or a safety refusal, 422 for a bad value,
an impossible technique or an inadmissible image, and 500 for a configuration
fault. A request that does not match its shape is a 422 with a `detail` list.

| method | path | what it does |
| --- | --- | --- |
| GET | `/health` | liveness and the version |
| POST, GET | `/jobs` | open a job; list the jobs |
| GET | `/jobs/{number}` | one job |
| POST, GET | `/jobs/{number}/welds` | add a weld; list the welds |
| GET | `/jobs/{number}/radiographs` | the films, paged with a cursor |
| GET | `/jobs/{number}/outstanding` | what still stops the job closing |
| POST | `/jobs/{number}/close` | close the job |
| GET | `/jobs/{number}/checklist` | the close out checklist |
| POST | `/jobs/{number}/welds/{weld}/technique` | plan the technique sheet |
| POST | `/jobs/{number}/welds/{weld}/technique/compare` | the same shot on several films |
| POST | `/jobs/{number}/welds/{weld}/technique/fastest` | the fastest workable film |
| POST | `/jobs/{number}/welds/{weld}/shots` | record an exposure |
| POST | `/jobs/{number}/welds/{weld}/radiographs/{radiograph}/reading` | record a reading |
| POST | `/jobs/{number}/welds/{weld}/radiographs/{radiograph}/reread` | record a second reading |
| GET | `/jobs/{number}/welds/{weld}/radiographs/{radiograph}/history` | the audit trail of one film |
| GET | `/jobs/{number}/report` | the report package |
| GET | `/jobs/{number}/report/traceability` | the chain behind each radiograph |
| GET | `/jobs/{number}/report/sheets` | the export sheet names |
| GET | `/jobs/{number}/report/{sheet}.csv` | one export sheet |
| POST, GET | `/sources` | register a source; list them |
| GET | `/sources/{serial}` | one source |
| GET | `/sources/{serial}/fitness` | whether it may go out, and why not |
| POST | `/sources/{serial}/send-out`, `/bring-back`, `/retire` | movements |
| POST, GET | `/devices/{serial}/inspections` | record and list device inspections |
| GET | `/devices/{serial}/clearance` | whether a device and source may leave the shop |
| POST | `/safety/boundaries` | the rope off distance to each boundary for a source in the open |
| POST | `/safety/radiographs/{radiograph}/surveys` | record a survey |
| POST, GET | `/registers/incidents` | report an incident; list them |
| GET | `/registers/incidents/{reference}` | one incident |
| POST | `/registers/incidents/{reference}/notify` | record the notification |
| POST | `/registers/calibrations` | record a calibration |
| GET | `/registers/calibrations/expired` | instruments out of calibration |
| GET | `/registers/calibrations/{serial}` | an instrument's latest calibration |
| POST, GET | `/nonconformances` | raise a report; list them |
| GET | `/nonconformances/{reference}` | one report |
| POST | `/nonconformances/{reference}/decision` | decide the disposition |
| POST | `/nonconformances/{reference}/closure` | close it |
| GET | `/morning` | the morning sheet |
| GET | `/doctor` | rows that cannot be true at the same time |

## How it is built

Whole integers underneath and decimal arithmetic on top. A wall is a `Length`
in micrometres, an activity is an `Activity` in becquerels, an exposure is a
`Duration` in milliseconds, a dose is a `Dose` in nanosieverts. Nothing takes a
bare number, and decimal results are held to 28 significant digits with the
logarithms, exponentials and roots correctly rounded.

One unit of work per request and per command: an application-managed
`EntityManager` with its own transaction, committed when the work returns and
rolled back when it throws, and repositories that refuse once the unit of work
is closed. Every constraint and index in the schema has a deterministic name,
the Flyway migrations build the schema, and Hibernate validates the entities
against it.

## Project layout

```
src/main/java/io/github/rkaushikdev/swagebay/
  Swagebay.java, *Error.java   the front door, the version and the error hierarchy
  quantities/ identity/ clock/ materials/
  geometry/ imaging/ exposure/ sources/ evaluation/ records/ technique/ shotplan/
  safety/ personnel/ procedure/ audit/ calibration/ nonconformance/ scheduling/
  stock/ welding/ naming/      the domain rules, free of any framework
  persistence/                 entities, repositories, mappers, the unit of work
  services/                    one piece of work each, as the API and CLI call them
  reporting/                   the report package, exports, submittal, traceability
  api/                         the HTTP API
  cli/                         the command line
  util/                        decimal, float, text and time primitives
src/main/resources/db/migration/   the schema, V1 to V9
src/test/java/                     one test class per area, same packages
```
