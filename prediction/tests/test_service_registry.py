"""Service registration: every ready model runs through POST /model-executions.

Covers the models registered at startup, their method defaults, and XGBoost's
artifact loading: a valid artifact registers it without training, and a missing
or invalid artifact skips XGBoost alone with a logged reason.
"""

import logging
import re
from pathlib import Path
from unittest.mock import patch

import pytest
from xgboost import XGBClassifier

from gamesense_prediction.contracts import PredictionMethod
from gamesense_prediction.execution_helpers import new_identifier
from gamesense_prediction.execution_validation import SchemaCatalog
from gamesense_prediction.models.xgboost import XGBoostModel
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.service_registry import DEFAULT_MODELS, build_registry
from gamesense_prediction.snapshots import FileSnapshotProvider

from .conftest import AWAY, HOME

FIXTURES = Path(__file__).parent / "fixtures"

# (model ID, execution kind, extra request fields)
ALWAYS_REGISTERED = [
    ("elo-v1", "DETERMINISTIC", {}),
    ("glicko2", "DETERMINISTIC", {}),
    ("bradley-terry-v1", "DETERMINISTIC", {}),
    ("monte-carlo-v1", "SIMULATION", {"trials": 50, "seed": 7}),
    ("coin-flip-v1", "SIMULATION", {"trials": 10, "seed": 42}),
]


def execution_payload(model_id, execution_kind, extra):
    return {
        "executionId": new_identifier(),
        "modelId": model_id,
        "matchup": {
            "homeTeamId": HOME,
            "awayTeamId": AWAY,
            "season": 2025,
            "isNeutralSite": True,
        },
        "dataSnapshotId": "development-2025",
        "executionKind": execution_kind,
        **extra,
    }


def assert_success(response, payload):
    assert response.status_code == 200
    body = response.json()
    SchemaCatalog.default().validate("model-execution-response", body)
    assert body["status"] == "SUCCESS"
    assert body["model"]["modelId"] == payload["modelId"]
    assert body["prediction"]["predictedWinnerTeamId"] in (HOME, AWAY)
    return body


@pytest.fixture
def xgboost_artifact(tmp_path):
    context = FileSnapshotProvider([FIXTURES / "xgboost-poc-2025.json"]).get(
        "xgboost-poc-2025", 2025
    )
    path = tmp_path / "xgboost.json"
    XGBoostModel.train_and_save(context, path)
    return path


@pytest.mark.parametrize("model_id,execution_kind,extra", ALWAYS_REGISTERED)
def test_registered_model_executes_over_http(
    http_client, model_id, execution_kind, extra
):
    payload = execution_payload(model_id, execution_kind, extra)
    assert_success(http_client.post("/model-executions", json=payload), payload)


def test_service_registers_every_ready_model_with_defaults():
    registry = build_registry(None)

    assert set(registry.entries) == {model_id for model_id, *_ in ALWAYS_REGISTERED}
    for method, model_id in DEFAULT_MODELS.items():
        assert registry.resolve_method(method) == model_id


def test_xgboost_registers_from_artifact_and_executes_over_http(
    http_client, monkeypatch, xgboost_artifact
):
    with patch.object(
        XGBClassifier, "fit", side_effect=AssertionError("Startup trained")
    ):
        registry = build_registry(xgboost_artifact)
    assert registry.resolve_method(PredictionMethod.MACHINE_LEARNING) == "xgboost-v1"

    # Imported after http_client so startup sees the fixture snapshot path.
    from gamesense_prediction import main

    monkeypatch.setattr(main, "runner", PredictionRunner(registry, main.snapshots))
    payload = execution_payload("xgboost-v1", "DETERMINISTIC", {})
    assert_success(http_client.post("/model-executions", json=payload), payload)


@pytest.mark.parametrize(
    "artifact,reason",
    [
        (None, "is not set"),
        ("missing.json", "not found"),
        (FIXTURES / "season-2025.json", "could not be loaded"),
    ],
)
def test_unavailable_xgboost_skips_only_xgboost(caplog, tmp_path, artifact, reason):
    if artifact == "missing.json":
        artifact = tmp_path / artifact

    with caplog.at_level(logging.WARNING):
        registry = build_registry(artifact)

    assert registry.get("xgboost-v1") is None
    assert PredictionMethod.MACHINE_LEARNING not in registry.defaults
    assert set(registry.entries) == {model_id for model_id, *_ in ALWAYS_REGISTERED}
    assert any(
        "XGBoost not registered" in r.message and reason in r.message
        for r in caplog.records
    )


def test_backend_model_selections_are_registered():
    """Every model the Java backend selects by default must exist in Python."""
    properties = (
        Path(__file__).parents[2] / "backend/src/main/resources/application.properties"
    )
    selected = {
        line.split("=", 1)[1].strip()
        for line in properties.read_text().splitlines()
        if re.fullmatch(r"prediction\.orchestration\.models\[\d+\]\.model-id=.*", line)
    }

    assert selected
    assert selected <= set(build_registry(None).entries)


def test_unregistered_xgboost_returns_not_found_over_http(http_client):
    payload = execution_payload("xgboost-v1", "DETERMINISTIC", {})
    response = http_client.post("/model-executions", json=payload)

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "NOT_FOUND"
