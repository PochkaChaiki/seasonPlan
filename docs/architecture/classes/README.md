# SeasonPlan — complete frontend/backend class model

Open **[SeasonPlan-all-classes.vpp](SeasonPlan-all-classes.vpp)** in Visual Paradigm 18.1.
It is a genuine native, editable UML project; the generation plugin is **not**
required for viewing or editing.

**[Browse the diagram index and reopened native previews](index.html).**
Use SVG for readable zoom; PNG previews are scaled. Evaluation watermarks are
retained. They are produced by the installed VP evaluation edition.

## Contents

| Source-backed elements | Count |
| --- | ---: |
| Backend classifiers: classes, interfaces, records and enums | 163 |
| Frontend JavaScript/React module classifiers | 17 |
| Attributes, including record components and React state projections | 527 |
| Declared operations, including constructors and named JS functions | 607 |
| Enumeration literals | 81 |
| Associations, realizations and dependencies | 487 |
| Class diagrams: 44 member catalogs + 44 relationship views | 88 |

Coverage is the **current working-tree snapshot**, including modified and
untracked production sources: 101 Java files and all 17 production JS/JSX files.
This includes security, API request/response records, notification inbox/outbox
and delivery, configuration, JDBC adapters, educational fakes, application ports,
all five use-case cores, domain models, frontend pages, shared components, auth,
API and local-storage modules. Existing projects and application files were not
modified. This is a new source model, not a merge of the older UC diagram projects.

### Navigation and member policy

- **Members** diagrams show every classifier's complete source-declared fields
  and operations: visibility, static/read-only flags, parameters and return types.
  Enum literals and explicit constructors are native UML members. Long signatures
  wrap rather than being replaced with ellipses. Each classifier has a member view.
- **Relationships** diagrams reuse those same populated native classifiers but
  hide member compartments for clarity. They are collaboration views, **not**
  separate member-free conceptual abstractions. Every modeled relationship is shown.
- Native packages preserve source hierarchy; nested Java types belong to their
  enclosing classifiers. Source paths/lines are available in classifier documentation.
- Interfaces use `Interface`, enums `Enumeration`, and records `record`
  stereotypes on native Class models, following the installed Open API convention.
- External/framework types retain their source type names in signatures. They
  are not expanded into third-party-library class catalogs. Generic type arguments
  remain intact; method type parameters and throws lists are in operation documentation.

### JavaScript is not Java

Each JS/JSX file is a native classifier stereotyped `React module` or
`JavaScript module`. Named components, helpers, nested handlers and object-facade
methods are native operations, retaining lexical qualification (for example,
`PlanReviewPage.decide` and `lifecycleApi.approvePlan`). Props/destructuring appear
as operation parameters. Module bindings and hook-state variables are attributes;
these are **source projections, not claims that React functions are class instances**.

JavaScript's undeclared types are `dynamic`; declared `async` functions use
`Promise<dynamic>`. Capitalized function names use the React-component convention
`ReactNode`; nested lower-case handlers do not inherit their owner's return type.
A non-async function that returns a promise remains `dynamic` unless established
otherwise. Runtime JSON/cache objects are not invented as declared domain classes.

Implicit boilerplate is deliberately not duplicated: inherited/library methods,
Java compiler-generated record accessors/canonical constructors/Object methods,
enum helpers, synthetic/bridge fields, anonymous implementations, lambdas, React
hook setters and anonymous JSX/event callbacks. Explicit source declarations are
included even when they replace that boilerplate. Tests, build/diagram tooling,
CSS selectors, SQL tables and dependencies' own source are outside the class catalog.

### Relationship meaning

- Typed project-class fields justify **associations**. Names are the real field
  names. No composition/cascade deletion or UUID-based ownership is invented.
  Cardinalities are intentionally unspecified: a Java reference alone does not
  establish domain optionality or lifecycle constraints.
- Implemented project interfaces justify **realizations**, with native VP
  supplier-to-implementation endpoint ordering and hollow triangles at interfaces.
- Other project type/signature references and ES module imports justify
  **dependencies**. They do not assert runtime call order. Repeated field targets
  are aggregated by classifier pair; evidence is retained in the manifest.
