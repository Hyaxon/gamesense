# Prediction Model Interface

## Scope

This contract defines how the prediction service invokes a model adapter and how every adapter returns a standardized result. It covers deterministic, stochastic single-run, and repeated-trial simulation execution. It builds on the [shared domain models](domain-model.md) and [API JSON contracts](api-schemas.md). The [interface ADR](../decisions/2026-10-01-prediction-model-interface.md) records the design decisions.

Models, training, backtesting, performance comparisons, aggregation, REST endpoints, Java-to-Python integration, and database persistence are outside this contract. The MVP uses in-memory or file-backed data snapshots. The [Python framework and author guide](prediction-model-development.md) implement the common registry, runner, types, context, and validation separately; no prediction algorithm is supplied here.

## Common invocation

Every adapter exposes one synchronous operation:

```python
class PredictionModel(Protocol):
    def get_descriptor(self) -> ModelDescriptor: ...

    def execute(
        self,
        request: ModelExecutionRequest,
        context: PredictionContext,
    ) -> ModelExecutionResponse: ...
```

This code block specifies the interface; its Python Protocol is implemented in `prediction/src/gamesense_prediction/models/interface.py`. `Protocol` denotes structural typing: an adapter conforms by providing these methods. The named request, descriptor, and response types are objects described by the linked JSON schemas below, mapped to language-specific DTOs. Python implementations use snake_case attributes internally and camelCase at serialization boundaries.

`ModelExecutionResponse` is exactly one `ModelExecutionResult` or `ModelExecutionError`. One invocation executes one registered model for one matchup. The generic runner selects adapters by modelId; it does not branch on Elo, ML, or other algorithm names. Execution kind controls common validation and simulation fields. No batch, asynchronous, or partial-success behavior is defined.

`PredictionContext` is a service-owned read-only view of the immutable snapshot identified by `dataSnapshotId`. It contains the selected season's canonical team identities, available records/ratings/history, and the snapshot's provenance. The service supplies it after resolving the snapshot; adapters may derive private features from it. Exact history/feature storage structures remain model implementation choices and are not new wire fields. Context lookup never creates a scheduled Game for a custom matchup.

An adapter must reject insufficient context with NOT_FOUND instead of inventing ratings or silently selecting another season. Snapshot contents cannot change during execution. A descriptor version identifies the algorithm and any fixed trained artifacts; change the version when either changes. Training is not part of execute.

## Schemas and types

| Type | Schema | Role |
|---|---|---|
| ModelDescriptor | [model-descriptor](../../shared/schemas/model-descriptor.schema.json) | Model identity, display name, family, version, and supported execution kinds. |
| ModelExecutionRequest | [model-execution-request](../../shared/schemas/model-execution-request.schema.json) | One validated invocation. |
| ModelExecutionResult | [model-execution-result](../../shared/schemas/model-execution-result.schema.json) | Successful standard output. |
| ModelExecutionError | [model-execution-error](../../shared/schemas/model-execution-error.schema.json) | Failed invocation using the existing error object. |
| ModelExecutionResponse | [model-execution-response](../../shared/schemas/model-execution-response.schema.json) | Discriminated success/error union returned by execute. |

All objects reject extra fields except configuration, whose properties belong to the registered model's schema. Optional fields are omitted, never null. IDs and timestamps follow common definitions; numeric values must be finite JSON numbers.

### Descriptor fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| modelId | nonblank string | yes | Stable registry key for one deployed adapter, e.g. elo-v1. |
| displayName | nonblank string | yes | Display label; never used for lookup. |
| method | PredictionMethod enum | yes | Domain algorithm family, e.g. ELO or MACHINE_LEARNING. |
| version | nonblank string | yes | Exact implementation/artifact version. |
| supportedExecutionKinds | unique nonempty array of enum strings | yes | DETERMINISTIC, STOCHASTIC, and/or SIMULATION. |
| configurationSchemaId | nonblank string | no | Identifier of a trusted local configuration schema. |

