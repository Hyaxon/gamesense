"""Translate the shared execution contract into a fair-coin simulation."""

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
)
from ...execution_helpers import (
    ExecutionTimer,
    execution_error,
    new_identifier,
    utc_now,
)
from .algorithm import simulate_coin_flips


class CoinFlipModel:
    """A RANDOM-family baseline supporting seeded repeated-trial execution."""

    def get_descriptor(self) -> ModelDescriptor:
        return ModelDescriptor(
            model_id="coin-flip-v1",
            display_name="Fair coin flip",
            method=PredictionMethod.RANDOM,
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
                "The coin-flip model supports simulation execution only.",
            )

        # Resolve identities from the supplied snapshot; never invent a team.
        context.require_team(request.matchup.home_team_id)
        context.require_team(request.matchup.away_team_id)

        # A fresh generator isolates each invocation from all other random state.
        actual_seed = (
            request.seed if request.seed is not None else randbelow(2147483648)
        )
        counts = simulate_coin_flips(request.trials, Random(actual_seed))
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
        )
