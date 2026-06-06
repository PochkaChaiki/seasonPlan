# Coding Agent Task: Implement first 3 season lifecycle use cases

## Goal

Implement the first vertical slice of the season coordination system:

1. **Планирование сезона**
2. **Согласование сезонного плана**
3. **Подготовка сезона к запуску**

The implementation must support the lifecycle:

`DRAFT → SUBMITTED → APPROVED / DRAFT → RITUAL_READY`

## Core domain entities

Implement at minimum:

### SeasonPlan

Fields:

* id
* seasonType
* targetTerritories
* timeWindows
* naturalPhenomenonParameters
* targetClimateMetrics
* status
* approvalComment
* readinessChecklist
* createdBy
* updatedBy
* createdAt
* updatedAt

Statuses:

* DRAFT
* SUBMITTED
* APPROVED
* VERIFIED
* RITUAL_READY

### PollenReservation

Fields:

* id
* seasonPlanId
* requestedAmount
* confirmedAmount
* status: PENDING, CONFIRMED, REJECTED
* comment
* confirmedBy

### MagicTask

Fields:

* id
* seasonPlanId
* title
* description
* requiredTalent
* timeWindow
* status: TODO, READY, BLOCKED

### FairyAssignment

Fields:

* id
* magicTaskId
* fairyId
* role
* talent
* status: PROPOSED, CONFIRMED

### AuditLog

Append-only record for critical actions:

* SEASON_PLAN_CREATED
* SEASON_PLAN_UPDATED
* SEASON_PLAN_SUBMITTED
* SEASON_PLAN_APPROVED
* SEASON_PLAN_RETURNED
* POLLEN_RESERVATION_CONFIRMED
* POLLEN_RESERVATION_REJECTED
* MAGIC_TASK_CREATED
* FAIRY_ASSIGNED
* SEASON_MARKED_RITUAL_READY

## Use case 1: Планирование сезона

Implement ability for role `FAIRY_PLANNER` to create and edit SeasonPlan.

Required behavior:

* Create SeasonPlan as DRAFT.
* Validate required fields:

  * seasonType
  * timeWindows
  * targetTerritories
  * naturalPhenomenonParameters
  * targetClimateMetrics
* Validate no overlapping season windows inside the same plan.
* Validate against Natural Calendar service/rules.
* Block save if required fields or calendar rules fail.
* Allow saving as DRAFT without submitting.
* Allow submitting only valid DRAFT plan.
* On submit, set status to SUBMITTED.
* Write AuditLog for create, update, submit.

## Use case 2: Согласование сезонного плана

Implement ability for role `CHIEF_GUARDIAN` to review submitted SeasonPlan.

Required behavior:

* Only SUBMITTED plans can be approved or returned.
* Display/read plan details, validation status, history, readiness indicators.
* Approve plan:

  * status becomes APPROVED
  * approval decision is stored
  * AuditLog is written
  * notification is sent to the planner
* Return plan for revision:

  * status becomes DRAFT or VERIFIED
  * rejection/comment is required
  * AuditLog is written
  * notification is sent to the planner
* If PollenReservation exists and is REJECTED, approval must be blocked.

## Use case 3: Подготовка сезона к запуску

Implement ability for roles `TALENT_COORDINATOR` and `POLLEN_GUARDIAN` to prepare APPROVED SeasonPlan.

Required behavior:

* Only APPROVED plans can enter preparation.
* Show resource reservation, tasks, roles, fairy availability, readiness checklist.
* Pollen Guardian can confirm or reject PollenReservation.
* System validates pollen limits before confirmation.
* Talent Coordinator can create/update MagicTask.
* Talent Coordinator can assign fairies through FairyAssignment.
* Validate:

  * all required roles are assigned
  * assigned fairies have compatible talents
  * no fairy schedule conflicts
  * pollen reservation is CONFIRMED
  * readiness checklist is complete
* If all checks pass, allow transition to RITUAL_READY.
* If any check fails, block RITUAL_READY and return structured errors.
* Write AuditLog for reservation decisions, task changes, assignments, and RITUAL_READY transition.

## Suggested service structure

Implement application services:

* SeasonPlanService

  * createDraft()
  * updateDraft()
  * validatePlan()
  * submitForApproval()
  * approve()
  * returnForRevision()
  * markRitualReady()

* PollenReservationService

  * requestReservation()
  * confirmReservation()
  * rejectReservation()
  * validateLimits()

* MagicTaskService

  * createTask()
  * updateTask()
  * listTasksBySeasonPlan()

* FairyAssignmentService

  * assignFairy()
  * validateTalentCompatibility()
  * validateScheduleConflicts()
  * validateRequiredRoles()

* ReadinessChecklistService

  * calculateChecklist()
  * assertReadyForRitual()

* AuditLogService

  * append()

* NotificationService

  * notifyPlannerApprovalResult()

## API endpoints

Implement REST or equivalent routes:

* POST /season-plans
* PATCH /season-plans/{id}
* POST /season-plans/{id}/submit
* POST /season-plans/{id}/approve
* POST /season-plans/{id}/return
* GET /season-plans/{id}
* GET /season-plans/{id}/readiness
* POST /season-plans/{id}/pollen-reservations
* POST /pollen-reservations/{id}/confirm
* POST /pollen-reservations/{id}/reject
* POST /season-plans/{id}/tasks
* PATCH /tasks/{id}
* POST /tasks/{id}/assignments
* POST /season-plans/{id}/mark-ritual-ready

## Acceptance criteria

* A planner can create a valid DRAFT SeasonPlan.
* Invalid plans cannot be saved/submitted and return field-level errors.
* A valid DRAFT plan can be submitted and becomes SUBMITTED.
* A chief guardian can approve SUBMITTED plan and it becomes APPROVED.
* A chief guardian can return SUBMITTED plan with comment and it becomes DRAFT or VERIFIED.
* Approval is blocked if resource reservation is rejected.
* Coordinator can create tasks for an APPROVED plan.
* Coordinator can assign fairies only when talent and schedule checks pass.
* Pollen Guardian can confirm reservation only within limits.
* RITUAL_READY transition is blocked until reservation, roles, assignments, tasks, and checklist are complete.
* Every critical action creates immutable AuditLog entry.
* Role permissions are enforced for every command.

## Testing requirement

Use Red-Green-Refactor TDD.

Start with tests for:

1. SeasonPlan creation validation.
2. Submit transition DRAFT → SUBMITTED.
3. Approval transition SUBMITTED → APPROVED.
4. Return transition SUBMITTED → DRAFT/VERIFIED with required comment.
5. Approval blocked by rejected PollenReservation.
6. RITUAL_READY blocked by incomplete checklist.
7. RITUAL_READY allowed when all readiness checks pass.
8. AuditLog written for each lifecycle transition.
9. Role-based access denied for unauthorized actors.

Do not implement later use cases: RitualSession launch, active monitoring, incidents, correction actions, completion, reports, or archive.
