"""Verify the health endpoint using the configured synthetic-snapshot HTTP fixture.

Checks that the application still reports its expected health response alongside
the model execution endpoint.
"""


def test_health_check(http_client):
    response = http_client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
