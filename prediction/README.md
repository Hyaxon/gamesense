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

Start the development server:

```bash
uvicorn gamesense_prediction.main:app --reload
```

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

The FastAPI application currently exposes only the health endpoint. Prediction
endpoints, Java transport, and production data ingestion remain separate work.

---

## Notes
