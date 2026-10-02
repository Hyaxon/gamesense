# Core API JSON Schemas

## Status and scope

Proposed contracts for team review. These files define JSON payloads, not REST endpoints, controllers, algorithms, frontend calls, authentication, or storage. The MVP uses no database; identifiers connect application records held in memory or loaded from files.

The prediction summary means an LLM-generated explanation and is deferred. `PredictionResponse` is a structured collection of results, not that summary feature.

[Shared domain models](domain-model.md) and [the existing conventions ADR](../decisions/2026-09-22-shared-domain-model-conventions.md) supply the established terminology. [Core API contracts ADR](../decisions/2026-10-01-core-api-contracts.md) records the proposed decisions here.

### Source-data conventions

Team conference names come from the feed. Conference divisions/groups are outside the project model; shortened team names are optional and may be derived. Venue details remain optional. BYE rows never become games. Source `index: "0"` is a same-day game index and is not a canonical game identifier. Unplayed source rows with outcome `-` and scores `0,0` do not supply actual scores.

## Conventions

- JSON property names are camelCase; enum values are UPPER_SNAKE_CASE. Schema filenames are kebab-case with `.schema.json`.
- Schemas use JSON Schema draft 2020-12. `$id` values under `https://gamesense.dev/schemas/` identify schemas; they do not promise hosting or select an API endpoint.
- Relationships use canonical string identifiers. The current domain ADR requires UUID v4. `format: uuid` checks UUID syntax, not version 4; generation and version enforcement are semantic rules. External identifiers are optional source traceability, not relationship keys.
- New output timestamps use RFC 3339 date-time strings ending in `Z`. Format validation must be enabled. Existing baseline schemas use date-time format without enforcing UTC.
- Probabilities are numbers from 0 through 1, never percentage strings. The frontend formats `0.67` as `67%`.
- Scores and trial counts are integers; predicted scores cannot be negative. Season is a positive integer. No numeric string coercion is part of the contract.
- New optional properties are omitted when unavailable. They cannot contain null. Required properties must be present. Optional does not imply a default.
- Every payload object rejects unknown properties, including nested objects. Array elements have explicit schemas. Future fields require reviewed contract changes.
- An optional nonempty array is omitted when unused rather than sent as an empty array. Result order carries no ranking significance.

## Payload inventory

All files are under [shared/schemas](../../shared/schemas/). Matching complete JSON examples are under [shared/examples](../../shared/examples/), named after each schema.

| Schema | Purpose and domain relationship |
|---|---|
| team | Shared Team entity. |
| game | Existing Game entity, including status and embedded optional venue. |
| game-status | Reusable lifecycle enum corresponding to GameStatus. |
| prediction | Existing standalone PredictionResult for an identified game. |
| common | Reusable identifier, timestamp, probability, method, strategy, and nonblank string definitions. Not an API payload. |
| matchup | Teams, season, and neutral-site context, without implying a scheduled game. |
| custom-prediction-request | User-selected independent matchup and requested methods; optional simulation configurations. |
| simulation-configuration-request | Method, explicit number of trials, optional seed. Can be nested in a custom request; no standalone endpoint is implied. |
| single-prediction-result | Existing PredictionResult fields without gameId; containing response supplies context. Adds nonnegative predicted scores and UTC timestamps. |
| deterministic-prediction-result | Alias of the single-result contract for deterministic execution. |
| simulation-outcome | SimulationResult fields without gameId for a containing response. |
| simulation-result | Standalone scheduled-game SimulationResult including gameId. |
| aggregated-prediction-outcome | AggregatedPredictionResult fields without gameId for a containing response. |
| aggregated-prediction-result | Standalone scheduled-game aggregate including gameId. |
| prediction-response | Matchup, individual predictions, optional simulations, optional aggregate, and optional scheduled game reference. |
| error | Shared failure response for the backend and prediction service. |

## Core request and response examples

A custom prediction does not require a game ID, date, venue, custom rating, or arbitrary algorithm parameters. Location means home/away/neutral for the MVP. Home and away are consistent labels even at a neutral site.

