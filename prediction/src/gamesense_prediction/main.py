"""HTTP entry point for the GameSense prediction service.

Registers every ready prediction model (XGBoost only when its trained artifact is
configured) and optionally loads a configured snapshot at initialization. Exposes health, snapshot publication, and execution endpoints, delegates
predictions to the shared runner, and maps errors to shared JSON and HTTP statuses.
"""

import os
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .contracts import ErrorCode, ErrorResponse, ModelExecutionError
from .execution_validation import require
from .runner import PredictionRunner
from .service_registry import XGBOOST_MODEL_PATH_ENV, build_registry
from .snapshots import FileSnapshotProvider, InMemorySnapshotProvider

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


# Register every ready model at startup; XGBoost joins only if its artifact loads.
registry = build_registry(os.environ.get(XGBOOST_MODEL_PATH_ENV))

# A local file is optional because Java can publish after Python starts.
# An explicitly configured invalid file still fails startup rather than being ignored.
snapshot_path = os.environ.get("PREDICTION_SNAPSHOT_PATH")
snapshots = (
    FileSnapshotProvider([Path(snapshot_path)])
    if snapshot_path
    else InMemorySnapshotProvider()
)

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


@app.post("/snapshots")
def publish_snapshot(request: dict):
    """Accept a dataset and return the ID Java should use for later predictions."""
    try:
        # HTTP callers cannot choose an existing ID and replace its evidence.
        # File loading supplies its own IDs through the provider directly.
        require(
            "dataSnapshotId" not in request,
            "Snapshot IDs are assigned by the prediction service.",
            "/dataSnapshotId",
        )
        # This identifies a publication, not its content: identical data published
        # again gets a new version. The season prefix is only for readability.
        snapshot_id = f"season-{request.get('season')}-{uuid4()}"
        snapshots.publish({**request, "dataSnapshotId": snapshot_id})
    except ValueError as exc:
        # Both contract violations and duplicate IDs use the shared error envelope
        # so Java can distinguish a rejected dataset from a transport failure.
        error = ErrorResponse(code=ErrorCode.VALIDATION_ERROR, message=str(exc))
        return JSONResponse(status_code=400, content=error.to_wire())
    # Acknowledge only after validation and storage have both succeeded.
    return {"dataSnapshotId": snapshot_id}
