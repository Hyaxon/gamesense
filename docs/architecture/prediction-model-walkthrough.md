# Step-by-Step: Build Your Prediction Model

Start here if you are implementing Elo, Monte Carlo, or another prediction model.
Follow the steps in order. First run the working coin-flip example, then use the
same structure for your package.

The examples below use **Elo** for the new package. If you are implementing another
model, replace the package name, class name, descriptor, and execution kind with
your own. The detailed [framework reference](prediction-model-development.md)
covers additional controls and validation rules.

## Step 1: Set up Python and run the example

Start in the repository root. Use Python 3.12 or newer. If you already have a
working environment, skip its creation and activate it.

On macOS/Linux:

```sh
cd prediction
python3 --version
python3 -m venv .venv
source .venv/bin/activate
```

Or in Windows PowerShell:

```powershell
cd prediction
python --version
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

Once activated, install the package and run:

```sh
python -m pip install -e ".[dev]"
python examples/run_coin_flip.py --trials 10 --seed 42
python -m pytest tests/models/test_coin_flip.py
```

**Checkpoint:** the script prints JSON with `status: "SUCCESS"`, a `prediction`,
and a `simulation`. With these 10 trials and seed 42, home wins 3 trials and away
wins 7: the prediction selects away with confidence 0.7. The coin-flip tests pass.

Unless a step says otherwise, run every command below from `prediction/` with
this environment activated.

## Step 2: Open the example files in order

| File | What it does |
| --- | --- |
| [algorithm.py](../../prediction/src/gamesense_prediction/models/coin_flip/algorithm.py) | Counts fair coin flips. Contains calculations, not HTTP or response handling. |
| [adapter.py](../../prediction/src/gamesense_prediction/models/coin_flip/adapter.py) | Identifies the model, reads the request/context, calls the algorithm, and returns shared result types. |
| [__init__.py](../../prediction/src/gamesense_prediction/models/coin_flip/__init__.py) | Exports the model class so callers can import it. |
| [run_coin_flip.py](../../prediction/examples/run_coin_flip.py) | Connects the adapter to the registry, fixture data, and runner. |
| [test_coin_flip.py](../../prediction/tests/models/test_coin_flip.py) | Tests calculations and runs the package through the real framework. |

Three names have different jobs:

- `method` is the algorithm family: `ELO`, `MONTE_CARLO`, or `RANDOM`.
- `model_id` identifies your particular implementation: for example, `elo-v1`.
- `execution_kind` describes how it runs: `DETERMINISTIC`, `STOCHASTIC`, or
  `SIMULATION`.

The coin-flip example uses `RANDOM` and `SIMULATION`. It actually runs the requested
number of trials. Run it with `--trials 1` for a single flip. Sample probabilities
can differ from the underlying 50/50 odds. At one trial the reported confidence
is 1.0, describing the sole sampled outcome rather than a real game's certainty.

Reusing a seed repeats the prediction values for this runtime. IDs and timestamps
still change, so compare probabilities/winner rather than the entire JSON output.

## Step 3: Create your package files

In your editor, create these files under the existing `prediction/` directory:

```text
src/gamesense_prediction/models/elo/
├── __init__.py
├── algorithm.py
├── adapter.py
└── README.md

examples/run_elo.py
tests/models/test_elo.py
```

Keep your work inside your package, example script, and tests. You can import the
framework's types and helpers directly; a new algorithm normally needs no changes
to `runner.py`, the shared schemas, or the existing prototype validator.

**Checkpoint:** you know which file holds calculations, which adapts them to the
framework, which runs the model locally, and which tests it.

## Step 4: Implement and test the calculations

For this Elo example, implement a function in `algorithm.py` with this interface:

```python
from gamesense_prediction.context import PredictionContext
from gamesense_prediction.contracts import Matchup


def predict_home_probability(
    matchup: Matchup,
    context: PredictionContext,
) -> float:
    # Implement the Elo approach required by your model ticket here.
    # Return the home team's probability as a number from 0.0 to 1.0.
    raise NotImplementedError(
        "Implement the Elo calculations before running this model."
    )
