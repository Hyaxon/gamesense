"""Snapshot publication HTTP contract and optional file startup behavior."""

import os
import subprocess
import sys

import pytest

from gamesense_prediction.snapshots import InMemorySnapshotProvider

from .conftest import HOME, UNKNOWN
from .test_execution_endpoint import assert_error


@pytest.fixture
def publish_client(http_client, monkeypatch):
    from gamesense_prediction import main

    provider = InMemorySnapshotProvider()
    monkeypatch.setattr(main, "snapshots", provider)
    monkeypatch.setattr(main.runner, "snapshots", provider)
    return http_client


@pytest.fixture
def publish_payload(snapshot_payload):
    del snapshot_payload["dataSnapshotId"]
    return snapshot_payload


def test_publish_and_execute(publish_client, publish_payload, execution_request):
    response = publish_client.post("/snapshots", json=publish_payload)
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    snapshot_id = response.json()["dataSnapshotId"]
    assert snapshot_id.startswith("season-2025-")
    request = execution_request.to_wire()
    request.update(
        dataSnapshotId=snapshot_id,
        modelId="coin-flip-v1",
        executionKind="SIMULATION",
        trials=10,
        seed=42,
    )
    execution = publish_client.post("/model-executions", json=request)
    assert execution.status_code == 200
    assert execution.json()["metadata"]["dataSnapshotId"] == snapshot_id


def test_repeated_content_gets_new_version(publish_client, publish_payload):
    first = publish_client.post("/snapshots", json=publish_payload)
    second = publish_client.post("/snapshots", json=publish_payload)
    assert first.status_code == second.status_code == 200
    assert first.json()["dataSnapshotId"] != second.json()["dataSnapshotId"]


@pytest.mark.parametrize(
    "mutation",
    [
        lambda p: p.pop("teams"),
        lambda p: p.update(season="2025"),
        lambda p: p.update(unexpected=True),
        lambda p: p.update(dataSnapshotId="caller-id"),
        lambda p: p["games"][0].update(winnerTeamId=HOME),
        lambda p: p["games"][0].update(homeTeamId=UNKNOWN),
    ],
)
def test_invalid_snapshot(publish_client, publish_payload, mutation):
    mutation(publish_payload)
    response = publish_client.post("/snapshots", json=publish_payload)
    assert_error(response, 400, "VALIDATION_ERROR")


@pytest.mark.parametrize("content", ['{"season":', "", "null", "[]", '"text"'])
def test_invalid_http_body(publish_client, content):
    response = publish_client.post(
        "/snapshots", content=content, headers={"Content-Type": "application/json"}
    )
    assert_error(response, 400, "VALIDATION_ERROR")


def test_generated_id_collision(publish_client, publish_payload, monkeypatch):
    from gamesense_prediction import main

    monkeypatch.setattr(main, "uuid4", lambda: "fixed-id")
    first = publish_client.post("/snapshots", json=publish_payload)
    assert first.status_code == 200
    original = main.snapshots.get(first.json()["dataSnapshotId"], 2025)
    second = publish_client.post("/snapshots", json=publish_payload)
    assert_error(second, 400, "VALIDATION_ERROR")
    assert main.snapshots.get(original.data_snapshot_id, 2025) is original


def test_startup_without_snapshot_environment_variable():
    environment = os.environ.copy()
    environment.pop("PREDICTION_SNAPSHOT_PATH", None)
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            "from gamesense_prediction.main import app, snapshots\n"
            "from gamesense_prediction.context import MissingContextError\n"
            "from fastapi.testclient import TestClient\n"
            "with TestClient(app) as client:\n"
            "    assert client.get('/health').status_code == 200\n"
            "try:\n"
            "    snapshots.get('development-2025', 2025)\n"
            "except MissingContextError:\n"
            "    pass\n"
            "else:\n"
            "    raise AssertionError('Expected an empty provider')\n",
        ],
        env=environment,
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, result.stderr
