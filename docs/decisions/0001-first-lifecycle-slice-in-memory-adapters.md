# First Lifecycle Slice In-Memory Adapters

The first season lifecycle slice uses deterministic in-memory repositories and stubbed integration adapters behind application services.

Temporary adapters introduced:

* `InMemorySeasonPlanRepository`
* `InMemoryPollenReservationRepository`
* `InMemoryMagicTaskRepository`
* `InMemoryFairyAssignmentRepository`
* `AllowingNaturalCalendarService`
* `NotificationService`

These adapters preserve service contracts for the vertical slice and keep domain logic independent from Spring, PostgreSQL, and external integration clients. They are intended to be replaced by PostgreSQL persistence and real HTTPS integrations in later use cases while reusing the service tests as behavioral compatibility tests.
