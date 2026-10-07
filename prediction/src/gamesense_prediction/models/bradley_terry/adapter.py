"""Translate the Bradley-Terry ratings into the shared prediction contract."""

from ...context import PredictionContext
from ...contracts import (
    ErrorCode,
    ExecutionKind,
    ModelDescriptor,
    ModelExecutionRequest,
    ModelExecutionResponse,
    PredictionMethod,
    SupportingScore,
)
from ...execution_helpers import ExecutionTimer, execution_error
from .algorithm import predict_matchup


class BradleyTerryModel:
    """Deterministic inference from strengths fitted on the supplied snapshot."""

    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="bradley-terry-v1",
            display_name="Bradley-Terry",
            method=PredictionMethod.BRADLEY_TERRY,
            version="1.0.0",
            supported_execution_kinds=(ExecutionKind.DETERMINISTIC,),
        )

    def execute(
        self, request: ModelExecutionRequest, context: PredictionContext
    ) -> ModelExecutionResponse:
        timer = ExecutionTimer()
        if request.execution_kind != ExecutionKind.DETERMINISTIC:
            return execution_error(
                request,
                ErrorCode.UNSUPPORTED_METHOD,
                "Bradley-Terry supports deterministic execution only.",
            )

        home_probability = predict_matchup(
            request.matchup.home_team_id, request.matchup.away_team_id, context
        )
        away_probability = 1 - home_probability
        winner_team_id = (
            request.matchup.home_team_id
            if home_probability >= away_probability
            else request.matchup.away_team_id
        )
        return timer.success(
            request,
            self.get_descriptor(),
            winner_team_id=winner_team_id,
            confidence=max(home_probability, away_probability),
            supporting_scores=(
                SupportingScore(
                    team_id=request.matchup.home_team_id,
                    name="winProbability",
                    value=home_probability,
                    unit="probability",
                ),
                SupportingScore(
                    team_id=request.matchup.away_team_id,
                    name="winProbability",
                    value=away_probability,
                    unit="probability",
                ),
            ),
        )