Multiple models may share a method but must have different modelId keys. A method enum is not a promise that any model is installed. Only one descriptor version is active for a modelId in a registry instance; registering a duplicate ID fails at startup. Keeping two versions active requires separate modelIds.

### Request fields

| Field | Type | Required | Meaning / constraints |
|---|---|---|---|
| executionId | canonical identifier | yes | Service-generated correlation for this invocation, echoed on success or failure. Not a database ID or caching promise. |
| modelId | nonblank string | yes | Registered adapter key. |
| matchup | Matchup object | yes | Required homeTeamId, awayTeamId, positive integer season, and boolean isNeutralSite. |
| dataSnapshotId | nonblank string | yes | Opaque identifier of immutable input data selected by the service. |
| executionKind | enum string | yes | DETERMINISTIC, STOCHASTIC, or SIMULATION; must be supported by the descriptor. |
| configuration | object | no | Model-specific settings; separately validated before execution. |
| seed | integer | no | 0 through 2147483647; prohibited for DETERMINISTIC. |
| trials | integer | for SIMULATION only | Required for SIMULATION, prohibited otherwise; 1 through 100000. |

Both team identifiers must resolve within the selected season and snapshot, and they must differ. Season selects team data, not a training request or historical evaluation cutoff. The caller-facing custom request has methods rather than modelIds; the service maintains an explicit default modelId per method and resolves it once before invocation. No consumer must guess from display names.

A source may lack venue detail. Neutral-site selection is sufficient for the common matchup contract. Teams labeled home/away remain those same sides throughout execution, including neutral games. A scheduled-game API request can be converted to the same matchup; gameId belongs to the outer API context, not algorithm input.

### Execution kinds

| Kind | Behavior |
|---|---|
| DETERMINISTIC | Same immutable inputs, effective configuration, and implementation/artifact version produce the same prediction values. |
| STOCHASTIC | One prediction may vary between invocations; no repeated-trial statistics are implied. |
| SIMULATION | Multiple trials produce two-outcome win probabilities and a standard prediction derived from them. |

Deterministic repeatability excludes IDs and wall-clock metadata. More computation does not imply simulation. ML can support any execution kind depending on its adapter. Seed behavior must be documented by the adapter; seeded stochastic execution remains labeled STOCHASTIC or SIMULATION, not reclassified as DETERMINISTIC.

If an explicit seed is unsupported, reject it with UNSUPPORTED_METHOD rather than silently ignore it. If a seed is generated and controls the run, return it in metadata. Reproducibility is limited to the same snapshot, configuration, version, and runtime conditions documented by the model; cross-language random-number equivalence is not promised.

## Standard result

| Field | Type | Required | Meaning |
|---|---|---|---|
| status | constant SUCCESS | yes | Success discriminator. |
| executionId | identifier | yes | Matches request. |
| model | ModelDescriptor object | yes | Exact adapter/version used. |
| matchup | Matchup object | yes | Matches request. |
| prediction | SinglePredictionResult object | yes | Standard winner and confidence for every execution kind. |
| simulation | SimulationOutcome object | for SIMULATION only | Repeated-trial counts/probabilities; prohibited for other kinds. |
| supportingScores | nonempty array of score objects | no | Optional ratings or diagnostic scores with a uniform representation. |
| configuration | object | no | Effective settings after model defaults are resolved. Required semantically when nonempty defaults or explicit settings affect execution. |
| metadata | execution metadata object | yes | Snapshot, kind, timing, and optional actual seed. |

Prediction requires id, method, predictedWinnerTeamId, confidence, and generatedAt; optional predictedHomeScore and predictedAwayScore are nonnegative integers. The winning team must be one of the matchup's teams. Confidence is the winner's estimated probability, not a historical accuracy metric. For the MVP's two-outcome model, the selected winner must have confidence at least 0.5. Exact 0.5 ties select the home-labeled side consistently; this is a prediction policy, not a claim the actual game will tie.

