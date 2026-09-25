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
│       └── main.py
└── tests/
    └── test_health.py
````

### `src/gamesense_prediction/`

Main source code directory, will contain the POC algorithms and other prediction and analysis related services.

### `tests/`

`pytest` tests that ensure features are acting in accordance with their expected behavior. New tests should be written for every feature added to the `src/` code.

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

> Add information here as prediction models such as Elo, additional statistical models, machine-learning models, and simulation-based approaches are implemented.

---

## Architecture

> Add a short explanation here describing how this Python service communicates with the Java backend and how prediction models are organized internally.

---

## Notes
