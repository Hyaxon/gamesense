"""Application entry point for the GameSense prediction service.

Creates the FastAPI application and exposes basic service endpoints used
to verify that the prediction service is running correctly.
"""

import os
from pathlib import Path
from fastapi import FastAPI

from .runner import PredictionRunner
from .contracts import PredictionMethod
from .models.coin_flip import CoinFlipModel
from .registry import ModelRegistration, ModelRegistry
from .snapshots import FileSnapshotProvider

app = FastAPI(title="GameSense Prediction Service")

# Define the available model registry upon initialization.
registry = ModelRegistry(
    [ModelRegistration(adapter=CoinFlipModel(), supports_seed=True)],
    {PredictionMethod.RANDOM: "coin-flip-v1"},
)

# Use environment settings to determine the prediction snapshot location.
snapshot_path = Path(os.environ["PREDICTION_SNAPSHOT_PATH"])
snapshots = FileSnapshotProvider([snapshot_path])

runner = PredictionRunner(registry, snapshots)


# Health check endpoint used to verify the service is running.
@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/model-executions")
def execute_model(request: dict):
    response = runner.execute(request)
    return response.to_wire()
