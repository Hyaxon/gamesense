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


class Glicko2Model:
    """Adapter for the Glicko-2 prediction model."""

    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="glicko2",
            display_name="Glicko-2",
            method=PredictionMethod.GLICKO,
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
                "Glicko-2 supports deterministic execution only.",
            )

        matchup = request.matchup

        home_games = context.require_history(matchup.home_team_id)
        away_games = context.require_history(matchup.away_team_id)

        result = predict_matchup(
            matchup.home_team_id,
            matchup.away_team_id,
            home_games,
            away_games,
            matchup.is_neutral_site,
        )

        home_is_winner = result.home_win_probability >= 0.5
        winner_team_id = (
            matchup.home_team_id if home_is_winner else matchup.away_team_id
        )

        return timer.success(
            request,
            self.get_descriptor(),
            winner_team_id=winner_team_id,
            confidence=max(result.home_win_probability, result.away_probability),
            supporting_scores=(
                SupportingScore(
                    team_id=matchup.home_team_id,
                    name="rating",
                    value=result.home_elo,
                    unit="rating_points",
                ),
                SupportingScore(
                    team_id=matchup.away_team_id,
                    name="rating",
                    value=result.away_elo,
                    unit="rating_points",
                ),
            ),
        )