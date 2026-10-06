"""Translate a trained XGBoost classifier into the shared prediction contract."""

from xgboost import XGBClassifier

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


class XGBoostModel:
    """Deterministic inference; trusted startup supplies the trained classifier."""

    def __init__(self, model: XGBClassifier):
        self.model = model

    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="xgboost-v1",
            display_name="XGBoost",
            method=PredictionMethod.MACHINE_LEARNING,
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
                "XGBoost supports deterministic prediction execution only.",
            )

        home_probability = predict_matchup(
            self.model,
            request.matchup.home_team_id,
            request.matchup.away_team_id,
            context,
            request.matchup.is_neutral_site,
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