- Four explicitly named `REST / JSON` dependencies link the frontend clients to
  lifecycle, notifications, authentication and administration controllers. These
  are HTTP contracts, not direct Java calls. Frontend `/api` is a proxy prefix;
  backend controller routes are root-relative.

## Requirements and implementation limits

Read alongside `arch_docs/USE_CASE_SPECIFICATION.md`,
`arch_docs/SYSTEM_REQUIREMENTS_SPECIFICATION.md`, `arch_docs/VISION.md` and the
authoritative [ST-00 decisions](../ST-00-specification-resolution.md).
Older requirements mention CORRECTION/EMERGENCY/CANCELLED lifecycle statuses;
the source and ST-00 allow only **DRAFT → SUBMITTED → VERIFIED → APPROVED →
RITUAL_READY → ACTIVE → COMPLETED → ARCHIVED**, including documented return
transitions. Correction/emergency are operating conditions, not extra statuses.
Pollen Tree owns pollen data; `CONFIRMED` is the canonical reservation state.
Audit history is append-only.

This is an **as-built declaration model**, not certification that every requirement
is implemented. UC-4/UC-5 services and ports exist, but their complete REST/UI,
persistence, sensor and report-rendering slices are not wired. Natural Calendar
is an allowing educational stub. Review notifications have JDBC outbox/inbox and
SMTP adapters, but live SMTP/provider delivery is not certified. Conditional
approval evidence remains a readiness blocker until an evidence implementation
exists. See [the current backend runtime notes](../../../backend/README.md).
No proposed services, renderers, lifecycle states or adapters were invented to
make the model appear complete.

## Verification and provenance

- **[validation.json](validation.json)** records the delivered `.vpp` SHA-256,
  inventory/diagram counts and verification limits.
- **[source-manifest.json](source-manifest.json)** contains source hashes, exact
  declaration inventory, relationship evidence and intended geometry.
- **[native-reopened.xml](native-reopened.xml)** is the inspection receipt from a
  **second VP process** loading a disposable copy of the saved native project.
- **[classes.xml](classes.xml)** is generator input, **not** the native deliverable.

Nine automated regression tests cover extraction, implicit interface-member
visibility, inherited nested-type resolution, conservative JS typing, complete
source/diagram coverage, geometry, native-signature comparison and crowded-owner
caption rejection. Native validation checks every field/operation/parameter,
classifier ownership/stereotypes, actual relationship kinds/endpoints, diagram
member settings, positive/nonoverlapping bounds, attached orthogonal routes,
connector drawing bounds, registered note text, normal relationship-caption mode,
source hashes and all reopened native SVG/PNG exports. All 88 views were inspected
at overview scale with representative member/relationship detail checks.
An independent review's three extraction findings were fixed through failing
regression tests before final regeneration. Its bounded follow-up review found no
remaining correctness blockers.

This is model/API, geometry and native-export verification. **Full desktop GUI
interaction and native mouse-drag automation were not performed**; caption mode
is checked through Open API, not claimed as a desktop drag test. Application code
was not changed, so the application suite was not rerun for this documentation task.

## Regeneration

Tools are in [tools](tools). Requirements: installed VP 18.1 with its bundled Java
11 runtime, a JDK capable of Java 17 source parsing, Python 3, Node.js, the existing
frontend `node_modules/@babel/parser`, and the existing documentation Graphviz
installation (`X:\apps\graphviz\dot.exe`). No production dependency was added.

From Windows PowerShell, run into a **new output directory** to preserve edits:

```powershell
python -B -m unittest discover -s .\docs\architecture\classes\tools -p 'test_*.py'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\docs\architecture\classes\tools\Generate-Classes.ps1 `
  -Output "$env:TEMP\SeasonPlan-class-model-new"
```

The policy override applies only to the spawned PowerShell process. The runner
compiles the source extractor for Java 17 and VP plugin separately for Java 11,
protects/hash-checks the existing UC-4 baseline, modifies only disposable native
copies, generates and reopens in separate VP processes, validates, then removes
its temporary installed plugin and scratch projects. Output overwrites are refused
when a native project already exists. Viewers need none of these authoring tools.
