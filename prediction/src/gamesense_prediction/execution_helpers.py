"""Common identity, timing, result construction, and safe failure helpers."""

from dataclasses import dataclass, field
from datetime import UTC, datetime
from time import perf_counter
from uuid import uuid4

from .contracts import (
    ErrorCode,
    ErrorDetail,
    ErrorResponse,
    ExecutionMetadata,
    ModelExecutionError,
    ModelExecutionResult,
    SinglePredictionResult,
)


def new_identifier() -> str:
    return str(uuid4())


def utc_now() -> str:
    return datetime.now(UTC).isoformat(timespec="microseconds").replace("+00:00", "Z")


class ExecutionFailureError(Exception):
    """Expected model failure. Message must be safe for clients, not exception text."""

    def __init__(self, code: ErrorCode, message: str, path: str | None = None):
        super().__init__(message)
        self.code = code
        self.path = path


def execution_error(request, code, message, *, path=None) -> ModelExecutionError:
    details = (
        (ErrorDetail(path=path, message="Invalid or unavailable value."),)
        if (path is not None)
        else None
    )
    return ModelExecutionError(
        execution_id=request.execution_id,
        model_id=request.model_id,
        error=ErrorResponse(code=code, message=message, details=details),
    )


@dataclass(frozen=True)
class ExecutionTimer:
    started_at: str = field(default_factory=utc_now)
    _started: float = field(default_factory=perf_counter, repr=False)

    def metadata(self, request, *, seed=None) -> ExecutionMetadata:
        completed_at = utc_now()
        # Wall clocks can move backwards. Duration always uses a monotonic clock.
        completed_at = max(completed_at, self.started_at)
        return ExecutionMetadata(
            execution_kind=request.execution_kind,
            data_snapshot_id=request.data_snapshot_id,
            started_at=self.started_at,
            completed_at=completed_at,
            duration_milliseconds=max(0.0, (perf_counter() - self._started) * 1000),
            seed=request.seed if seed is None else seed,
        )

    def success(
        self,
        request,
        descriptor,
        *,
        winner_team_id,
        confidence,
        simulation=None,
        configuration=None,
        supporting_scores=None,
        seed=None,
        predicted_home_score=None,
        predicted_away_score=None,
    ) -> ModelExecutionResult:
        metadata = self.metadata(request, seed=seed)
        return ModelExecutionResult(
            execution_id=request.execution_id,
            model=descriptor,
            matchup=request.matchup,
            prediction=SinglePredictionResult(
                id=new_identifier(),
                method=descriptor.method,
                predicted_winner_team_id=winner_team_id,
                confidence=confidence,
                generated_at=metadata.completed_at,
                predicted_home_score=predicted_home_score,
                predicted_away_score=predicted_away_score,
            ),
            simulation=simulation,
            metadata=metadata,
            configuration=configuration,
            supporting_scores=supporting_scores,
        )
