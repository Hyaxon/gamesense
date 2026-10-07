"""Structural adapter interface, independent of algorithm family."""

from typing import Protocol, runtime_checkable

from ..context import PredictionContext
from ..contracts import ModelDescriptor, ModelExecutionRequest, ModelExecutionResponse


@runtime_checkable
class PredictionModel(Protocol):
    def get_descriptor(self) -> ModelDescriptor: ...

    def execute(
        self, request: ModelExecutionRequest, context: PredictionContext
    ) -> ModelExecutionResponse: ...
