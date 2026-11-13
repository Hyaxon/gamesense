# GameSense Prediction Service

Python service responsible for prediction, simulation, and data-analysis functionality for the GameSense application.

---

## Project Structure

```text
prediction/
├── pyproject.toml
├── README.md
├── src/
│   └── gamesense_prediction/
│       ├── __init__.py
│       ├── main.py
│       ├── contracts.py
│       ├── context.py
│       ├── snapshots.py
│       ├── registry.py
│       ├── runner.py
│       ├── execution_validation.py
│       ├── execution_helpers.py
│       ├── models/
│       │   └── interface.py
│       ├── resources/
│       │   └── snapshot.schema.json
│       └── prediction_validation.py  # Earlier prototype validator
├── examples/
│   └── example_adapter.py
└── tests/
    ├── fixtures/
    ├── contract_checks.py
    └── test_*.py
```

### `src/gamesense_prediction/`

Main source code directory, will contain the POC algorithms and other prediction and analysis related services.

The shared framework provides immutable contract types, snapshot-backed input
contexts, explicit model registration, execution, and schema/semantic validation.
The prototype `prediction_validation.py` remains for compatibility and does not
validate the canonical execution contract; use the runner for model integration.

### `tests/`

`pytest` tests that ensure features are acting in accordance with their expected behavior. New tests should be written for every feature added to the `src/` code.