```json
{
  "matchup": {
    "homeTeamId": "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
    "awayTeamId": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
    "season": 2025,
    "isNeutralSite": true
  },
  "methods": ["ELO"],
  "simulations": [{"method": "MONTE_CARLO", "trials": 10000, "seed": 42}]
}
```

`season` provisionally selects that season's team data. It does not define an as-of date or prevent future-data leakage in historical evaluation. Confirm this interpretation with the team before acceptance; an evaluation cutoff would need its own explicit contract.

The matching [response example](../../shared/examples/prediction-response.json) contains `matchup`, a nonempty `predictions` array, optional `simulations`, and an optional `aggregate`. A scheduled-game response also includes `gameId`, and its matchup must match that game's teams and season. Custom responses omit `gameId`; result IDs identify outputs, not persisted records. IDs need not survive a process restart in the MVP.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "The request contains invalid input.",
  "requestId": "request-123",
  "details": [{"path": "/matchup/season", "message": "Season must be a positive integer."}]
}
```

Messages are safe display text, not stack traces or internal exception details. Clients branch on `code`, not message wording. `requestId` is an optional opaque correlation string. Detail `path` values are JSON Pointers; an empty string indicates the root. No HTTP status mapping or endpoint is defined here.

## Prediction probability semantics

Single-method and aggregate confidence refer to `predictedWinnerTeamId`. If a UI displays the home team's probability:

- Predicted winner is home: display confidence.
- Predicted winner is away: display `1 - confidence`.

For example, a model giving Oklahoma 25% predicts the opponent with 75% confidence. Do not average winner-confidence numbers that refer to different teams. Convert to a common team's probability before aggregation. These contracts do not specify the aggregation algorithm or weights. Confidence is not a model's historical accuracy.

Simulation outcomes explicitly carry both probabilities. MVP simulations have two outcomes, with no tie probability. Their sum must be 1 within an absolute tolerance of 0.000001. The optional seed aids repeatability for a supporting implementation; it does not guarantee identical results across models, versions, or languages.

Deterministic execution and simulation are distinct concepts. Machine learning may return a single prediction; computation cost alone does not require trials. A method may support a single output, repeated trials, both, or neither in the current deployment. Requests for unavailable capabilities fail with `UNSUPPORTED_METHOD`.

## Enum definitions

| Enum | Allowed values |
|---|---|
| GameStatus | SCHEDULED, IN_PROGRESS, FINAL, POSTPONED, CANCELLED |
| PredictionMethod | ELO, GLICKO2, TRUESKILL, MONTE_CARLO, MACHINE_LEARNING, BRADLEY_TERRY, HOME_GROWN, RANDOM |
| AggregationStrategy | AVERAGE, WEIGHTED_AVERAGE, MAJORITY_VOTE |
| ErrorCode | VALIDATION_ERROR, NOT_FOUND, UNSUPPORTED_METHOD, PREDICTION_FAILED, INTERNAL_ERROR |

Methods and statuses retain the domain-model meanings. Aggregation strategies describe combination approaches, not a guarantee of calibrated probabilities. Error codes respectively mean invalid input, missing referenced resource/data, unavailable method or execution mode, prediction execution failure, and an unexpected service failure.

## Validation beyond JSON Schema

Structural validation checks fields, types, enums, bounds, formats, array uniqueness where declared, and unknown properties. The following rules require application context or arithmetic and must be checked at service boundaries when those services are implemented:

- Home and away must differ; referenced teams must exist and have usable data for the selected season.
- A predicted winner must be one of the matchup's teams. Canonical identifiers must satisfy the accepted identifier-generation convention.
- Result identifiers must be unique within a response. Results must match requested methods and execution modes. Duplicate simulation configurations for the same method are rejected for the MVP.
- Aggregate contributors must reference single results for that same matchup; in a containing response, they must be present in `predictions`. Standalone aggregates reference results supplied by application context. An aggregate may use a nonempty subset; any weights and their provenance need a later contract if exposed.
- Simulation probabilities must sum to one within the stated tolerance. Trial counts in outputs must match the configuration used.
- Historical winner IDs must agree with scores. FINAL games require actual nonnegative home/away scores. SCHEDULED games must not expose the feed's placeholder zeros as measured scores. A zero score after play begins is valid.
- Unknown kickoff time must not be presented as an actual midnight kickoff. The domain requires scheduledAt; sources that supply only dates need an explicit policy for representing unavailable kickoff times.

The unchanged Game schema currently permits missing final scores, nullable score/winner fields, and inconsistent status/score combinations. The unchanged PredictionResult schema lacks nonnegative predicted-score constraints and UTC-only checks. These structural limitations require the application-level checks listed above.

For new contracts, required predictedWinnerTeamId means tied predictions need a consistent method policy; no nullable winner or tie outcome is introduced here. Existing historical tie behavior remains governed by the domain model.

## Cross-language use

Java uses camelCase DTO/record fields, UUID strings or UUID values, Instant timestamps, enum literals, and double probabilities. Optional numeric fields need boxed types rather than primitive defaults. Python maps camelCase at its boundary to snake_case models and back. JavaScript/TypeScript uses the wire fields directly and formats probabilities for presentation. All three must preserve omission rules and avoid accepting numeric strings through coercion.

Trial counts up to 100000 and seeds through 2147483647 fit signed Java integers and JavaScript's exact integer range. No nonfinite numbers, binary fields, language-specific objects, or database keys are used. Actual DTO serialization interoperability remains an endpoint implementation check; this ticket validates the JSON contracts and examples.

## Validation workflow

From the repository root:

```sh
python3 -m venv /tmp/gamesense-schema-validation
/tmp/gamesense-schema-validation/bin/pip install -r shared/validation/requirements.txt
/tmp/gamesense-schema-validation/bin/python shared/validation/validate_schemas.py
```

The validator checks every schema against the draft meta-schema, resolves relative references using a local registry without fetching schema IDs, validates every example with format checking enabled, rejects representative invalid inputs, and tests probability boundaries. Each payload schema needs a matching example. Semantic checks above remain documented requirements, not service implementations in this ticket.

Before merging, review the contracts with the team, confirm season meaning and the proposed trial cap, run validation, and submit the changes through a reviewed pull request.

## Deferred additions

- LLM prediction summary response: generated explanation, provenance, and failure behavior need a later contract. No summary placeholder appears in core payloads.
- Model accuracy badges: metric definition, evaluation dataset, period, sample count, and thresholds must precede High/Medium/Low labels.
- Rich venue information and imagery beyond the existing optional venue value object; no additional feed data is assumed.
- Database persistence and durable prediction history, if adopted after MVP. Core schemas remain independent of storage.
- Asynchronous jobs, partial failures, pagination envelopes, historical evaluation cutoffs, custom ratings, tunable algorithm parameters, exposed aggregation weights, and model versions only when their features are agreed.
- Explicit home/away probabilities for every single-method output may simplify UI display; this would require a reviewed change to the shared PredictionResult model.

Team records and rankings visible in the mockup already have domain definitions. Their standalone API schemas should be added when their MVP consumption is confirmed; the image alone does not commit those endpoints or require new performance-metric contracts.

## Field reference

Required fields are marked yes. All other fields may be omitted. New payload fields are nonnullable; exceptions are explicitly noted for the existing Game schema. `$ref` targets identify nested structures or shared types.

### Shared types and nested value objects

| Definition | Type | Validation |
|---|---|---|
| identifier | string | UUID format; current generation convention is v4. |
| utcTimestamp | string | Valid date-time with a trailing Z. |
| probability | number | Inclusive range 0 to 1. |
| nonBlankString | string | At least one non-whitespace character. |
| predictionMethod | string | PredictionMethod enum above. |
| aggregationStrategy | string | AggregationStrategy enum above. |

Existing `Game.venue` is an object with required string `name` and optional string `city` and `state`. It rejects additional properties and cannot be null; the entire venue may be omitted. The baseline does not constrain those strings to nonblank values.

Each `ErrorResponse.details` item is an object with required nonnullable strings `path` and `message`. `path` is empty or starts with `/`, representing a JSON Pointer; `message` must be nonblank. Unknown item properties are rejected. The schema checks the path prefix; callers must also produce correctly escaped JSON Pointer segments (`~0` for `~`, `~1` for `/`).

### AggregatedPredictionOutcome

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `aggregationStrategy` | `common.schema.json#/$defs/aggregationStrategy` | yes | See referenced definition / semantics above. |
| `contributingResultIds` | `array of common.schema.json#/$defs/identifier` | yes | minItems=1; uniqueItems=True |
| `predictedWinnerTeamId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `confidence` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `generatedAt` | `common.schema.json#/$defs/utcTimestamp` | yes | See referenced definition / semantics above. |

