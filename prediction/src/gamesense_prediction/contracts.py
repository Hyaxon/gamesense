"""Immutable Python DTOs for the canonical shared JSON contracts.

Construction is convenient for adapters; boundary validation is performed by
``from_wire`` and the runner. Only dataclass optional fields are omitted: nulls
inside model-specific configuration are preserved for its schema to validate.
"""

from collections.abc import Mapping
from dataclasses import dataclass, fields
from enum import StrEnum
from types import MappingProxyType, UnionType
from typing import Any, ClassVar, get_args, get_origin, get_type_hints


class PredictionMethod(StrEnum):
    ELO = "ELO"
    GLICKO2 = "GLICKO2"
    TRUESKILL = "TRUESKILL"
    MONTE_CARLO = "MONTE_CARLO"
    MACHINE_LEARNING = "MACHINE_LEARNING"
    BRADLEY_TERRY = "BRADLEY_TERRY"
    HOME_GROWN = "HOME_GROWN"
    RANDOM = "RANDOM"


class ExecutionKind(StrEnum):
    DETERMINISTIC = "DETERMINISTIC"
    STOCHASTIC = "STOCHASTIC"
    SIMULATION = "SIMULATION"


class ErrorCode(StrEnum):
    VALIDATION_ERROR = "VALIDATION_ERROR"
    NOT_FOUND = "NOT_FOUND"
    UNSUPPORTED_METHOD = "UNSUPPORTED_METHOD"
    PREDICTION_FAILED = "PREDICTION_FAILED"
    INTERNAL_ERROR = "INTERNAL_ERROR"


def freeze(value):
    """Detach and recursively freeze JSON containers, including caller mappings."""
    if isinstance(value, Mapping):
        return MappingProxyType({key: freeze(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)):
        return tuple(freeze(item) for item in value)
    return value


def to_json_value(value):
    if isinstance(value, WireModel):
        return value.to_wire()
    if isinstance(value, StrEnum):
        return value.value
    if isinstance(value, Mapping):
        return {key: to_json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [to_json_value(item) for item in value]
    return value


def camel_case(name):
    first, *rest = name.split("_")
    return first + "".join(part.title() for part in rest)


def _decode(annotation, value):
    origin = get_origin(annotation)
    if origin is UnionType:
        annotation = next(
            item for item in get_args(annotation) if item is not type(None)
        )
        return None if value is None else _decode(annotation, value)
    if origin is tuple:
        return tuple(_decode(get_args(annotation)[0], item) for item in value)
    if isinstance(annotation, type) and issubclass(annotation, WireModel):
        return annotation._from_validated(value)
    if isinstance(annotation, type) and issubclass(annotation, StrEnum):
        return annotation(value)
    return freeze(value)


@dataclass(frozen=True, kw_only=True)
class WireModel:
    schema_name: ClassVar[str]

    def __post_init__(self):
        for field in fields(self):
            object.__setattr__(self, field.name, freeze(getattr(self, field.name)))

    def to_wire(self) -> dict:
        return {
            camel_case(field.name): to_json_value(value)
            for field in fields(self)
            if (value := getattr(self, field.name)) is not None
        }

    @classmethod
    def from_wire(cls, payload, *, catalog=None):
        from .execution_validation import SchemaCatalog

        (catalog or SchemaCatalog.default()).validate(cls.schema_name, payload)
        return cls._from_validated(payload)

    @classmethod
    def _from_validated(cls, payload):
        hints = get_type_hints(cls)
        return cls(
            **{
                field.name: _decode(hints[field.name], payload[camel_case(field.name)])
                for field in fields(cls)
                if camel_case(field.name) in payload
            }
        )


@dataclass(frozen=True, kw_only=True)
class Matchup(WireModel):
    schema_name = "matchup"
    home_team_id: str
    away_team_id: str
    season: int
    is_neutral_site: bool


@dataclass(frozen=True, kw_only=True)
class ModelDescriptor(WireModel):
    schema_name = "model-descriptor"
    model_id: str
    display_name: str
    method: PredictionMethod
    version: str
    supported_execution_kinds: tuple[ExecutionKind, ...]
    configuration_schema_id: str | None = None


@dataclass(frozen=True, kw_only=True)
class ModelExecutionRequest(WireModel):
    schema_name = "model-execution-request"
    execution_id: str
    model_id: str
    matchup: Matchup
    data_snapshot_id: str
    execution_kind: ExecutionKind
    configuration: Mapping[str, Any] | None = None
    seed: int | None = None
    trials: int | None = None


@dataclass(frozen=True, kw_only=True)
class SinglePredictionResult(WireModel):
    schema_name = "single-prediction-result"
    id: str
    method: PredictionMethod
    predicted_winner_team_id: str
    confidence: float
    generated_at: str
    predicted_home_score: int | None = None
    predicted_away_score: int | None = None


@dataclass(frozen=True, kw_only=True)
class SimulationOutcome(WireModel):
    schema_name = "simulation-outcome"
    id: str
    method: PredictionMethod
    trials: int
    home_win_probability: float
    away_win_probability: float
    simulated_at: str


@dataclass(frozen=True, kw_only=True)
class SupportingScore(WireModel):
    team_id: str
    name: str
    value: float
    unit: str | None = None


@dataclass(frozen=True, kw_only=True)
class ExecutionMetadata(WireModel):
    execution_kind: ExecutionKind
    data_snapshot_id: str
    started_at: str
    completed_at: str
    duration_milliseconds: float
    seed: int | None = None


@dataclass(frozen=True, kw_only=True)
class ModelExecutionResult(WireModel):
    schema_name = "model-execution-result"
    execution_id: str
    model: ModelDescriptor
    matchup: Matchup
    prediction: SinglePredictionResult
    metadata: ExecutionMetadata
    simulation: SimulationOutcome | None = None
    supporting_scores: tuple[SupportingScore, ...] | None = None
    configuration: Mapping[str, Any] | None = None
    status: str = "SUCCESS"


@dataclass(frozen=True, kw_only=True)
class ErrorDetail(WireModel):
    path: str
    message: str


@dataclass(frozen=True, kw_only=True)
class ErrorResponse(WireModel):
    schema_name = "error"
    code: ErrorCode
    message: str
    request_id: str | None = None
    details: tuple[ErrorDetail, ...] | None = None


@dataclass(frozen=True, kw_only=True)
class ModelExecutionError(WireModel):
    schema_name = "model-execution-error"
    execution_id: str
    model_id: str
    error: ErrorResponse
    status: str = "ERROR"


type ModelExecutionResponse = ModelExecutionResult | ModelExecutionError


def response_from_wire(payload, *, catalog=None) -> ModelExecutionResponse:
    from .execution_validation import SchemaCatalog

    (catalog or SchemaCatalog.default()).validate("model-execution-response", payload)
    cls = (
        ModelExecutionResult if payload["status"] == "SUCCESS" else ModelExecutionError
    )
    return cls._from_validated(payload)
