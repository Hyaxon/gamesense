"""Opt-in HTTP demo exposing all implemented models; see demo.md."""

import json
import os
from pathlib import Path

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from gamesense_prediction.contracts import (
    ErrorCode,
    ErrorResponse,
    ModelExecutionError,
    PredictionMethod,
)
from gamesense_prediction.models.bradley_terry import BradleyTerryModel
from gamesense_prediction.models.coin_flip import CoinFlipModel
from gamesense_prediction.models.elo import EloModel
from gamesense_prediction.models.glicko import Glicko2Model
from gamesense_prediction.models.monte_carlo import MonteCarloModel
from gamesense_prediction.models.xgboost import XGBoostModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import InMemorySnapshotProvider

app = FastAPI(title="Schedule demo prediction service")
snapshot = json.loads(Path(os.environ["PREDICTION_SNAPSHOT_PATH"]).read_text())
snapshots = InMemorySnapshotProvider([snapshot])
context = snapshots.get(snapshot["dataSnapshotId"], snapshot["season"])
# Train once during demo startup, using only the exported pre-cutoff history.
# Subsequent HTTP requests perform inference with this trained adapter.
xgboost = XGBoostModel.train(context)
runner = PredictionRunner(
    ModelRegistry(
        [
            ModelRegistration(adapter=EloModel()),
            ModelRegistration(adapter=Glicko2Model()),
            ModelRegistration(adapter=BradleyTerryModel()),
            ModelRegistration(adapter=MonteCarloModel(), supports_seed=True),
            ModelRegistration(adapter=CoinFlipModel(), supports_seed=True),
            ModelRegistration(adapter=xgboost),
        ],
        {
            PredictionMethod.ELO: "elo-v1",
            PredictionMethod.GLICKO2: "glicko2",
            PredictionMethod.BRADLEY_TERRY: "bradley-terry-v1",
            PredictionMethod.MONTE_CARLO: "monte-carlo-v1",
            PredictionMethod.RANDOM: "coin-flip-v1",
            PredictionMethod.MACHINE_LEARNING: "xgboost-v1",
        },
    ),
    snapshots,
)
ERROR_HTTP_STATUS = {
    ErrorCode.VALIDATION_ERROR: 400,
    ErrorCode.NOT_FOUND: 404,
    ErrorCode.UNSUPPORTED_METHOD: 422,
    ErrorCode.PREDICTION_FAILED: 500,
    ErrorCode.INTERNAL_ERROR: 500,
}


@app.exception_handler(RequestValidationError)
async def invalid_body(request, exc):
    return JSONResponse(
        status_code=400,
        content=ErrorResponse(
            code=ErrorCode.VALIDATION_ERROR,
            message="The request body must be a valid JSON object.",
        ).to_wire(),
    )


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/model-executions")
def execute(request: dict):
    response = runner.execute(request)
    error = response.error if isinstance(response, ModelExecutionError) else response
    status = ERROR_HTTP_STATUS[error.code] if isinstance(error, ErrorResponse) else 200
    return JSONResponse(status_code=status, content=response.to_wire())
