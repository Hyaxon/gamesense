"""Application entry point for the GameSense prediction service.

Creates the FastAPI application and exposes basic service endpoints used
to verify that the prediction service is running correctly.
"""

import os
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .contracts import ErrorCode, ErrorResponse, ModelExecutionError, PredictionMethod
from .models.coin_flip import CoinFlipModel
from .registry import ModelRegistration, ModelRegistry
from .runner import PredictionRunner
from .snapshots import FileSnapshotProvider

ERROR_HTTP_STATUS = {
    ErrorCode.VALIDATION_ERROR: 400,
    ErrorCode.NOT_FOUND: 404,
    ErrorCode.UNSUPPORTED_METHOD: 422,
    ErrorCode.PREDICTION_FAILED: 500,
    ErrorCode.INTERNAL_ERROR: 500,
}

app = FastAPI(title="GameSense Prediction Service")


@app.exception_handler(RequestValidationError)
async def handle_request_validation(
    request: Request,
    exc: RequestValidationError,
):
    error = ErrorResponse(
        code=ErrorCode.VALIDATION_ERROR,
        message="The request body must be a valid JSON object.",
    )
    return JSONResponse(
        status_code=ERROR_HTTP_STATUS[error.code],
        content=error.to_wire(),
    )


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

    if isinstance(response, ModelExecutionError):
        status_code = ERROR_HTTP_STATUS[response.error.code]
    elif isinstance(response, ErrorResponse):
        status_code = ERROR_HTTP_STATUS[response.code]
    else:
        status_code = 200

    return JSONResponse(
        status_code=status_code,
        content=response.to_wire(),
    )