The JavaScript prediction validator and its Vitest tests belong to the [frontend package](../frontend/README.md#unit-tests). This directory manages Python code and dependencies through `pyproject.toml`; run JavaScript tests from `frontend/` with `npm test`.

---

## Requirements

* Python 3.12 or newer
* `pip`
* Python virtual environment support

Check your Python version:

```bash
python --version
```

---

## Initial Setup

Run the following commands from the `prediction/` directory.

### 1. Create a Virtual Environment

```bash
python -m venv .venv
```

### 2. Activate the Virtual Environment

#### macOS / Linux

```bash
source .venv/bin/activate
```

#### Windows PowerShell

```powershell
.venv\Scripts\Activate.ps1
```

After activation, your terminal should show something similar to:

```text
(.venv)
```

### 3. Install the Project

Install the project and development dependencies in editable mode:

```bash
python -m pip install -e ".[dev]"
```

Editable mode allows changes under `src/` to be used without reinstalling the package after every edit.

---

## Running the Prediction Service

From `prediction/`, with the virtual environment activated, optionally set the
`PREDICTION_SNAPSHOT_PATH` environment variable and start the development server.
For local testing, use the synthetic snapshot included with the tests:

### macOS / Linux

```bash
export PREDICTION_SNAPSHOT_PATH=tests/fixtures/season-2025.json
uvicorn gamesense_prediction.main:app --reload
```

### Windows PowerShell

```powershell
$env:PREDICTION_SNAPSHOT_PATH = "tests/fixtures/season-2025.json"
uvicorn gamesense_prediction.main:app --reload
```

`PREDICTION_SNAPSHOT_PATH` points to one snapshot JSON file. Relative paths resolve
from the process's working directory; an absolute path can also be used. There is
no default file: an unset or empty variable starts an empty snapshot provider.
An explicitly configured unreadable file or invalid snapshot prevents startup.
The application reads the environment directly; it does not automatically load a
`.env` file.

The file is loaded and validated once when the application initializes. Restart
the service after changing the snapshot file or environment setting. The fixture
contains synthetic season-2025 data with snapshot ID `development-2025`.
Prediction requests reference that ID in `dataSnapshotId`, not the file path.

### Registered models

At startup the service registers every model that is ready for use
(`service_registry.py`). Each method has one model, which is also its default:

| Model ID | Method | Execution kind | Registered |
| --- | --- | --- | --- |
| `elo-v1` | `ELO` | `DETERMINISTIC` | Always |
| `glicko2` | `GLICKO2` | `DETERMINISTIC` | Always |
| `bradley-terry-v1` | `BRADLEY_TERRY` | `DETERMINISTIC` | Always |
| `monte-carlo-v1` | `MONTE_CARLO` | `SIMULATION` (seed supported) | Always |
| `coin-flip-v1` | `RANDOM` | `SIMULATION` (seed supported) | Always |
| `xgboost-v1` | `MACHINE_LEARNING` | `DETERMINISTIC` | Only with a trained artifact |

XGBoost needs a trained artifact, and the service never trains at startup. Train
one offline, then point `PREDICTION_XGBOOST_MODEL_PATH` at it:

```bash
mkdir -p artifacts
python src/scripts/train_xgboost.py --output artifacts/xgboost.json
export PREDICTION_XGBOOST_MODEL_PATH=artifacts/xgboost.json
uvicorn gamesense_prediction.main:app --reload
```

`prediction/artifacts/` is ignored by git. If the variable is unset, or the file is
missing or cannot be loaded, the service logs a warning that starts with
`XGBoost not registered` and starts without XGBoost; requests for `xgboost-v1`
then return `NOT_FOUND`. Every other model is unaffected. The Java backend leaves
XGBoost out of its default model selections for the same reason; add it there
after configuring an artifact.

Not registered: `TRUESKILL` and `HOME_GROWN` exist in the contract but have no
implemented model.

### Publishing snapshots

`POST /snapshots` accepts a JSON snapshot with `season`, `provenance`, `teams`,
`games`, and optional `ratings`, following the snapshot schema except that
`dataSnapshotId` must be omitted. Python assigns a versioned ID and returns HTTP
200 with `{"dataSnapshotId": "season-2026-<uuid>"}`. Use the returned ID in
subsequent model execution requests. Repeated content receives a new ID.

Invalid JSON, schema or semantic validation failures, caller-supplied IDs, and
ID collisions return HTTP 400 with the shared `ErrorResponse` envelope and code
`VALIDATION_ERROR`. Validation rejects the entire snapshot; no games are dropped.
File-loaded snapshots retain their configured IDs and can coexist with published
snapshots. Published snapshots are held in memory and lost when Python restarts.
Run a single worker so publication and execution share the same in-memory store.

By default, the service will run at:

```text
http://127.0.0.1:8000
```

The `--reload` option automatically restarts the development server when Python source files change.

---

## Health Check

The service exposes a basic health endpoint used to verify that the application is running.

```text
GET /health
```

Open:

```text
http://127.0.0.1:8000/health
```

Expected response:

```json
{
  "status": "ok"
}
```

---

## Running Tests

Run all prediction-service tests:

```bash
python -m pytest
```

A successful run should complete without failed tests.

---

## Linting

Check the Python source code with Ruff:

```bash
python -m ruff check .
```

To automatically fix supported linting problems:

```bash
python -m ruff check . --fix
```

---

## Formatting

Format the Python source code:

```bash
python -m ruff format .
```

Check formatting without modifying files:

```bash
python -m ruff format --check .
```

The non-modifying command is intended for validation and CI.

---

## Development Validation

Before submitting changes, run:

```bash
python -m pytest
python -m ruff check .
python -m ruff format --check .
```

All commands should pass before opening or updating a pull request.

---

## Development Workflow

1. Activate the virtual environment.
2. Install or update dependencies if necessary.
3. Make changes under `src/gamesense_prediction/`.
4. Add or update tests under `tests/`.
5. Run the test suite.
6. Run Ruff linting.
7. Run the Ruff formatting check.
8. Verify the prediction service starts successfully.
9. Submit changes through a pull request.

---

## Dependencies

Project dependencies are defined in:

```text
pyproject.toml
```

### Current Dependencies

- **FastAPI** — Web framework used to expose the Python prediction service as an HTTP API.
- **Uvicorn** — ASGI server used to run and serve the FastAPI application.
- **jsonschema and referencing** — offline shared-contract and model-configuration validation.
- **pytest** — Test framework used for automated unit and service tests.
- **httpx2** — HTTP client used by FastAPI testing tools to test API endpoints.
- **Ruff** — Linter and formatter used to enforce consistent Python code quality and style.

### Adding Dependencies

Runtime dependencies should be added to the main dependency section in `pyproject.toml`.

Development-only tools should be added to the development dependency group.

After changing dependencies, reinstall the project:

```bash
python -m pip install -e ".[dev]"
```

---

## API

### `GET /health`

Used to verify that the prediction service is available.

**Response**

```json
{
  "status": "ok"
}
```

---

## Prediction Models

Start with the [step-by-step implementation guide](../docs/architecture/prediction-model-walkthrough.md).
It walks through the working coin-flip model, creating a package, writing an
adapter, running against fixtures, and testing. The
[framework reference](../docs/architecture/prediction-model-development.md) provides
additional detail on context access, configuration, registration, and validation.

The [coin-flip package](src/gamesense_prediction/models/coin_flip/README.md) is a real
fair-coin simulation baseline. Run it from `prediction/`:

```sh
python examples/run_coin_flip.py --trials 1000 --seed 42
python -m pytest tests/models/test_coin_flip.py
```

The [Bradley-Terry package](src/gamesense_prediction/models/bradley_terry/README.md)
rates each team from the win/loss record in the snapshot and predicts a matchup
from the ratings. Run it from `prediction/`:

```sh
python examples/run_bradley_terry.py
python -m pytest tests/models/test_bradley_terry.py
```

The [XGBoost POC](src/gamesense_prediction/models/xgboost/README.md) trains a binary
classifier offline using pregame scoring history, then returns deterministic
winner predictions through the same runner. Run the synthetic demonstration:

```sh
python src/examples/run_xgboost.py
python -m pytest tests/models/test_xgboost.py
```

The [Monte Carlo package](src/gamesense_prediction/models/monte_carlo/README.md)
predicts a matchup by simulating it many times from each team's own scoring
history. Run it from `prediction/`:

```sh
python examples/run_monte_carlo.py --trials 10000 --seed 42
python -m pytest tests/models/test_monte_carlo.py
```

The [Elo package](src/gamesense_prediction/models/elo/README.md) is a deterministic
rating model. It replays completed games in date order to build team ratings, then
converts the rating gap into a win probability. Run it from `prediction/`:

```sh
python examples/run_elo.py
python -m pytest tests/models/test_elo.py
```

Other algorithms are implemented in their own tickets.

Run the fixture-backed framework demonstration from `prediction/`:

```sh
python examples/example_adapter.py
```

It prints schema-valid synthetic results for deterministic, stochastic, and
simulation execution. It is a contract example, not a prediction algorithm.

---

## Architecture

The runner invokes one explicitly registered adapter for one matchup against an
immutable season snapshot. Adapters own calculations and convert their outputs
into the common success/error contract. The registry resolves model IDs and
configured defaults per method; consumers do not parse algorithm-specific outputs.

The FastAPI application exposes `GET /health`, `POST /snapshots`, and
`POST /model-executions`. At initialization it registers every ready model (see
[Registered models](#registered-models)) and optionally loads the snapshot
specified by `PREDICTION_SNAPSHOT_PATH`.
The execution endpoint delegates to the shared runner.
The Java backend publishes live-season snapshots after startup ingestion and stores
the returned ID for prediction orchestration. Publication failures are logged without
stopping the backend. Set `PREDICTION_SNAPSHOT_PUBLISH_ENABLED=false` to disable
publication. For file-based use, configure Java with matching
`PREDICTION_SNAPSHOT_FALLBACK_SEASON` and `PREDICTION_SNAPSHOT_FALLBACK_ID` values
and matching canonical teams and games.

See the [connectivity guide](../docs/architecture/prediction-service-connectivity.md)
for the HTTP status/error policy, Java client usage, and a local end-to-end check.
The endpoint returns 200 for success, 400 for validation errors, 404 for unavailable
models/data, 422 for unsupported execution controls, and 500 for execution/internal
failures. Model errors retain execution/model IDs; malformed request bodies receive
a plain shared error object.

---

## Notes