```

This is a function outline; replace the exception with your calculations before running
the full model. The framework does not choose an Elo formula for you.

For Elo, your ticket owns research, starting ratings, rating updates, home/neutral
policies, and historical processing. Process `context.completed_games` in its
existing chronological order to build private ratings. Do not modify the context.

Use the context instead of opening your own data files inside the algorithm:

```python
home_team = context.require_team(matchup.home_team_id)
away_team = context.require_team(matchup.away_team_id)
history = context.completed_games
home_games = context.games_for(matchup.home_team_id)
```

`games_for()` may return no games. Your model must document whether it supports
that through an initialization policy. If history is required, call
`context.require_history(team_id)`; missing required history becomes `NOT_FOUND`.

Add calculation tests in `tests/models/test_elo.py`. Use known input/expected output
cases from your chosen Elo approach, including initialization, updates, and edge
cases. You can inspect the coin-flip tests for examples of testing calculations
separately from the runner.

**Checkpoint:** the calculation function returns a valid home-team probability,
and its calculation tests pass.

## Step 5: Add the adapter and export the class

For a deterministic Elo implementation, put this adapter in `adapter.py`:

```python
from gamesense_prediction.context import PredictionContext
from gamesense_prediction.contracts import (
    ErrorCode,
    ExecutionKind,
    ModelDescriptor,
    ModelExecutionRequest,
    ModelExecutionResponse,
    PredictionMethod,
)
from gamesense_prediction.execution_helpers import ExecutionFailureError, ExecutionTimer

from .algorithm import predict_home_probability


class EloModel:
    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="elo-v1",
            display_name="Elo",
            method=PredictionMethod.ELO,
            version="1.0.0",
            supported_execution_kinds=(ExecutionKind.DETERMINISTIC,),
        )

    def execute(
        self,
        request: ModelExecutionRequest,
        context: PredictionContext,
    ) -> ModelExecutionResponse:
        timer = ExecutionTimer()
        probability = predict_home_probability(request.matchup, context)
        if not 0 <= probability <= 1:
            raise ExecutionFailureError(
                ErrorCode.PREDICTION_FAILED,
                "The model could not calculate a valid probability.",
            )
        winner = (
            request.matchup.home_team_id
            if probability >= 0.5
            else request.matchup.away_team_id
        )
        return timer.success(
            request,
            self.get_descriptor(),
            winner_team_id=winner,
            confidence=max(probability, 1 - probability),
        )
```

In your package's `__init__.py`, add:

```python
from .adapter import EloModel

__all__ = ["EloModel"]
```

`get_descriptor()` describes your adapter. `execute()` connects your calculations
to the shared result shape. The timer helper supplies IDs, timestamps, and timing.
Confidence belongs to the selected winner: a home probability of 0.3 selects away
with confidence 0.7. A probability of 0.5 selects home.

For Monte Carlo, use `SIMULATION`, accept `request.trials`, and construct a
`SimulationOutcome` from the actual trial results, as the coin-flip adapter does.
Support seeds only if your algorithm honors them. Do not copy Elo's deterministic
descriptor into a simulation model.

**Checkpoint:** this import works:

```sh
python -c "from gamesense_prediction.models.elo import EloModel; print(EloModel().get_descriptor().to_wire())"
```

## Step 6: Wire a local run through the framework

For Elo, put this in `examples/run_elo.py`:

```python
import json
from pathlib import Path

from gamesense_prediction.contracts import (
    ExecutionKind,
    Matchup,
    ModelExecutionRequest,
    PredictionMethod,
)
from gamesense_prediction.execution_helpers import new_identifier
from gamesense_prediction.models.elo import EloModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider

