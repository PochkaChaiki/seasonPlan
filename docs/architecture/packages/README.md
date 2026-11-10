# SeasonPlan — UML package diagrams

Open **[SeasonPlan-packages.vpp](SeasonPlan-packages.vpp)** in Visual Paradigm
18.1. It is a native, editable project: real UML Packages, Dependencies and
registered notes, not an imported image. No authoring plugin is needed to view or
edit it. Previous UC-2/UC-4 projects were not changed.

## Views

| Native diagram | Scope | Preview |
| --- | --- | --- |
| 1. SeasonPlan — Архитектура пакетов | React client, REST/authentication, compatibility facade, application core, domain, JDBC persistence, fake/Pollen Tree adapters and Spring configuration; 10 package shapes, 22 dependencies | [PNG](01-system.png) · [SVG](01-system.svg) |
| 2. SeasonPlan — Прецеденты и контракты | Five use cases, cross-use-case pollen boundary, shared support, ports and domain; 10 package shapes, 30 dependencies | [PNG](02-application.png) · [SVG](02-application.svg) |

The first view rolls all `application.*` dependencies into `application`. In the
second, the `application` box means its **direct** contents (`UseCaseSupport`),
not its descendants. The native model tree preserves actual Java package
ownership; package nesting is not inferred from where boxes happen to be placed.
`frontend/src` is an architectural grouping of JavaScript folders, not a Java
package. Bootstrap class `com.seasonplan.SeasonPlanApplication` is inventoried
but has no project-package imports and is omitted from diagram shapes.

Arrows point **client → supplier**. Backend arrows are direct project-package
imports (including wildcard imports), not execution order or UML PackageImport.
The two named client arrows represent HTTP/JSON contracts, not Java imports.
Third-party frameworks, PostgreSQL and provider systems are described in notes,
not misrepresented as application packages. These are not Maven modules or
microservices. Body notes list representative types; they are not class models.

## Requirements and implementation boundaries

Read together: [Vision](../../../arch_docs/VISION.md),
[SRS](../../../arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md),
[use cases](../../../arch_docs/USE_CASE_SPECIFICATION.md),
[ST-00 resolution](../ST-00-specification-resolution.md),
[backend inventory](../../../backend/README.md), and
[Pollen Tree boundary](../POLLEN_TREE_EXTERNAL_SERVICE.md).

- Actual code has one `domain` package. SeasonPlan, PollenReservation, MagicTask,
  FairyAssignment, RitualSession, ClimateMetric, MagicIncident, CorrectionAction,
  SeasonReport and AuditLogEntry are distinct concepts, **not fabricated Java
  subpackages**.
- `PollenService` is the application boundary; `PollenTreeClient` is its port;
  `RestPollenTreeClient` is the HTTPS adapter. There is no local pollen reservation
  repository or direct database write path. Provider compatibility remains
  unverified. Canonical reservation state is `CONFIRMED`, per ST-00.
- UC-4/UC-5 core services exist but lack complete REST/composition/persistence
  slices. Consumption has no REST route or complete outbox delivery wiring.
  Sensors, real calendar integration and PDF/HTML renderers are not implemented.
  The allowing calendar and notification adapters are explicitly marked stubs.
- UC-1 currently lists plans without search/filtering; cross-plan territory-window
  conflict detection is missing. Its interval checks are intra-plan only. FR-82–85
  are target requirements, not an implemented search API.
- UC-2 verification/conditional approval and other ST-00 conformance gaps are
  not concealed by package dependencies. The diagram does not certify command
  transactions, durable outbox delivery or deployment readiness.
- Only the eight canonical SeasonPlan statuses are valid. Old requirements'
  `CORRECTION`/`EMERGENCY` are operating conditions, not additional lifecycle
  statuses. Audit records remain immutable; completion is blocked by open
  CRITICAL incidents.

## Verification and regeneration

[validation.json](validation.json) records PASS, the delivered project's SHA-256,
checks and limits. The generator saved a new project, opened a **copy in another
VP process**, checked package hierarchy, exact bounds, dependency model/shape
endpoints, orthogonal attachment, obstacle clearance, notes and normal caption
mode, then exported the included previews from that reopened project. Both full
previews were visually inspected. VP evaluation watermarks are intentionally
retained. Desktop selection/drag gestures were **not** automated; caption mode
was inspected through the Open API.

[source-manifest.json](source-manifest.json) includes all 18 production Java
package inventories, dependency import evidence, view geometry and source file
hashes. [packages.xml](packages.xml) is generator input, **not** the native
project; [native-reopened.xml](native-reopened.xml) is the Open API inspection
receipt. Nine source/model/geometry/validation tests pass. No application code changed.

Tooling in [tools](tools) uses Python's standard library, installed Graphviz
(`X:\apps\graphviz\dot.exe`), a Java compiler, and VP's Java 11 runtime. This
documentation-only tooling does not add an application dependency or alter the
Java 17 production target.

From Windows PowerShell, use a **new output directory** to protect manual edits:

```powershell
python -B -m unittest discover -s .\docs\architecture\packages\tools -p test_packages.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docs\architecture\packages\tools\Generate-Packages.ps1 -Output "$env:TEMP\seasonplan-packages-new"
python -B .\docs\architecture\packages\tools\validate_native.py .\docs\architecture\packages
```

`-ExecutionPolicy Bypass` applies only to that child process; no machine policy
is changed. The runner copies the UC-4 baseline before opening it, checks its
hash afterward and removes its own temporary plugin/scratch project. It refuses
to overwrite an existing output VPP. Package summaries and test expectations
should be reviewed when the implementation changes, not silently regenerated as
claims of new completed functionality.
