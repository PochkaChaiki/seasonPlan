# UC-2 implementation activity — executable calls

Open **[SeasonPlan-UC2-implementation.vpp](SeasonPlan-UC2-implementation.vpp)** in Visual Paradigm 18.1 and select **3. UC-2 - Implementation**.

This is an edited **copy**, not a replacement of your original project. The abstract, logical and package views retain the native identities, elements, texts, bounds and connector routes from the input snapshot. The original `.vpp` and its backups are untouched. Preserved views retain their historical target-design notes; only the implementation view has been refreshed against current code.

## Implementation view

- English-only action labels, guards and partition headers.
- Concrete existing method calls, assignments and field expressions; no proposal/change-request labels.
- Native UML 2 actions, decision/merge/control nodes, control flows and exception handlers.
- Native CallOperationAction references with typed operation parameters/results. JavaScript state updates and exception expressions use opaque action effects.
- No notes, object nodes, class diagram or class-relationship connectors. Class/field use appears in call expressions, as requested; activity arrows remain control/exception flows, not associations or inheritance.
- The persistence order follows the implementation: save plan → append audit → enqueue outbox → COMMIT. Failures before commit roll back; notification processing does not hold the HTTP response. Its fork branch denotes independently scheduled work, not a direct invocation by the controller.

[SVG preview](03-implementation.svg) · [PNG preview](03-implementation.png) · [native validation](validation.json)

Previews were exported by Visual Paradigm from a separate-process reopen of the delivered native project. Evaluation watermarks are preserved. Model-level checks and export inspection are not full desktop GUI automation.

## Application changes

UC-2 now has explicit verification, VERIFIED-only approval, motivated return from both review statuses, immutable persisted approval conditions, guarded review history, command-time plan/calendar/territory validation, transactional audit/outbox persistence and background delivery. See the [backend documentation](../../../../backend/README.md) and [API contract](../../../api/openapi.yaml) for source-backed endpoints and configuration.

External configuration still matters: real Pollen Tree compatibility and live SMTP sending require actual provider settings. SMTP also needs an explicit creator-ID-to-email mapping because the current user model has no email field. Missing transport/address configuration fails closed and leaves delivery retryable; it is not reported as a delivered email. Conditional approval is stored by UC-2; unsupported/unevidenced conditions remain unmet in UC-3 readiness, rather than being bypassed by a manual checklist flag.

## Verification

- **275 backend tests passed** under Temurin Java 17.0.21, including **18 PostgreSQL integration tests** (rollback, concurrency, inbox isolation and missing-plan endpoints).
- **43 frontend tests passed**; the production build passed.
- **18 diagram-source tests passed** (8 repair-specific plus 10 historical-source checks); saved/reopened native validation passed, including all 56 painted control flows, 11 exception-handler links, and rebuilt connector drawing bounds.
- Independent code and native-artifact reviews accepted the fixes. Native verification is tied to the delivered project's SHA-256 in `validation.json`.

Project operations link to source paths; Spring transaction operations are library-backed. Types are participating-operation projections, not an added class inventory.

## Reproduce safely

From the repository root in PowerShell 5.1:

```powershell
python -B -m unittest discover -s .\docs\architecture\uc-2\tools -p test_implementation.py
python -B .\docs\architecture\uc-2\tools\validate_implementation.py .\docs\architecture\uc-2\implementation-revised

$out = Join-Path $env:TEMP ('uc2-revised-' + [guid]::NewGuid().ToString('N'))
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\docs\architecture\uc-2\tools\Revise-Implementation.ps1 -Output $out
```

The runner refuses to overwrite an existing output project, copies the baseline before any VP process opens it, compares the preserved views before/after, and removes its own temporary plugin/scratch files. It uses the tested original activity authoring template but replaces only the implementation activity; it does **not** create a fresh project and discard your other views. `original-project.sha256` identifies the input snapshot; `original-native.xml` supports the preservation check.
