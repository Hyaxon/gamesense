"""Single-invocation boundary; model-family logic belongs in adapters."""

import logging

from .context import MissingContextError, PredictionContext
from .contracts import (
    ErrorCode,
    ErrorDetail,
    ErrorResponse,
    ModelExecutionError,
    ModelExecutionRequest,
    ModelExecutionResponse,
    ModelExecutionResult,
    to_json_value,
)
from .execution_helpers import ExecutionFailureError, execution_error
from .execution_validation import (
    ContractValidationError,
    validate_request,
    validate_response,
)
from .registry import ModelRegistry
from .snapshots import SnapshotProvider

logger = logging.getLogger(__name__)


class PredictionRunner:
    def __init__(self, registry: ModelRegistry, snapshots: SnapshotProvider):
        self.registry = registry
        self.snapshots = snapshots

    def execute(
        self, invocation: ModelExecutionRequest | dict
    ) -> ModelExecutionResponse | ErrorResponse:
        """Malformed wire inputs return ErrorResponse before a valid invocation exists.

        After parsing, failures use the correlated ModelExecutionError envelope.
        Adapters may return an expected error or raise ExecutionFailureError; unexpected
        exceptions and malformed responses are logged and mapped to INTERNAL_ERROR.
        """
        try:
            payload = (
                invocation.to_wire()
                if isinstance(invocation, ModelExecutionRequest)
                else invocation
            )
            request = ModelExecutionRequest.from_wire(
                payload, catalog=self.registry.catalog
            )
        except (ContractValidationError, TypeError, AttributeError) as error:
            path = error.path if isinstance(error, ContractValidationError) else ""
            return ErrorResponse(
                code=ErrorCode.VALIDATION_ERROR,
                message="The execution request is invalid.",
                details=(ErrorDetail(path=path, message="Invalid value."),),
            )
        try:
            validate_request(request, self.registry.catalog)
        except ContractValidationError as error:
            # Noncanonical correlation IDs cannot be used in a valid envelope.
            if error.path == "/executionId":
                return ErrorResponse(
                    code=ErrorCode.VALIDATION_ERROR,
                    message="The execution identifier is invalid.",
                )
            return execution_error(
                request,
                ErrorCode.VALIDATION_ERROR,
                "The execution request is invalid.",
                path=error.path,
            )
        entry = self.registry.get(request.model_id)
        if entry is None:
            return execution_error(
                request,
                ErrorCode.NOT_FOUND,
                "The requested model is unavailable.",
                path="/modelId",
            )
        if request.execution_kind not in entry.descriptor.supported_execution_kinds:
            return execution_error(
                request,
                ErrorCode.UNSUPPORTED_METHOD,
                "The requested execution kind is unsupported.",
            )
        if request.seed is not None and not entry.supports_seed:
            return execution_error(
                request,
                ErrorCode.UNSUPPORTED_METHOD,
                "This model does not support an explicit seed.",
            )
        try:
            configuration = to_json_value(request.configuration or {})
            for reserved in ("seed", "trials"):
                if reserved in configuration:
                    raise ContractValidationError(
                        "Control belongs to the common request",
                        f"/configuration/{reserved}",
                    )
            if entry.configuration_validator is None:
                if configuration:
                    raise ContractValidationError(
                        "Configuration is unsupported", "/configuration"
                    )
            else:
                self.registry.catalog.validate_with(
                    entry.configuration_validator,
                    configuration,
                    prefix="/configuration",
                )
        except ContractValidationError as error:
            return execution_error(
                request,
                ErrorCode.VALIDATION_ERROR,
                "The model configuration is invalid.",
                path=error.path,
            )
        except Exception:
            logger.exception(
                "Configuration validation failed for execution %s", request.execution_id
            )
            return execution_error(
                request,
                ErrorCode.INTERNAL_ERROR,
                "Prediction execution encountered an internal error.",
            )
        try:
            context = self.snapshots.get(
                request.data_snapshot_id, request.matchup.season
            )
            if not isinstance(context, PredictionContext) or (
                context.data_snapshot_id != request.data_snapshot_id
                or context.season != request.matchup.season
            ):
                raise RuntimeError("Snapshot provider returned inconsistent context")
            context.require_team(request.matchup.home_team_id)
            context.require_team(request.matchup.away_team_id)
        except MissingContextError:
            return execution_error(
                request,
                ErrorCode.NOT_FOUND,
                "Required snapshot, season, or team data is unavailable.",
            )
        except Exception:
            logger.exception(
                "Snapshot lookup failed for execution %s", request.execution_id
            )
            return execution_error(
                request,
                ErrorCode.INTERNAL_ERROR,
                "Prediction execution encountered an internal error.",
            )
        try:
            if entry.adapter.get_descriptor() != entry.descriptor:
                raise RuntimeError("Adapter descriptor changed after registration")
            try:
                response = entry.adapter.execute(request, context)
            except MissingContextError:
                response = execution_error(
                    request,
                    ErrorCode.NOT_FOUND,
                    "Required model input data is unavailable.",
                )
            except ExecutionFailureError as error:
                response = execution_error(
                    request, error.code, str(error), path=error.path
                )
            if not isinstance(response, (ModelExecutionResult, ModelExecutionError)):
                raise TypeError("Adapters must return a typed execution response")
            validate_response(
                request,
                response,
                entry.descriptor,
                self.registry.catalog,
                entry.configuration_validator,
            )
            return response
        except Exception:
            logger.exception(
                "Model execution failed for execution %s", request.execution_id
            )
            return execution_error(
                request,
                ErrorCode.INTERNAL_ERROR,
                "Prediction execution encountered an internal error.",
            )
