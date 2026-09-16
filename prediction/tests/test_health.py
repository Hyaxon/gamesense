# Verifies that the prediction service health endpoint is available and returns the expected response.
from fastapi.testclient import TestClient

from gamesense_prediction.main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
