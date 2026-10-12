"""Translate the shared execution contract into an Elo prediction"""

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


# adeterministic model that rates teams by replaying their games
class EloModel:
    # model_id is used to find model, display_name just for UI
    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="elo-v1",
            display_name="Elo",
            method=PredictionMethod.ELO,
            version="1.0.0",  # bump this if the algorithm changes
            supported_execution_kinds=(ExecutionKind.DETERMINISTIC,),
        )

    def execute(
        self, request: ModelExecutionRequest, context: PredictionContext
    ) -> ModelExecutionResponse:
        # Starts a timer so the response can record how long this took
        timer = ExecutionTimer()

        # if someone asks for a kind we don't support return a standard error
        if request.execution_kind != ExecutionKind.DETERMINISTIC:
            return execution_error(
                request,
                ErrorCode.UNSUPPORTED_METHOD,
                "Elo supports deterministic execution only.",
            )

        # matchup holds both team IDs and whether it's a neutral site
        matchup = request.matchup

        # each call gives back the games that team played this season
        home_games = context.require_history(matchup.home_team_id)
        away_games = context.require_history(matchup.away_team_id)

        # algorthm.py does the calculations
        result = predict_matchup(
            matchup.home_team_id,
            matchup.away_team_id,
            home_games,
            away_games,
            matchup.is_neutral_site,  # TODO: confirm field name
        )

        # pick the more likely winner, with a tie going to home
        home_is_winner = result.home_win_probability >= 0.5
        winner_team_id = (
            matchup.home_team_id if home_is_winner else matchup.away_team_id
        )

        # build the standard success response
        return timer.success(
            request,
            self.get_descriptor(),
            winner_team_id=winner_team_id,
            # confidence is the winner's probability so it's always >= 0.5
            confidence=max(result.home_win_probability, result.away_probability),
            # each (team, name) pair has to be unique
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
