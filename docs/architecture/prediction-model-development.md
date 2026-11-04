# Implementing a Prediction Model Package

For a first implementation, start with the
[step-by-step walkthrough](prediction-model-walkthrough.md). It runs the working
coin-flip package first, then walks through an Elo package's files, adapter,
registration, and tests. This document is the detailed framework reference.

The Python framework implements the common invocation and validation machinery
specified in [Prediction Model Interface](prediction-model-interface.md).
The [shared JSON schemas](../../shared/schemas/) remain the wire contracts.
This guide explains the Python extension points for Elo, Monte Carlo, and other
model packages. Additional algorithms and production ingestion are separate
implementation work.

## Run the framework example

From `prediction/`, install the service and development dependencies, then run:

```sh
python -m pip install -e ".[dev]"
python examples/example_adapter.py
python -m pytest
```

The [example adapter](../../prediction/examples/example_adapter.py) runs through
the real registry and runner for all three execution kinds. Its probabilities are
synthetic demonstration values; it does not implement Elo, random sampling, or
an actual repeated-trial simulation. It rejects explicit seeds because it uses no
randomness. It is not registered in FastAPI startup; the running service registers
the real coin-flip adapter instead. See the
[connectivity guide](prediction-service-connectivity.md).

## Directory structure

```text
prediction/
├── src/gamesense_prediction/
│   ├── main.py                     # FastAPI health and execution endpoints
│   ├── contracts.py                # Immutable Python DTOs and JSON conversion
│   ├── context.py                  # Read-only teams, history, records, ratings
│   ├── snapshots.py                # Trusted startup snapshot providers
│   ├── registry.py                 # Adapter registration and method defaults
│   ├── runner.py                   # Single-invocation execution boundary
│   ├── execution_validation.py     # Offline schemas and semantic invariants
│   ├── execution_helpers.py        # IDs, UTC timestamps, timing, safe failures
│   ├── resources/
│   │   └── snapshot.schema.json    # Internal snapshot format
│   └── models/
│       ├── interface.py            # PredictionModel Protocol
│       └── <your_model>/           # Created by the model's implementation ticket
│           ├── __init__.py
│           ├── adapter.py
│           ├── algorithm.py
│           ├── configuration.schema.json  # Optional
│           └── README.md
├── examples/example_adapter.py
└── tests/
    ├── fixtures/season-2025.json
    ├── contract_checks.py
    ├── test_contracts.py
    ├── test_context.py
    ├── test_registry.py
    ├── test_runner.py
    └── models/test_<your_model>.py
```

Model-owned schema files matching `models/*/*.schema.json` and package READMEs
are included in setuptools package data. The common shared schemas are installed with the wheel under
`share/gamesense/schemas`; development installations read `shared/schemas` from
the checkout. Schema resolution is local and never downloads schema URLs.

## 1. Implement the algorithm and adapter

Keep model calculations in `algorithm.py`. Implement an adapter with these two
methods in `adapter.py`:

```python
from gamesense_prediction.context import PredictionContext
from gamesense_prediction.contracts import (
    ModelDescriptor,
    ModelExecutionRequest,
    ModelExecutionResponse,
)

def get_descriptor(self) -> ModelDescriptor: ...

def execute(
    self,
    request: ModelExecutionRequest,
    context: PredictionContext,
) -> ModelExecutionResponse: ...
```

These signatures belong on your adapter class. The interface uses structural
typing, so inheritance from `PredictionModel` is optional. Import that Protocol
from `gamesense_prediction.models.interface` when annotating adapter consumers.

The descriptor declares a unique `model_id`, display name, existing
`PredictionMethod`, version, and supported `ExecutionKind` values. Multiple
adapters may share a method, but must have different IDs. Change the version when
the algorithm or fixed trained artifact changes.

DTOs use snake_case attributes. `to_wire()` returns fresh camelCase JSON
containers and omits optional DTO fields whose value is `None`. `from_wire()`
validates before decoding; `response_from_wire()` decodes the success/error union.
Boundary validation does not coerce strings to numbers, accepts only integer
values for integer controls, and rejects nonfinite numbers. Configuration nulls
are preserved and must satisfy the registered configuration schema.

## 2. Use the supplied context

The context is an immutable view of one season snapshot. Request team IDs select
canonical teams; custom matchups do not create scheduled games.

| Access | Meaning |
| --- | --- |
| `context.require_team(team_id)` | Canonical team identity; raises `MissingContextError` if absent. |
| `context.completed_games` | All completed games in this snapshot's season. |
| `context.games_for(team_id)` | That team's completed history; an empty tuple is valid. |
| `context.require_history(team_id)` | Same history, but raises when empty. |
| `context.record_for(team_id)` | Wins, losses, and ties derived from completed games. |
| `context.require_rating(team_id, name)` | Required named input rating; raises when absent. |
| `context.ratings` | Available named ratings, keyed by canonical team ID. |
| `context.provenance` | Source and snapshot documentation. |

History is sorted by actual timestamp instant and then canonical game ID for
equal times. Only `FINAL` games enter completed history. Snapshot loading checks
scores, winner consistency, team references, and explicit neutral-site policy.
Scheduled games cannot provide placeholder zeros as actual scores.

The [synthetic fixture](../../prediction/tests/fixtures/season-2025.json) includes
two teams with history, a third with no completed history, a historical tie,
optional sample ratings, and an unplayed game. Historical ties are valid input;
prediction output still follows the MVP's two-outcome policy.

Snapshot provenance must document any ordering-time convention for date-only
sources; it must not imply that a substituted time is a known kickoff.
Season selects the supplied data; there is no automatic historical evaluation
cutoff or protection from future-data leakage in backtesting.

