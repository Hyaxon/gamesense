"""Tests for the prediction service health endpoint.

Verifies that the service responds successfully and returns the expected
health-check response.
"""


def test_health_check(http_client):
    response = http_client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
