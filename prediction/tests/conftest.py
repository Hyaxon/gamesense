"""Shared fixtures for prediction framework and HTTP endpoint tests.

Provides synthetic snapshots, an example adapter, and prepared execution requests.
The HTTP client fixture sets the snapshot environment variable before importing
the application so endpoint tests do not require production data.
"""

import importlib.util
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from gamesense_prediction.contracts import (
    ExecutionKind,
    Matchup,
    ModelExecutionRequest,
    PredictionMethod,
)
from gamesense_prediction.execution_helpers import new_identifier
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import InMemorySnapshotProvider

HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a"
AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
NEW_TEAM = "c3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a"
UNKNOWN = "a3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a"


@pytest.fixture
def http_client(monkeypatch):
    # Startup reads configuration at import time; configure before importing app.
    monkeypatch.setenv(
        "PREDICTION_SNAPSHOT_PATH",
        str(Path(__file__).parent / "fixtures/season-2025.json"),
    )
    from gamesense_prediction.main import app

    with TestClient(app) as client:
        yield client


@pytest.fixture
def snapshot_payload():
    return json.loads((Path(__file__).parent / "fixtures/season-2025.json").read_text())


@pytest.fixture
def snapshots(snapshot_payload):
    return InMemorySnapshotProvider([snapshot_payload])


@pytest.fixture
def example_module():
    path = Path(__file__).parents[1] / "examples/example_adapter.py"
    spec = importlib.util.spec_from_file_location("example_adapter", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.fixture
def adapter(example_module):
    return example_module.ExampleAdapter()


@pytest.fixture
def registry(adapter, example_module):
    return ModelRegistry(
        [
            ModelRegistration(
                adapter=adapter,
                configuration_schema=example_module.CONFIGURATION_SCHEMA,
            )
        ],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )


@pytest.fixture
def runner(registry, snapshots):
    return PredictionRunner(registry, snapshots)


@pytest.fixture
def execution_request():
    return ModelExecutionRequest(
        execution_id=new_identifier(),
        model_id="example-v1",
        matchup=Matchup(
            home_team_id=HOME, away_team_id=AWAY, season=2025, is_neutral_site=True
        ),
        data_snapshot_id="development-2025",
        execution_kind=ExecutionKind.DETERMINISTIC,
    )
