# Resource safety implementation checkpoint — before review 9

This is implementation evidence, not an independent review or finding closure.

## Authority and limit

The user limited this continuation to **one iteration maximum**. Complete this implementation/verification/review checkpoint, then stop. Do not start another fix/review cycle under the earlier six-pass allowance.

The consumption worker hit its provider usage limit. Its original RED-only artifact predates its later partial production and PostgreSQL test changes. The parent inspected those changes and completed the same consumption slice directly. No alternate CLI or hidden agent fallback was used.

## Implemented boundary

- The previous reservation checkpoint passed 534 Java 17 backend tests and 54 frontend tests. Its durable request/confirm/reject phases, immutable material-revision binding and operation-ID recovery remain in place.
- Consumption has a separate immutable `ConsumptionIntent` and receipt, stored through the same pollen-owned repository contract. Forward migration 018 adds the consumption evidence table, history foreign key, payload/state constraints, immutable evidence triggers and narrow runtime grants. Migrations 001–017 were not changed in this continuation.
- A new consumption operation captures the plan revision, original actor, exact normalized amount, `(planId, clientEventId)` identity and intended agreement. A durable claim precedes spending. Spending occurs without an ambient database transaction or season row lock.
- The outbox must resolve to a non-null implementation before provider access. Ambient command invocation is rejected before provider access.
- A known receipt commits before local audit/outbox/completion. Audit and notification writes roll back together on failure; receipt recovery does not repeat spending. Retry returns the original event snapshot, not a newer aggregate.
- Unknown outcomes never authorize another provider mutation. An exact event receipt can support read-only reconciliation; absent/pending/unsupported lookup after a dispatch claim does not permit redispatch.
- Proven pre-mutation denials become terminal DENIED evidence, not an uncertain accepted spend. No success audit is written for denial.
- `PollenOperations.reconcileConsumption` provides owner Java-API recovery by operation ID with the existing consumption permission. It preserves the initiating actor. Consumption remains core-only: no execution HTTP endpoint or browser recovery screen is claimed.
- Unresolved reservation or consumption evidence blocks new resource mutations and settled report usage. Funding/readiness evidence also carries the combined uncertainty flag. Accepted mismatched, obsolete or ineligible consumption receipts remain durable and quarantined, without a manual success override.
- Consumption does not create reservation funding bindings. Legacy/unbound funding cannot become current through a consumption read or receipt.

## Provider and legacy limits

The educational fake implements exact-event lookup and authoritative pre-dispatch absence. The REST adapter has no verified event-lookup contract and reports UNKNOWN. The owner therefore does not send a consumption POST through that unsupported adapter. No provider endpoint, idempotency guarantee or exactly-once execution was invented.

Consumption preserves the old provider event key rather than generating a new nonce that could charge an old event again. A known historical amount mismatch is rejected before creating a poisoned intent. Matching legacy replay preserves the actual stored audit record and does not repeat provider mutation. Conflicting old actor/audit provenance fails closed; it is not rewritten.

No live-provider, deployed migration, native FreeBSD or process-kill crash test was performed. Restart tests construct fresh services and repositories over committed PostgreSQL data. Quarantined/unverifiable outcomes can remain blocked; unsupported release/recovery policy was not invented.

## Red–green evidence

The worker first observed three RED invocation-count regressions: accepted response loss, audit failure and outbox failure each caused two provider mutation calls instead of one. Those tests now pass.

Parent observations before the corresponding fixes:

1. `PollenConsumptionSafetyTest`: four assertion failures and one error among six tests. These demonstrated missing public owner recovery, cross-plan event-key collision, poisoned legacy amount replay, denial classified as uncertainty, and unsettled reservation usage returned as settled. Ambient entry already passed.
2. Missing-agreement regression: one expected failure; the old partial code attempted provider mutation instead of rejecting missing confirmed evidence.
3. PostgreSQL/policy focused run: 30 tests, one expected schema failure. A malformed empty JSON intent could be inserted because PostgreSQL CHECK permits NULL. Required evidence fields are now checked explicitly.
4. Missing-outbox regression: one expected failure; provider reads occurred before null composition was detected. Both consumption entry points now require a non-null outbox first.

During integration, the new timestamp check incorrectly assumed ISO JSON instead of also allowing Jackson numeric instants. The full run exposed that error. The check now accepts both serializer representations without changing the immutable captured value. Added ACTIVE PostgreSQL fixtures also needed distinct territories to avoid real window conflicts; pending-outcome assertions now require the stable pending exception, not a business validation error. A legacy-audit comparison now takes its original value from PostgreSQL before replay, preserving stored timestamp precision rather than comparing it with a transient nanosecond value.

No regression tests were disabled or removed.

## Automated evidence

New `PollenConsumptionPersistenceTest` executes eight PostgreSQL cases using an actual LOGIN granted `seasonplan_runtime`:

- Committed intent visible before spending; an independent connection can lock the same season during provider mutation; ambient invocation touches no provider method.
- Actual audit and outbox insertion followed by rollback, independently committed receipt, fresh-service local-only completion, exactly one mutation.
- Accepted timeout, operation-ID recovery after restart, original actor and original snapshot after later spending.
- Unknown outcome blocks another spend, reservation replacement and settled report usage, without redispatch.
- Ineligible accepted receipt is retained and quarantined.
- Concurrent same-event retry dispatches once.
- Runtime payload/receipt rewrites, DELETE, TRUNCATE and guard-changing DDL are denied; malformed evidence insertion is rejected.

Worker-added PostgreSQL consumption cases remain enabled in `PollenJournalPersistenceTest`, including legacy-key/audit replay with no invented funding binding. Existing reservation/revision, architecture, recursive immutable API, ownership, security and restricted-runtime tests remain enabled.

## Final execution

Actual Temurin Java 17 container command, one Maven runner, timeout 600 seconds:

```powershell
powershell.exe -NoProfile -Command 'docker run --rm --mount "type=bind,source=X:\projects\seasonPlan,target=/workspace" --mount "type=bind,source=C:\Users\pochk\.m2,target=/root/.m2" --mount "type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e DOCKER_API_VERSION=1.44 -w /workspace/backend seasonplan-backend:latest mvn -B "-Dapi.version=1.44" clean verify 2>&1 | Tee-Object -FilePath "$env:TEMP\seasonplan-single-iteration-verify.log" | Select-Object -Last 50; exit $LASTEXITCODE'
```

**562 tests, zero failures/errors/skips, BUILD SUCCESS**, executable JAR packaged. Elapsed 5:02; completed at 2026-11-22T12:10:23Z. JPA context and connection-pool shutdown completed normally.

Frontend: **54 tests passed**, lint zero errors / one existing `AuthContext.jsx:63` Fast Refresh warning, production build passed. No frontend files were changed in this continuation.

At this implementation checkpoint, architecture acceptance was pending independent review 9; passing tests alone did not establish acceptance.

## Independent outcome and stop

[Review 9](architecture-review-pass-9.md) subsequently accepted the reviewed integrated safety repairs with **zero HIGH and zero CRITICAL findings**. It closes AR-03/AR-04 and retains AR-01/08/17 as closed. The parent accepts that scoped disposition. Retained MEDIUM/LOW findings and live-provider/deployment limits remain. This is not full feature completion or production-readiness certification. The single authorized iteration is complete; no further fix/review cycle was started.
