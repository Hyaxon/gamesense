"""Translate the shared execution contract into a score simulation."""

from random import Random
from secrets import randbelow

from ...context import PredictionContext
from ...contracts import (
    ErrorCode,
    ExecutionKind,
    ModelDescriptor,
    ModelExecutionRequest,
    ModelExecutionResponse,
    PredictionMethod,
    SimulationOutcome,
    SupportingScore,
)
from ...execution_helpers import (
    ExecutionTimer,
    execution_error,
    new_identifier,
    utc_now,
)
from .algorithm import simulate_matchup


class MonteCarloModel:
    """A SIMULATION model that scores teams from their own game history."""

    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="monte-carlo-v1",
            display_name="Monte Carlo",
            method=PredictionMethod.MONTE_CARLO,
            version="1.0.0",
            supported_execution_kinds=(ExecutionKind.SIMULATION,),
        )

    def execute(
        self, request: ModelExecutionRequest, context: PredictionContext
    ) -> ModelExecutionResponse:
        timer = ExecutionTimer()
        if request.execution_kind != ExecutionKind.SIMULATION:
            return execution_error(
                request,
                ErrorCode.UNSUPPORTED_METHOD,
                "Monte Carlo supports simulation execution only.",
            )

        actual_seed = (
            request.seed if request.seed is not None else randbelow(2147483648)
        )
        counts = simulate_matchup(
            request.matchup.home_team_id,
            request.matchup.away_team_id,
            context,
            request.trials,
            Random(actual_seed),
        )
        home_is_winner = counts.home_wins >= counts.away_wins
        winner_team_id = (
            request.matchup.home_team_id
            if home_is_winner
            else request.matchup.away_team_id
        )
        descriptor = self.get_descriptor()
        simulation = SimulationOutcome(
            id=new_identifier(),
            method=descriptor.method,
            trials=counts.trials,
            home_win_probability=counts.home_probability,
            away_win_probability=counts.away_probability,
            simulated_at=utc_now(),
        )
        return timer.success(
            request,
            descriptor,
            winner_team_id=winner_team_id,
            confidence=max(counts.home_probability, counts.away_probability),
            simulation=simulation,
            seed=actual_seed,
            predicted_home_score=round(counts.home_expected_score),
            predicted_away_score=round(counts.away_expected_score),
            supporting_scores=(
                SupportingScore(
                    team_id=request.matchup.home_team_id,
                    name="expectedScore",
                    value=counts.home_expected_score,
                    unit="points",
                ),
                SupportingScore(
                    team_id=request.matchup.away_team_id,
                    name="expectedScore",
                    value=counts.away_expected_score,
                    unit="points",
                ),
            ),
        )