Simulation requires id, method, trials, homeWinProbability, awayWinProbability, and simulatedAt. Probabilities sum to 1 within 0.000001. Prediction chooses the more probable team, with confidence equal to its simulation probability within that tolerance. At equal probabilities the home-labeled side is selected. trials must match the request. All nested methods must match model.method.

Supporting score items require teamId, nonblank name, and finite numeric value; unit is an optional nonblank string. Examples include rating/rating_points or rating_deviation/rating_points. teamId must be in the matchup, and each (teamId, name) pair is unique. Scores are displayed or logged generically; shared consumers never need their names to interpret the winner or confidence. Model authors document their names and units. Score scales are not assumed comparable across models; negative diagnostic scores are allowed.

Metadata requires executionKind, dataSnapshotId, startedAt, completedAt, and nonnegative numeric durationMilliseconds. Timestamps are UTC strings ending in Z. completedAt cannot precede startedAt. Duration is measured with a monotonic clock rather than computed from rounded timestamps. Optional seed records the actual random seed; it is prohibited for DETERMINISTIC. Snapshot and kind match the request. Service runner and adapter share a timing/identity helper when implemented so IDs, timestamps, and safe failures are handled consistently.

## Model-specific configuration

Configuration is intentionally an extensible JSON object; it is not permission to accept arbitrary settings. At startup each registry entry pairs an adapter with its descriptor and optional draft-2020-12 configuration schema. configurationSchemaId identifies that locally registered schema. Do not fetch a caller-provided URL or load caller-selected files, Python classes, or artifacts.

A configuration schema defines allowed properties, types, bounds, required fields, and documented defaults, normally with additionalProperties false. Validate it at registration. A model without a schema accepts omitted configuration or an empty object only. Configuration validation occurs before algorithm execution. JSON Schema defaults do not populate values: the adapter resolves documented defaults explicitly and returns effective settings. Never return credentials or internal artifact paths in configuration.

The examples use an illustrative Elo configuration {"homeAdvantage": 0}. It demonstrates the extension point, not a mandated Elo parameter or algorithm implementation. At a neutral site that example requests no home advantage. Actual Elo/Glicko/ML tickets define their own schemas and default policies without changing the common wrapper. Common seed and trials controls cannot be duplicated or overridden in configuration.

## Registration and execution flow

Use an explicit service-owned registry mapping modelId to descriptor, adapter, and configuration validator. Register adapters at startup from code or trusted service configuration; no filesystem plugin scanning or runtime imports from request input. Verify unique IDs, valid descriptors, available configuration schemas, and explicitly configured defaults per method. Adapter initialization can load a fixed trained artifact; execute cannot train or mutate shared snapshot state.

1. Resolve the scheduled or custom matchup, selected season data snapshot, and requested method's configured modelId. Generate executionId.
2. Validate ModelExecutionRequest with format checks enabled. Reject duplicate team IDs or unavailable season data.
3. Look up the adapter. Confirm its supported execution kind, configuration, seed support, and resource limits. Supply the immutable PredictionContext matching dataSnapshotId.
4. Call execute(request, context) once through the common interface. The adapter owns feature extraction, algorithm calculations, and conversion of native outputs to the standardized result.
5. Validate the returned response schema and semantic invariants against the request and registered descriptor. Treat invalid adapter output as INTERNAL_ERROR, not a user validation failure.
6. For success, copy prediction into PredictionResponse.predictions and simulation, when present, into PredictionResponse.simulations. The containing API response provides matchup and optional gameId. For failure, use the nested shared error object. Do not aggregate outputs in this contract.

The existing prediction_validation.py helper checks earlier prototype fields predictedWinner and confidenceScore. It is not the validator for this interface. Future model tickets must use the canonical predictedWinnerTeamId and confidence fields and the contracts here.

## Examples

Complete payloads are checked by the shared validator:

