"""Exact coin counts, reproducibility, and integration with the shared runner."""

import importlib.util
import json
from dataclasses import replace
from pathlib import Path
from random import Random

import pytest

from gamesense_prediction.contracts import (
    ErrorCode,
    ExecutionKind,
    ModelExecutionError,
    PredictionMethod,
)
from gamesense_prediction.models.coin_flip import CoinFlipModel
from gamesense_prediction.models.coin_flip.algorithm import simulate_coin_flips
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner

from ..conftest import NEW_TEAM, UNKNOWN
from ..contract_checks import check_adapter_contract, check_seeded_repeatability


class ScriptedCoins:
    """Fixed heads/tails outcomes make count tests independent of chance."""

    def __init__(self, outcomes):
        self.outcomes = iter(outcomes)

    def getrandbits(self, width):
        assert width == 1
        return next(self.outcomes)


@pytest.mark.parametrize(
    "outcomes,home,away",
    [
        ([1], 1, 0),
        ([0], 0, 1),
        ([1, 0, 1, 0], 2, 2),
        ([1, 1, 0], 2, 1),
        ([0, 0, 1], 1, 2),
    ],
)
def test_exact_flip_counts(outcomes, home, away):
    counts = simulate_coin_flips(len(outcomes), ScriptedCoins(outcomes))
    assert (counts.home_wins, counts.away_wins) == (home, away)
    assert counts.trials == len(outcomes)
    assert counts.home_probability == home / len(outcomes)
    assert counts.away_probability == away / len(outcomes)


def test_documented_seeded_example():
    counts = simulate_coin_flips(10, Random(42))
    assert counts.home_wins == 3
    assert counts.away_wins == 7


@pytest.mark.parametrize("trials", [0, -1, True, 1.0, "10"])
def test_invalid_algorithm_trial_count(trials):
    with pytest.raises(ValueError, match="positive integer"):
        simulate_coin_flips(trials, Random(42))


@pytest.fixture
def coin_runner(snapshots):
    registry = ModelRegistry(
        [ModelRegistration(adapter=CoinFlipModel(), supports_seed=True)],
        {PredictionMethod.RANDOM: "coin-flip-v1"},
    )
    return PredictionRunner(registry, snapshots)


@pytest.fixture
def coin_request(execution_request):
    return replace(
        execution_request,
        model_id="coin-flip-v1",
        execution_kind=ExecutionKind.SIMULATION,
        trials=1000,
        seed=42,
    )


def test_seeded_execution_and_contract(coin_runner, coin_request):
    check_seeded_repeatability(coin_runner, coin_request)
    response = check_adapter_contract(coin_runner, coin_request)
    assert response.model.method == PredictionMethod.RANDOM
    assert response.metadata.seed == 42
    assert response.simulation.trials == 1000
    assert "configuration" not in response.to_wire()


@pytest.mark.parametrize("outcomes", [[1], [0], [1, 0], [1, 1, 0], [0, 0, 1]])
def test_prediction_matches_actual_trials(
    coin_runner, coin_request, monkeypatch, outcomes
):
    from gamesense_prediction.models.coin_flip import adapter

    monkeypatch.setattr(adapter, "Random", lambda _seed: ScriptedCoins(outcomes))
    request = replace(coin_request, trials=len(outcomes))
    result = check_adapter_contract(coin_runner, request)
    home_wins = outcomes.count(1)
    away_wins = outcomes.count(0)
    expected_winner = (
        request.matchup.home_team_id
        if home_wins >= away_wins
        else (request.matchup.away_team_id)
    )
    assert result.prediction.predicted_winner_team_id == expected_winner
    assert result.prediction.confidence == max(home_wins, away_wins) / len(outcomes)


def test_generated_seed_can_replay_run(coin_runner, coin_request):
    first = check_adapter_contract(coin_runner, replace(coin_request, seed=None))
    actual_seed = first.metadata.seed
    assert type(actual_seed) is int and 0 <= actual_seed <= 2147483647
    replay = check_adapter_contract(
        coin_runner, replace(coin_request, seed=actual_seed)
    )
    assert (
        replay.simulation.home_win_probability == first.simulation.home_win_probability
    )
    assert replay.prediction.confidence == first.prediction.confidence


def test_global_random_state_and_context_unchanged(coin_runner, coin_request):
    import random

    before = random.getstate()
    context = coin_runner.snapshots.get("development-2025", 2025)
    games = context.completed_games
    check_adapter_contract(coin_runner, coin_request)
    assert random.getstate() == before
    assert context.completed_games == games


def test_history_is_not_required(coin_runner, coin_request):
    request = replace(
        coin_request, matchup=replace(coin_request.matchup, home_team_id=NEW_TEAM)
    )
    check_adapter_contract(coin_runner, request)


def test_missing_team_fails(coin_runner, coin_request):
    request = replace(
        coin_request, matchup=replace(coin_request.matchup, home_team_id=UNKNOWN)
    )
    result = coin_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.NOT_FOUND


@pytest.mark.parametrize(
    "kind", [ExecutionKind.DETERMINISTIC, ExecutionKind.STOCHASTIC]
)
def test_other_kinds_are_unsupported(coin_runner, coin_request, kind):
    request = replace(coin_request, execution_kind=kind, trials=None, seed=None)
    result = coin_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.UNSUPPORTED_METHOD


@pytest.mark.parametrize("trials,exit_code", [(1, 0), (0, 1), (100001, 1)])
def test_local_script_returns_json_and_exit_status(
    monkeypatch, capsys, trials, exit_code
):
    path = Path(__file__).resolve().parents[2] / "examples/run_coin_flip.py"
    spec = importlib.util.spec_from_file_location("coin_flip_example", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    monkeypatch.setattr(
        "sys.argv", [str(path), "--trials", str(trials), "--seed", "42"]
    )
    assert module.main() == exit_code
    payload = json.loads(capsys.readouterr().out)
    if exit_code == 0:
        assert payload["status"] == "SUCCESS"
        assert payload["simulation"]["trials"] == 1
        assert payload["prediction"]["confidence"] == 1
    else:
        assert payload["code"] == "VALIDATION_ERROR"
