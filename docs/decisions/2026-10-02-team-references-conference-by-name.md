# ADR 2: Team References Conference by Name, Not ID

## Status

Accepted

## Context

ADR 1 set the default rule that every shared entity gets a canonical `id` (a UUID), and that relationships between entities are expressed as `<entity>Id` foreign keys. Under that rule, `Team` referenced `Conference` through a `conferenceId` field, and `Conference` had its own `id`.

Review feedback on the domain model pointed out that this is unnecessary overhead for how conferences actually work in this project: every `Team` record always has its conference given directly, by name. Nothing in the system ever needs to look a conference up by a separate identifier -- there's no case where we have a conference's `id` but not its name, or need to join on anything other than the name. A synthetic `id` here is indirection that nothing on the other end actually uses.

## Decision

`Conference` has no `id`. `Team.conference` holds the conference's full name directly (e.g. `"Southeastern Conference"`), instead of a `conferenceId` pointing at a separate `Conference` record.

This is treated as the one stated exception to ADR 1's "every entity has an `id`" rule, not a reversal of it -- the rest of ADR 1 (UUIDs for entities that are independently created and referenced, camelCase wire format, ISO 8601 timestamps, `UPPER_SNAKE_CASE` enums) still applies everywhere else.

## Consequences

- Positive: one less UUID to generate and keep in sync during ingestion, for a relationship that was never going to be looked up by ID anyway.
- Positive: matches the real data feed directly -- it gives a team's conference as a name string, never as a separate ID, so this removes a translation step ingestion would otherwise have to do for no benefit.
- Trade-off: if a conference ever needs its own independently-managed data (logo, standings page, a flag for whether it currently has a conference championship game, etc.), `Conference` will need an `id` added back in at that point. That's a deliberate "add it when something actually needs it" call, not an oversight.
- Follow-up: `shared/schemas/team.schema.json` and `docs/architecture/domain-model.md` are updated to match (`conference: string`, required; no `conferenceId`, no `division` -- see the note on `Team` in `domain-model.md` for why `division` was dropped too).
