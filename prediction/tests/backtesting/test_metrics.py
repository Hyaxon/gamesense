"""Metric values are checked against numbers worked out by hand."""

import math

import pytest

from gamesense_prediction.backtesting import DEFAULT_METRICS, Outcome, PredictionRecord
from gamesense_prediction.backtesting.metrics import (
    accuracy,
    brier_score,
    coverage,
    log_loss,
)

HOME, AWAY = "home", "away"


def record(*, winner, predicted, probability=None, error=None):
    return PredictionRecord(
        model_id="m",
        game_id="g",
        season=2025,
        scheduled_at="2025-09-01T00:00:00Z",
        home_team_id=HOME,
        away_team_id=AWAY,
        actual_winner_team_id=winner,
        predicted_winner_team_id=predicted,
        home_win_probability=probability,
        error_code=error,
    )


def test_outcome_classification():
    assert record(winner=HOME, predicted=HOME).outcome == Outcome.CORRECT
    assert record(winner=HOME, predicted=AWAY).outcome == Outcome.INCORRECT
    assert record(winner=None, predicted=HOME).outcome == Outcome.TIE
    assert record(winner=HOME, predicted=None).outcome == Outcome.NO_PREDICTION
    assert record(winner=None, predicted=None).outcome == Outcome.NO_PREDICTION


@pytest.fixture
def records():
    return [
        record(winner=HOME, predicted=HOME, probability=0.8),  # correct
        record(winner=AWAY, predicted=HOME, probability=0.6),  # incorrect
        record(winner=AWAY, predicted=AWAY, probability=0.25),  # correct
        record(winner=None, predicted=HOME, probability=0.9),  # tie: left out
        record(winner=HOME, predicted=None, error="NOT_FOUND"),  # no prediction
    ]


def test_accuracy_ignores_ties_and_missing_predictions(records):
    assert accuracy(records) == pytest.approx(2 / 3)


def test_brier_score(records):
    # (0.8-1)^2 = 0.04, (0.6-0)^2 = 0.36, (0.25-0)^2 = 0.0625
    assert brier_score(records) == pytest.approx((0.04 + 0.36 + 0.0625) / 3)


def test_log_loss(records):
    expected = -(math.log(0.8) + math.log(1 - 0.6) + math.log(1 - 0.25)) / 3
    assert log_loss(records) == pytest.approx(expected)


def test_coverage_counts_ties_as_predicted(records):
    assert coverage(records) == pytest.approx(4 / 5)


def test_a_certain_wrong_prediction_gives_a_finite_log_loss():
    wrong = [record(winner=AWAY, predicted=HOME, probability=1.0)]

    assert math.isfinite(log_loss(wrong))
    assert brier_score(wrong) == 1.0


def test_metrics_are_none_when_there_is_nothing_to_score():
    nothing = [record(winner=None, predicted=HOME, probability=0.7)]

    for metric in (accuracy, brier_score, log_loss):
        assert metric(nothing) is None
        assert metric([]) is None
    assert coverage([]) is None


def test_default_metrics_cover_the_documented_set():
    assert set(DEFAULT_METRICS) == {"accuracy", "brier_score", "log_loss", "coverage"}
