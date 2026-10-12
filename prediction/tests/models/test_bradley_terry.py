"""Rating calculations and integration with the shared runner."""

import random
from dataclasses import dataclass, replace
from unittest.mock import patch

import pytest

from gamesense_prediction.contracts import (
    ErrorCode,
    ExecutionKind,
    ModelExecutionError,
    PredictionMethod,
)
from gamesense_prediction.models.bradley_terry import BradleyTerryModel
from gamesense_prediction.models.bradley_terry.algorithm import (
    fit_strengths,
    predict_matchup,
    win_probability,
)
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner

from ..conftest import AWAY, HOME, NEW_TEAM, UNKNOWN
from ..contract_checks import check_adapter_contract, check_deterministic_repeatability


@dataclass
class FakeGame:
    """Minimal stand-in for a completed game; fit_strengths only reads these fields."""

    home_team_id: str
    away_team_id: str
    winner_team_id: str | None


def test_win_probability_is_ratio_of_strengths():
    assert win_probability(3.0, 1.0) == 0.75
    assert win_probability(2.0, 2.0) == 0.5


def test_better_record_gets_higher_strength(snapshots):
    context = snapshots.get("development-2025", 2025)
    strengths = fit_strengths(context.completed_games)
    # HOME has the better record in the fixture.
    assert strengths[HOME] > strengths[AWAY]


def test_better_team_is_favoured(snapshots):
    context = snapshots.get("development-2025", 2025)
    assert predict_matchup(HOME, AWAY, context) > 0.5
    assert predict_matchup(AWAY, HOME, context) < 0.5


def test_missing_history_raises(snapshots):
    from gamesense_prediction.context import MissingContextError

    context = snapshots.get("development-2025", 2025)
    with pytest.raises(MissingContextError):
        predict_matchup(NEW_TEAM, AWAY, context)


def test_tie_counts_as_half_a_win_each():
    strengths = fit_strengths([FakeGame("A", "B", None)])
    assert strengths["A"] == pytest.approx(strengths["B"])


def test_winless_and_unbeaten_teams_stay_finite():
    strengths = fit_strengths([FakeGame("A", "B", "A")])
    assert 0 < strengths["B"] < strengths["A"] < float("inf")


def test_empty_history_and_self_games_give_no_strengths():
    assert fit_strengths([]) == {}
    assert fit_strengths([FakeGame("A", "A", "A")]) == {}


def test_game_order_does_not_change_strengths():
    games = [
        FakeGame("A", "B", "A"),
        FakeGame("B", "C", "B"),
        FakeGame("C", "A", "C"),
        FakeGame("A", "C", "A"),
    ]
    expected = fit_strengths(games)
    random.Random(1).shuffle(games)
    shuffled = fit_strengths(games)
    assert all(shuffled[t] == pytest.approx(expected[t]) for t in expected)


def test_beating_a_stronger_opponent_counts_for_more():
    # X and Y each have one win, but X beat a strong team and Y a winless one.
    games = [
        FakeGame("S", "T1", "S"),
        FakeGame("S", "T2", "S"),
        FakeGame("S", "T3", "S"),
        FakeGame("W", "T1", "T1"),
        FakeGame("W", "T2", "T2"),
        FakeGame("W", "T3", "T3"),
        FakeGame("X", "S", "X"),
        FakeGame("Y", "W", "Y"),
    ]
    strengths = fit_strengths(games)
    assert strengths["X"] > strengths["Y"]


@pytest.fixture
def bradley_terry_runner(snapshots):
    registry = ModelRegistry(
        [ModelRegistration(adapter=BradleyTerryModel(), supports_seed=False)],
        {PredictionMethod.BRADLEY_TERRY: "bradley-terry-v1"},
    )
    return PredictionRunner(registry, snapshots)


@pytest.fixture
def bradley_terry_request(execution_request):
    return replace(
        execution_request,
        model_id="bradley-terry-v1",
        execution_kind=ExecutionKind.DETERMINISTIC,
        trials=None,
        seed=None,
    )


def test_framework_contract(bradley_terry_runner, bradley_terry_request):
    response = check_adapter_contract(bradley_terry_runner, bradley_terry_request)
    check_deterministic_repeatability(bradley_terry_runner, bradley_terry_request)
    assert response.prediction.method == PredictionMethod.BRADLEY_TERRY
    assert response.simulation is None
    assert sum(s.value for s in response.supporting_scores) == pytest.approx(1)
    assert response.prediction.confidence == max(
        s.value for s in response.supporting_scores
    )


@pytest.mark.parametrize(
    "probability,winner,confidence", [(0.5, HOME, 0.5), (0.2, AWAY, 0.8)]
)
def test_winner_policy(
    bradley_terry_runner, bradley_terry_request, probability, winner, confidence
):
    with patch(
        "gamesense_prediction.models.bradley_terry.adapter.predict_matchup",
        return_value=probability,
    ):
        response = check_adapter_contract(bradley_terry_runner, bradley_terry_request)
    assert response.prediction.predicted_winner_team_id == winner
    assert response.prediction.confidence == confidence


@pytest.mark.parametrize("team_id", [NEW_TEAM, UNKNOWN])
def test_required_history(bradley_terry_runner, bradley_terry_request, team_id):
    request = replace(
        bradley_terry_request,
        matchup=replace(bradley_terry_request.matchup, home_team_id=team_id),
    )
    result = bradley_terry_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.NOT_FOUND


@pytest.mark.parametrize(
    "kind,trials",
    [(ExecutionKind.SIMULATION, 10), (ExecutionKind.STOCHASTIC, None)],
)
def test_other_kinds_are_unsupported(
    bradley_terry_runner, bradley_terry_request, kind, trials
):
    request = replace(bradley_terry_request, execution_kind=kind, trials=trials)
    result = bradley_terry_runner.execute(request)
    assert isinstance(result, ModelExecutionError)
    assert result.error.code == ErrorCode.UNSUPPORTED_METHOD


def test_reject_configuration(bradley_terry_runner, bradley_terry_request):
    request = replace(bradley_terry_request, configuration={"prior_games": 1})
    response = bradley_terry_runner.execute(request)
    assert isinstance(response, ModelExecutionError)
    assert response.error.code == ErrorCode.VALIDATION_ERROR