Example: [aggregated-prediction-outcome.json](../../shared/examples/aggregated-prediction-outcome.json).

### AggregatedPredictionResult

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `aggregationStrategy` | `common.schema.json#/$defs/aggregationStrategy` | yes | See referenced definition / semantics above. |
| `contributingResultIds` | `array of common.schema.json#/$defs/identifier` | yes | minItems=1; uniqueItems=True |
| `predictedWinnerTeamId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `confidence` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `generatedAt` | `common.schema.json#/$defs/utcTimestamp` | yes | See referenced definition / semantics above. |
| `gameId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |

Example: [aggregated-prediction-result.json](../../shared/examples/aggregated-prediction-result.json).

### Common definitions

Reusable wire types. Identifier generation follows the domain-model ADR; UUID format alone does not enforce version 4.

### CustomPredictionRequest

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `matchup` | `matchup.schema.json` | yes | See referenced definition / semantics above. |
| `methods` | `array of common.schema.json#/$defs/predictionMethod` | yes | minItems=1; uniqueItems=True |
| `simulations` | `array of simulation-configuration-request.schema.json` | no | minItems=1 |

Example: [custom-prediction-request.json](../../shared/examples/custom-prediction-request.json).

### DeterministicPredictionResult

Uses exactly the fields of `single-prediction-result.schema.json`.

