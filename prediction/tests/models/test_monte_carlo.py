"""Scoring calculations, reproducibility, and integration with the shared runner."""

from dataclasses import replace
from random import Random

import pytest

from gamesense_prediction.context import MissingContextError
from gamesense_prediction.contracts import (
    ErrorCode,
    ExecutionKind,
    ModelExecutionError,
    PredictionMethod,
)
from gamesense_prediction.models.monte_carlo import MonteCarloModel
from gamesense_prediction.models.monte_carlo.algorithm import (
    average_points_allowed,
    average_points_scored,
    blended_expected_score,
    simulate_matchup,
)
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner

from ..conftest import AWAY, HOME, NEW_TEAM, UNKNOWN
from ..contract_checks import check_adapter_contract, check_seeded_repeatability


def test_blended_expected_score_averages_offense_and_opponent_defense():
    assert blended_expected_score(30, 20) == 25


@pytest.mark.parametrize("trials", [0, -1, True, 1.0, "10"])
def test_invalid_algorithm_trial_count(trials, snapshots):
    context = snapshots.get("development-2025", 2025)
    with pytest.raises(ValueError, match="positive integer"):
        simulate_matchup(HOME, AWAY, context, trials, Random(42))


def test_averages_use_the_right_side_of_each_game(snapshots):
    context = snapshots.get("development-2025", 2025)
    games = context.games_for(HOME)
    # HOME scored 28 at home and 14 away; allowed 21 at home and 14 away.
    assert average_points_scored(HOME, games) == (28 + 14) / 2
    assert average_points_allowed(HOME, games) == (21 + 14) / 2


def test_better_team_wins_more_often(snapshots):
    context = snapshots.get("development-2025", 2025)
    counts = simulate_matchup(HOME, AWAY, context, 20000, Random(42))
    # HOME has the better scoring history in the fixture.
    assert counts.home_probability > 0.5


def test_missing_history_raises(snapshots):
    context = snapshots.get("development-2025", 2025)
    with pytest.raises(MissingContextError):
        simulate_matchup(NEW_TEAM, AWAY, context, 100, Random(42))


@pytest.fixture
def monte_carlo_runner(snapshots):
    registry = ModelRegistry(
        [ModelRegistration(adapter=MonteCarloModel(), supports_seed=True)],
        {PredictionMethod.MONTE_CARLO: "monte-carlo-v1"},
    )
    return PredictionRunner(registry, snapshots)


@pytest.fixture
def monte_carlo_request(execution_request):
    return replace(
        execution_request,
        model_id="monte-carlo-v1",
        execution_kind=ExecutionKind.SIMULATION,
        trials=5000,
        seed=42,
    )


def test_seeded_execution_and_contract(monte_carlo_runner, monte_carlo_request):
    check_seeded_repeatability(monte_carlo_runner, monte_carlo_request)
    response = check_adapter_contract(monte_carlo_runner, monte_carlo_request)
    assert response.model.method == PredictionMethod.MONTE_CARLO
    assert response.metadata.seed == 42
    assert response.simulation.trials == 5000
    assert len(response.supporting_scores) == 2


def test_win_probabilities_sum_to_one(monte_carlo_runner, monte_carlo_request):
    response = check_adapter_contract(monte_carlo_runner, monte_carlo_request)
    total = (
        response.simulation.home_win_probability
        + response.simulation.away_win_probability
    )
    assert total == pytest.approx(1.0)


def test_generated_seed_can_replay_run(monte_carlo_runner, monte_carlo_request):
    first = check_adapter_contract(
        monte_carlo_runner, replace(monte_carlo_request, seed=None)
    )
    actual_seed = first.metadata.seed
    assert type(actual_seed) is int and 0 <= actual_seed <= 2147483647
    replay = check_adapter_contract(
        monte_carlo_runner, replace(monte_carlo_request, seed=actual_seed)
    )
    assert (
        replay.simulation.home_win_probability == first.simulation.home_win_probability
    )


def test_missing_team_fails(monte_carlo_runner, monte_carlo_request):
    request = replace(
        monte_carlo_request,
        matchup=replace(monte_carlo_request.matchup, home_team_id=UNKNOWN),
    )
    result = monte_carlo_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.NOT_FOUND


def test_team_with_no_history_fails(monte_carlo_runner, monte_carlo_request):
    request = replace(
        monte_carlo_request,
        matchup=replace(monte_carlo_request.matchup, home_team_id=NEW_TEAM),
    )
    result = monte_carlo_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.NOT_FOUND


@pytest.mark.parametrize(
    "kind", [ExecutionKind.DETERMINISTIC, ExecutionKind.STOCHASTIC]
)
def test_other_kinds_are_unsupported(monte_carlo_runner, monte_carlo_request, kind):
    request = replace(monte_carlo_request, execution_kind=kind, trials=None, seed=None)
    result = monte_carlo_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.UNSUPPORTED_METHOD
