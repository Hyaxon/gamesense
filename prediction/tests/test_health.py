"""Tests for the prediction service health endpoint.

Verifies that the service responds successfully and returns the expected
health-check response.
"""
from fastapi.testclient import TestClient

from gamesense_prediction.main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
