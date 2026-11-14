# Architecture

Architecture notes and diagrams belong here.

- [Visual Paradigm agent handoff guide](VISUAL_PARADIGM_AGENT_GUIDE.md): reusable
  native-project generation, layout, caption interaction and validation lessons.
- [UC-2 UML 2 activity diagrams](uc-2/README.md): native Visual Paradigm project
  with abstract, package-aligned logical and method/object-level implementation
  activities, plus a package reference and reopened native previews. The
  [English implementation revision](uc-2/implementation-revised/README.md) uses
  implemented calls only and preserves the user's original project and other views.
- [UC-3 UML 2 sequence diagrams](uc-3/README.md): native Visual Paradigm project
  with abstract, existing-system-package and method/object views, linked details,
  PostgreSQL, source-backed calls and explicitly marked target-design gaps.
- [UC-4 class diagrams](uc-4/README.md): native Visual Paradigm project with
  use-case, logical and implementation class views, limited to season execution.
- [UC-5 UML 2 communication diagrams](uc-5/README.md): native Visual Paradigm
  cooperation diagrams at abstract, package and method/object levels; completion,
  report approval/export and archival, with reopened previews and regression tests.
- [System package diagrams](packages/README.md): native Visual Paradigm project
  with system architecture and use-case/port dependency views, source import
  evidence, implementation-gap notes and reopened native previews.
- [Deployment view — native FreeBSD / Helios](deployment/README.md): detailed
  UML 2 deployment project with machine characteristics, native artifacts,
  interfaces/security, manual publication and explicitly proposed audit recovery;
  no Docker deployment runtime. Includes reopened previews and validation.

# Architecture decisions

- [ST-00 specification resolution](ST-00-specification-resolution.md) is the
  authoritative transition and business-semantics decision record for the
  remaining vertical slices.
- [Pollen Tree external-service refactor and integration handoff](POLLEN_TREE_EXTERNAL_SERVICE.md)
  records requirements, external ownership, proposed REST contract, configuration,
  recovery behavior and verification limits.
- [Backend package layout and integration boundaries](../../backend/README.md)
  describes the use-case-oriented application core.
- [Backend reorganization changelog](../../backend/CHANGELOG.md) records preserved
  behavior, new execution/completion logic, tests and outstanding adapter work.
