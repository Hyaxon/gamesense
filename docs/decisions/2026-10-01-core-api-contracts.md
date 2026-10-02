# ADR 2: Core API Payload Contracts

## Context

The frontend, Java backend, and Python prediction service need shared JSON contracts built on the shared domain models for teams, games, and prediction results.

The MVP uses no database. Custom predictions represent user-selected matchups independent of upcoming schedules. The intended prediction summary is LLM-generated and is deferred to later polish.

## Decision

- Use JSON Schema draft 2020-12, camelCase fields, string identifiers, uppercase enum literals, and UTC timestamps, following the shared-domain conventions ADR.
- Use the shared domain models as the source of truth for entity fields and identifier conventions. API-specific requests and response containers describe how those entities are exchanged.
- Describe a custom matchup using two existing team identifiers, a season, and an explicit neutral-site flag. Home/away identify consistent sides. Season selects that season's team data; this is a proposed interpretation requiring team confirmation, not a cutoff for historical evaluation.
- Require explicit requested methods and explicit trial counts when simulation is requested. Document unavailable methods as a runtime validation error; enumeration does not promise implementation availability.
- Give scheduled standalone results a gameId. Inside a prediction response, the containing matchup supplies context; custom responses omit gameId. This avoids manufacturing a scheduled game for an independent matchup.
- Keep the existing predictedWinnerTeamId plus confidence representation for single-method and aggregate results. Simulation results carry probabilities for both teams. Consumers convert single-result confidence to a consistent team's probability before display or aggregation.
- Deterministic outputs use the single-result wire shape. Determinism depends on execution and inputs, not merely algorithm name or computation time.
- Use omitted optional properties for the new contracts; null is not accepted. Game score and winner fields retain the nullability defined by the shared Game schema.
- Reject unknown properties in payload objects. Future fields require a reviewed schema revision and coordinated consumer updates.
- Use one error object with a stable code, human-readable message, optional request correlation identifier, and optional JSON Pointer field details.
- Return complete success or one error for MVP prediction execution. Partial method failures and asynchronous job contracts are deferred.
- Set a proposed maximum of 100,000 trials and a nonnegative signed-32-bit seed for portable MVP inputs. Confirm the trial cap against engine capacity before acceptance.
- Defer LLM summaries, database persistence, model performance badges, and richer venue presentation. Record these as future work rather than adding placeholder fields.

## Consequences

The MVP has explicit inputs and outputs independent of persistence. Custom and scheduled results share a representation while retaining different identity semantics. Java, Python, and JavaScript can consume the same primitive types.

Relative references resolve through a local schema registry; schema IDs are identifiers, not hosted endpoints. Structural validation does not check team existence, winner membership, probability sums, or referenced result consistency. These rules need service-level validation when services are implemented.

Before acceptance, confirm season semantics and simulation limits, and review the contracts with the team. This ADR does not choose algorithm implementations, aggregation weights, endpoints, or transport changes.
