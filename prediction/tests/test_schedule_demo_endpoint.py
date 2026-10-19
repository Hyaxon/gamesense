"""Exercise the demo's registered models through its actual HTTP boundary."""

import importlib.util
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from gamesense_prediction.execution_validation import SchemaCatalog


@pytest.fixture
def demo_client(monkeypatch):
    root = Path(__file__).resolve().parents[1]
    monkeypatch.setenv(
        "PREDICTION_SNAPSHOT_PATH", str(root / "tests/fixtures/xgboost-poc-2025.json")
    )
    spec = importlib.util.spec_from_file_location(
        "schedule_demo_app_test", root / "examples/schedule_demo_app.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    with TestClient(module.app) as client:
        yield client


@pytest.mark.parametrize(
    "model_id,method,kind",
    [
        ("elo-v1", "ELO", "DETERMINISTIC"),
        ("glicko2", "GLICKO2", "DETERMINISTIC"),
        ("bradley-terry-v1", "BRADLEY_TERRY", "DETERMINISTIC"),
        ("monte-carlo-v1", "MONTE_CARLO", "SIMULATION"),
        ("coin-flip-v1", "RANDOM", "SIMULATION"),
        ("xgboost-v1", "MACHINE_LEARNING", "DETERMINISTIC"),
    ],
)
def test_demo_model_success(
    demo_client, execution_request, model_id, method, kind, monkeypatch
):
    from gamesense_prediction.models.xgboost import XGBoostModel

    def no_training(*args, **kwargs):
        pytest.fail("Requests must reuse the model trained at startup")

    monkeypatch.setattr(XGBoostModel, "train", no_training)
    payload = execution_request.to_wire()
    payload["modelId"] = model_id
    payload["dataSnapshotId"] = "xgboost-poc-2025"
    payload["executionKind"] = kind
    if kind == "SIMULATION":
        payload.update(trials=10000, seed=42)
    response = demo_client.post("/model-executions", json=payload)
    assert response.status_code == 200
    result = response.json()
    SchemaCatalog.default().validate("model-execution-response", result)
    assert result["model"]["modelId"] == model_id
    assert result["prediction"]["method"] == method
    assert result["metadata"]["dataSnapshotId"] == payload["dataSnapshotId"]
    assert result["matchup"] == payload["matchup"]
    assert 0.5 <= result["prediction"]["confidence"] <= 1
    if model_id != "coin-flip-v1":
        assert len(result["supportingScores"]) == 2
    if kind == "SIMULATION":
        assert result["simulation"]["trials"] == 10000
        assert result["metadata"]["seed"] == 42
        repeated = demo_client.post("/model-executions", json=payload).json()
        assert (
            repeated["simulation"]["homeWinProbability"]
            == result["simulation"]["homeWinProbability"]
        )


def test_demo_rejects_unregistered_model(demo_client, execution_request):
    payload = execution_request.to_wire()
    payload["modelId"] = "unregistered"
    response = demo_client.post("/model-executions", json=payload)
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "NOT_FOUND"