registry = ModelRegistry(
    [ModelRegistration(adapter=EloModel())],
    {PredictionMethod.ELO: "elo-v1"},
)
fixture = Path(__file__).resolve().parents[1] / "tests/fixtures/season-2025.json"
snapshots = FileSnapshotProvider([fixture])
context = snapshots.get("development-2025", 2025)
team_ids = tuple(context.teams)
request = ModelExecutionRequest(
    execution_id=new_identifier(),
    model_id=registry.resolve_method(PredictionMethod.ELO),
    matchup=Matchup(
        home_team_id=team_ids[0],
        away_team_id=team_ids[1],
        season=context.season,
        is_neutral_site=True,
    ),
    data_snapshot_id=context.data_snapshot_id,
    execution_kind=ExecutionKind.DETERMINISTIC,
)
runner = PredictionRunner(registry, snapshots)
print(json.dumps(runner.execute(request).to_wire(), indent=2))
```

Then run:

```sh
python examples/run_elo.py
```

**Checkpoint:** the result has `status: "SUCCESS"`, `method: "ELO"`, and a
prediction with a matchup team as winner. A deterministic result has no simulation.

This script registers the model for a local run. To make an additional model
available through the service, register it in trusted startup code as described
in the [connectivity guide](prediction-service-connectivity.md). The existing
execution endpoint delegates to the shared runner; individual model packages
do not need their own HTTP endpoints.

## Step 7: Add framework integration tests

Add this test alongside your calculation tests in `tests/models/test_elo.py`:

```python
from dataclasses import replace

from gamesense_prediction.contracts import PredictionMethod
from gamesense_prediction.models.elo import EloModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from tests.contract_checks import (
    check_adapter_contract,
    check_deterministic_repeatability,
)


def test_elo_framework_integration(snapshots, execution_request):
    registry = ModelRegistry(
        [ModelRegistration(adapter=EloModel())],
        {PredictionMethod.ELO: "elo-v1"},
    )
    runner = PredictionRunner(registry, snapshots)
    request = replace(execution_request, model_id="elo-v1")
    check_adapter_contract(runner, request)
    check_deterministic_repeatability(runner, request)
```

`snapshots` and `execution_request` are existing pytest fixtures; pytest supplies
them automatically. The contract helper validates the result and JSON round trip.
It does not verify your Elo calculations; keep the calculation tests from Step 4.

For simulations, also test trial counts, known sampled outcomes, ties, and seed
replay if supported. Follow `tests/models/test_coin_flip.py` for a complete example.

Run:

```sh
python -m pytest tests/models/test_elo.py
```

**Checkpoint:** calculation and integration tests both pass.

## Step 8: Document, check, and submit

Write your package's `README.md` with its model ID, method, supported execution
kinds, required inputs, algorithm assumptions, initialization/default behavior,
and run/test commands. Simulation authors also document seed/reproducibility rules.

If your ticket needs configurable settings, follow the configuration section of
the [framework reference](prediction-model-development.md#4-declare-configuration-and-seed-behavior).
Do that before the final checks: register its schema, resolve defaults in your
adapter, and return effective settings. Keep common `seed` and `trials` outside
configuration. Model configuration schemas and READMEs are included in package data.

Run from `prediction/`:

```sh
python -m pytest
python -m ruff check .
python -m ruff format --check .
```

Then return to the repository root and validate the shared contracts:

```sh
cd ..
python shared/validation/validate_schemas.py
```

Submit a pull request with your approach, assumptions, test results, and a sample
successful output. Request code review through the team's normal workflow.

## If you get stuck

| Symptom | First check |
| --- | --- |
| `ModuleNotFoundError: gamesense_prediction` | Activate the environment and install `.[dev]` from `prediction/`. |
| `NOT_FOUND` | Check model ID, snapshot ID, season, team IDs, and required history/ratings. |
| `UNSUPPORTED_METHOD` | Check the descriptor's execution kind and registered seed support. |
| `VALIDATION_ERROR` | Check trials/seed bounds, types, and configuration. Read the error's detail path. |
| `INTERNAL_ERROR` | Read the local runner traceback. Check unfinished calculations and result consistency. |
| Registry fails during startup | Check unique model IDs, method defaults, and any declared configuration schema. |
| Entire seeded JSON differs between runs | Compare prediction/simulation values; IDs and timestamps are expected to change. |
