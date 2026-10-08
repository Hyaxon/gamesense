"""HTTP contract tests using the real runner and a synthetic snapshot.

Covers seeded coin-flip success, invalid bodies and configuration, unavailable
models/data, unsupported execution modes, and safe execution failure responses.
"""

import pytest

from gamesense_prediction.contracts import ErrorCode
from gamesense_prediction.execution_helpers import ExecutionFailureError
from gamesense_prediction.execution_validation import SchemaCatalog


@pytest.fixture
def payload(execution_request):
    request = execution_request.to_wire()
    request.update(
        modelId="coin-flip-v1", executionKind="SIMULATION", trials=10, seed=42
    )
    return request


def assert_error(response, status, code, payload=None):
    assert response.status_code == status
    assert response.headers["content-type"] == "application/json"
    body = response.json()
    catalog = SchemaCatalog.default()
    if payload is None:
        catalog.validate("error", body)
        assert body["code"] == code
    else:
        catalog.validate("model-execution-response", body)
        assert body["status"] == "ERROR"
        assert body["executionId"] == payload["executionId"]
        assert body["modelId"] == payload["modelId"]
        assert body["error"]["code"] == code
        assert "prediction" not in body
    return body


def test_seeded_coin_flip_success(http_client, payload):
    response = http_client.post("/model-executions", json=payload)

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    body = response.json()
    SchemaCatalog.default().validate("model-execution-response", body)
    assert body["status"] == "SUCCESS"
    assert body["executionId"] == payload["executionId"]
    assert body["model"]["modelId"] == payload["modelId"]
    assert body["matchup"] == payload["matchup"]
    assert body["metadata"]["dataSnapshotId"] == payload["dataSnapshotId"]
    assert body["metadata"]["executionKind"] == "SIMULATION"
    assert body["metadata"]["seed"] == 42
    assert body["simulation"]["trials"] == 10
    assert body["simulation"]["homeWinProbability"] == pytest.approx(0.3)
    assert body["simulation"]["awayWinProbability"] == pytest.approx(0.7)
    assert body["prediction"]["confidence"] == pytest.approx(0.7)
    assert (
        body["prediction"]["predictedWinnerTeamId"]
        == (payload["matchup"]["awayTeamId"])
    )


@pytest.mark.parametrize("content", ['{"modelId":', "", "null", "[]", '"text"'])
def test_invalid_http_body(http_client, content):
    response = http_client.post(
        "/model-executions",
        content=content,
        headers={"Content-Type": "application/json"},
    )

    body = assert_error(response, 400, "VALIDATION_ERROR")
    assert "detail" not in body


def test_missing_contract_fields(http_client):
    response = http_client.post("/model-executions", json={})
    assert_error(response, 400, "VALIDATION_ERROR")


@pytest.mark.parametrize("field", ["modelId", "dataSnapshotId"])
def test_unavailable_model_or_snapshot(http_client, payload, field):
    payload[field] = "unavailable"
    response = http_client.post("/model-executions", json=payload)
    assert_error(response, 404, "NOT_FOUND", payload)


def test_unsupported_execution_kind(http_client, payload):
    payload["executionKind"] = "DETERMINISTIC"
    del payload["trials"]
    del payload["seed"]
    response = http_client.post("/model-executions", json=payload)
    assert_error(response, 422, "UNSUPPORTED_METHOD", payload)


def test_invalid_model_configuration(http_client, payload):
    payload["configuration"] = {"unexpected": True}
    response = http_client.post("/model-executions", json=payload)
    assert_error(response, 400, "VALIDATION_ERROR", payload)


@pytest.mark.parametrize("expected", [True, False])
def test_execution_failure(http_client, payload, monkeypatch, expected):
    from gamesense_prediction.main import runner

    def fail(request, context):
        if expected:
            raise ExecutionFailureError(ErrorCode.PREDICTION_FAILED, "Model failed.")
        raise RuntimeError("private internal diagnostic")

    monkeypatch.setattr(runner.registry.get("coin-flip-v1").adapter, "execute", fail)
    response = http_client.post("/model-executions", json=payload)
    code = "PREDICTION_FAILED" if expected else "INTERNAL_ERROR"
    assert_error(response, 500, code, payload)
    assert "private internal diagnostic" not in response.text