### ErrorResponse

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `code` | `string` | yes | enum=['VALIDATION_ERROR', 'NOT_FOUND', 'UNSUPPORTED_METHOD', 'PREDICTION_FAILED', 'INTERNAL_ERROR'] |
| `message` | `common.schema.json#/$defs/nonBlankString` | yes | See referenced definition / semantics above. |
| `requestId` | `common.schema.json#/$defs/nonBlankString` | no | See referenced definition / semantics above. |
| `details` | `array of object` | no | minItems=1 |

Example: [error.json](../../shared/examples/error.json).

### GameStatus

Lifecycle state. BYE is not a game status.

### Game

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `string` | yes | format=uuid |
| `externalId` | `string` | no | See referenced definition / semantics above. |
| `season` | `integer` | yes | See referenced definition / semantics above. |
| `week` | `integer` | no | See referenced definition / semantics above. |
| `homeTeamId` | `string` | yes | format=uuid |
| `awayTeamId` | `string` | yes | format=uuid |
| `venue` | `#/$defs/venue` | no | See referenced definition / semantics above. |
| `isNeutralSite` | `boolean` | no | See referenced definition / semantics above. |
| `scheduledAt` | `string` | yes | format=date-time |
| `status` | `#/$defs/gameStatus` | yes | See referenced definition / semantics above. |
| `homeScore` | `integer or null` | no | minimum=0 |
| `awayScore` | `integer or null` | no | minimum=0 |
| `winnerTeamId` | `string or null` | no | format=uuid |

Example: [game.json](../../shared/examples/game.json).

### Matchup

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `homeTeamId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `awayTeamId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `season` | `integer` | yes | minimum=1 |
| `isNeutralSite` | `boolean` | yes | See referenced definition / semantics above. |

Example: [matchup.json](../../shared/examples/matchup.json).

