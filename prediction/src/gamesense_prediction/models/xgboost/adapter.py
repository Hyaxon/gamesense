"""Own XGBoost model setup and translate predictions into the shared contract."""

from pathlib import Path
from typing import Self

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
from .algorithm import predict_matchup, train_model


class XGBoostModel:
    """Explicit training/loading during setup; execute performs inference only."""

    def __init__(self, model: XGBClassifier):
        self.model = model

    @classmethod
    def train(cls, context: PredictionContext) -> Self:
        """Create a trained adapter without writing an artifact."""
        return cls(train_model(context))

    @classmethod
    def train_and_save(
        cls, context: PredictionContext, output_path: str | Path
    ) -> Self:
        """Train offline, save a JSON/UBJ artifact, and return the trained adapter."""
        path = Path(output_path)
        if path.suffix not in (".json", ".ubj"):
            raise ValueError("Model output path must end in .json or .ubj")
        adapter = cls.train(context)
        adapter.model.save_model(path)
        return adapter

    @classmethod
    def from_file(cls, model_path: str | Path) -> Self:
        """Load an artifact once from a trusted setup path, without training."""
        model = XGBClassifier(n_jobs=1)
        model.load_model(model_path)
        return cls(model)

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