Elo may build private ratings from this history. Initial ratings, update rules,
and no-history policies belong to the Elo implementation. The framework never
invents model-specific ratings. Require the inputs your model needs; missing
required context becomes `NOT_FOUND`. Never mutate the snapshot or train during
`execute()`.

## 3. Construct standard results and failures

Create `ExecutionTimer()` at the beginning of `execute()`. Its `success()` helper
constructs a prediction and success envelope with UUID v4 IDs and common metadata.
Duration uses a monotonic clock; wall-clock timestamps are UTC strings ending in
`Z`. Optional predicted scores and supporting scores can be supplied explicitly.

Every successful execution includes a prediction. Its confidence is the selected
winner's probability and must be at least 0.5. Equal probabilities select the
home-labeled team, including at neutral sites.

For `SIMULATION`, create a `SimulationOutcome` with the actual trial count and
two win probabilities, and pass it to `success(simulation=...)`. Probabilities must
sum to one within 0.000001. Prediction winner/confidence must match those values.
Other execution kinds must omit simulation. Supporting scores are optional,
finite diagnostics with unique `(team_id, name)` pairs; they may be negative.

Use `execution_error()` to return an expected failure, or raise
`ExecutionFailureError(code, safe_message, path)` from the adapter. Paths are JSON
Pointers. Do not include raw exception text, credentials, file paths, or stack
traces in client messages. Unexpected exceptions and invalid adapter responses
are logged internally and become `INTERNAL_ERROR`. Missing required context
becomes `NOT_FOUND`.

Malformed wire requests return the plain `ErrorResponse` before a valid invocation
exists. Once parsed and correlated, failures use `ModelExecutionError`. Successful
partial results are not returned with errors.

## 4. Declare configuration and seed behavior

If your model has settings, create a draft-2020-12 configuration schema with a
stable `$id`, allowed properties, bounds, and documented defaults. Normally reject
additional properties. Declare that ID in `configuration_schema_id` and supply the
schema during registration. References must resolve to the supplied schema or
the trusted local catalog; unresolved external references fail startup.

The runner validates request configuration before calling the model. JSON Schema
defaults do not populate settings. The adapter resolves defaults explicitly and
returns effective configuration. Always return this object for models with
settings, including defaults, and honor explicit settings. Effective configuration
is validated again on success. Models without a schema accept only omitted or
empty configuration.

Keep `trials` and `seed` in their common request fields rather than duplicating
them in configuration. Simulation trials range from 1 to 100000; seeds range from
0 to 2147483647. Deterministic requests cannot include a seed.

Set `supports_seed=True` at registration only when the adapter honors explicit
seeds. The runner otherwise rejects seeded invocations. If your model generates a
seed controlling execution, pass the actual seed to the metadata/result helper.
Document the runtime, snapshot, version, and configuration conditions under which
seeded execution is repeatable. Seeded simulation remains `SIMULATION`.

## 5. Register and invoke the model

In trusted startup code, assemble registrations and an explicit default model ID
for each installed method:

```python
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider

registry = ModelRegistry(
    [ModelRegistration(adapter=my_adapter, supports_seed=False)],
    {my_adapter.get_descriptor().method: my_adapter.get_descriptor().model_id},
)
snapshots = FileSnapshotProvider([trusted_snapshot_path])
runner = PredictionRunner(registry, snapshots)
response = runner.execute(request)
payload = response.to_wire()
```

Supply `configuration_schema=trusted_schema` in the registration if declared.
Read configuration schemas and snapshot paths from trusted startup configuration,
never from a request. Providers load files once, reject duplicate snapshot IDs,
and do not observe subsequent file edits. A new snapshot needs a new ID/provider.
`InMemorySnapshotProvider` accepts the same internal snapshot payloads directly.

`registry.resolve_method(PredictionMethod.ELO)` resolves a caller-facing method to
its configured model ID; absent methods raise `KeyError`. Enum membership does
not imply that a model is installed. The caller/service generates `execution_id`
with `new_identifier()` when constructing its request.

The common runner does not branch on algorithm names. It validates controls,
resolves context, invokes the adapter once, and checks the response against the
request and registered descriptor. Adapters must return the typed success/error
union, not an arbitrary dictionary. A descriptor cannot change after registration.

The [HTTP integration](prediction-service-connectivity.md) now uses this runner
from `POST /model-executions`. Startup explicitly registers coin flip and loads
the configured snapshot. Additional adapters must be registered in trusted startup
code; Java uses the same execution contract regardless of model family.

## 6. Test and submit your package

Add algorithm tests under `prediction/tests/models/`, including known calculation
cases, initialization, historical processing, and missing-data behavior.
Register your adapter against fixtures and call these reusable helpers from
`tests.contract_checks`:

- `check_adapter_contract(runner, request)` checks execution, semantic validity,
  and serialization round trips.
- `check_deterministic_repeatability(runner, request)` compares prediction values,
  excluding generated IDs and timestamps.
- `check_seeded_repeatability(runner, request)` checks prediction/simulation values
  when your adapter documents seeded repeatability for the current runtime.

Cover each declared execution kind, configuration defaults and explicit settings,
seed policies, and expected failures. Simulation tests must verify actual trial
calculations as well as the common output contract. Framework checks cannot prove
that the algorithm itself is correct.

From `prediction/`, run:

```sh
python -m pytest
python -m ruff check .
python -m ruff format --check .
```

From the repository root, using that environment, run:

```sh
prediction/.venv/bin/python shared/validation/validate_schemas.py
```

Document algorithm assumptions, required inputs, diagnostic names/units,
configuration defaults, and initialization/seed policies in the model README.
Submit the package through a reviewed pull request.