### PredictionResponse

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `matchup` | `matchup.schema.json` | yes | See referenced definition / semantics above. |
| `gameId` | `common.schema.json#/$defs/identifier` | no | See referenced definition / semantics above. |
| `predictions` | `array of single-prediction-result.schema.json` | yes | minItems=1 |
| `simulations` | `array of simulation-outcome.schema.json` | no | minItems=1 |
| `aggregate` | `aggregated-prediction-outcome.schema.json` | no | See referenced definition / semantics above. |

Example: [prediction-response.json](../../shared/examples/prediction-response.json).

### PredictionResult

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `string` | yes | format=uuid |
| `gameId` | `string` | yes | format=uuid |
| `method` | `#/$defs/predictionMethod` | yes | See referenced definition / semantics above. |
| `predictedWinnerTeamId` | `string` | yes | format=uuid |
| `confidence` | `number` | yes | minimum=0; maximum=1 |
| `predictedHomeScore` | `integer` | no | See referenced definition / semantics above. |
| `predictedAwayScore` | `integer` | no | See referenced definition / semantics above. |
| `generatedAt` | `string` | yes | format=date-time |

Example: [prediction.json](../../shared/examples/prediction.json).

### SimulationConfigurationRequest

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `method` | `common.schema.json#/$defs/predictionMethod` | yes | See referenced definition / semantics above. |
| `trials` | `integer` | yes | minimum=1; maximum=100000 |
| `seed` | `integer` | no | minimum=0; maximum=2147483647 |

Example: [simulation-configuration-request.json](../../shared/examples/simulation-configuration-request.json).

### SimulationOutcome

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `method` | `common.schema.json#/$defs/predictionMethod` | yes | See referenced definition / semantics above. |
| `trials` | `integer` | yes | minimum=1; maximum=100000 |
| `homeWinProbability` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `awayWinProbability` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `simulatedAt` | `common.schema.json#/$defs/utcTimestamp` | yes | See referenced definition / semantics above. |

Example: [simulation-outcome.json](../../shared/examples/simulation-outcome.json).

### SimulationResult

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |
| `method` | `common.schema.json#/$defs/predictionMethod` | yes | See referenced definition / semantics above. |
| `trials` | `integer` | yes | minimum=1; maximum=100000 |
| `homeWinProbability` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `awayWinProbability` | `common.schema.json#/$defs/probability` | yes | See referenced definition / semantics above. |
| `simulatedAt` | `common.schema.json#/$defs/utcTimestamp` | yes | See referenced definition / semantics above. |
| `gameId` | `common.schema.json#/$defs/identifier` | yes | See referenced definition / semantics above. |

Example: [simulation-result.json](../../shared/examples/simulation-result.json).

### SinglePredictionResult

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `prediction.schema.json#/properties/id` | yes | See referenced definition / semantics above. |
| `method` | `prediction.schema.json#/properties/method` | yes | See referenced definition / semantics above. |
| `predictedWinnerTeamId` | `prediction.schema.json#/properties/predictedWinnerTeamId` | yes | See referenced definition / semantics above. |
| `confidence` | `prediction.schema.json#/properties/confidence` | yes | See referenced definition / semantics above. |
| `predictedHomeScore` | `integer` | no | minimum=0 |
| `predictedAwayScore` | `integer` | no | minimum=0 |
| `generatedAt` | `common.schema.json#/$defs/utcTimestamp` | yes | See referenced definition / semantics above. |

Example: [single-prediction-result.json](../../shared/examples/single-prediction-result.json).

### Team

| Field | Type / structure | Required | Constraints |
|---|---|---|---|
| `id` | `string` | yes | format=uuid |
| `externalId` | `string` | no | See referenced definition / semantics above. |
| `name` | `string` | yes | See referenced definition / semantics above. |
| `shortName` | `string` | no | See referenced definition / semantics above. |
| `conferenceId` | `string` | yes | format=uuid |
| `division` | `string` | no | See referenced definition / semantics above. |
| `logoUrl` | `string` | no | format=uri |

Example: [team.json](../../shared/examples/team.json).