| Execution | Request | Result |
|---|---|---|
| Deterministic Elo-shaped adapter | [deterministic-request](../../shared/examples/model-execution/deterministic-request.json) | [deterministic-result](../../shared/examples/model-execution/deterministic-result.json) |
| Stochastic ML-shaped adapter | [stochastic-request](../../shared/examples/model-execution/stochastic-request.json) | [stochastic-result](../../shared/examples/model-execution/stochastic-result.json) |
| Repeated-trial simulation adapter | [simulation-request](../../shared/examples/model-execution/simulation-request.json) | [simulation-result](../../shared/examples/model-execution/simulation-result.json) |

These are synthetic contract examples, not calculated model outputs. Deterministic and stochastic examples both return prediction without simulation. The simulation example runs 10000 trials and returns home probability 0.71, away probability 0.29, and home winner confidence 0.71. Named supporting ratings are optional and do not change the shared parsing path.

## Failure behavior

ModelExecutionError requires status ERROR, executionId, modelId, and the existing ErrorResponse object. It has no prediction, simulation, or partial result. See the [failure example](../../shared/examples/model-execution-error.json).

| Shared code | Interface meaning |
|---|---|
| VALIDATION_ERROR | Malformed input or invalid model configuration, including unsupported configuration keys. |
| NOT_FOUND | Unknown modelId, unknown snapshot/team/season, or insufficient required input data. |
| UNSUPPORTED_METHOD | Registered model cannot perform the requested kind or honor an explicit seed/control. |
| PREDICTION_FAILED | A valid supported invocation fails during model execution, including numerical failure or exhausted execution budget. |
| INTERNAL_ERROR | Unexpected defect, invalid adapter output, or inconsistent registry metadata. |

Expected execution failures return the error union. The service boundary catches unexpected exceptions and maps them to INTERNAL_ERROR, with diagnostics logged internally. Client messages contain no stack traces, paths, or secrets. Error details use JSON Pointers such as /configuration/homeAdvantage. Structural request validation may fail before a valid executionId exists; in that case the API uses its existing ErrorResponse directly, rather than inventing a model invocation envelope.

## Model coverage and Java consumption

| Planned family | Adapter responsibility | Shared output |
|---|---|---|
| Elo | Resolve ratings and compute probability for the matchup. | Prediction, optional named ratings. |
| Glicko-2 | Resolve ratings/deviation/volatility and compute prediction. | Prediction, optional named diagnostic scores. |
| ML | Load fixed versioned artifact; derive its required features. Declare deterministic or stochastic inference accurately. | Same prediction; simulation statistics only for repeated-trial mode. |
| Simulation | Validate trials/seed and derive two-outcome probabilities. | Same prediction plus required simulation statistics. |

This review establishes representational fit, not proof that unimplemented models work. Adapters normalize native output; Java never parses a different result structure for each method. Java DTOs can map status and executionKind to enums, IDs to UUID/string, timestamps to Instant, probabilities and supporting scores to double, trials and seed to integers, and configuration to a JSON object/map. It branches on SUCCESS/ERROR and the presence of schema-governed simulation, not on algorithm names. Optional fields require nullable/optional DTO members internally while remaining omitted on the wire. The [connectivity guide](prediction-service-connectivity.md) documents the implemented Java records, strict response decoder, HTTP client, and Python endpoint. Transport remains separate from this payload contract.

## Validation and review

Run the [shared validation workflow](../../shared/README.md#validate-schemas-and-examples). It validates all schemas/examples, execution-kind constraints, success/error exclusivity, and representative semantic relationships in the model examples. The Python runner implements common runtime schema and semantic validation; model-specific input sufficiency, configuration default resolution, and algorithm correctness remain adapter responsibilities.

Team review should confirm snapshot selection and season meaning, model identity/version policy, trial budget, seed policies, configuration schemas, and equal-probability winner policy before acceptance. No deployment, model algorithms, or integration are included. LLM explanations, asynchronous execution, tie outcomes, performance metadata, and aggregation remain separate future contracts.
