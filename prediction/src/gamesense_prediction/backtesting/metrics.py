"""Provisional evaluation metrics for backtesting.

These stand in until the team defines the final metric set (ticket 44, issue
#48). Each metric is a plain function from records to a number, or None when it
cannot be computed, so a final definition can replace or extend this mapping
without touching the backtester. Provisional policy: tied games are left out of
accuracy, Brier score and log loss.
"""

import math
from collections.abc import Sequence
from types import MappingProxyType

from .results import Metric, Outcome, PredictionRecord

PROBABILITY_FLOOR = 1e-15  # keeps log loss finite when a model is certain and wrong


def _decided(records: Sequence[PredictionRecord]) -> list[PredictionRecord]:
    """Records with a prediction and a real winner."""
    return [r for r in records if r.outcome in (Outcome.CORRECT, Outcome.INCORRECT)]


def _home_won(record: PredictionRecord) -> float:
    return 1.0 if record.actual_winner_team_id == record.home_team_id else 0.0


def accuracy(records: Sequence[PredictionRecord]) -> float | None:
    """Share of decided games where the predicted winner won."""
    decided = _decided(records)
    if not decided:
        return None
    return sum(r.outcome == Outcome.CORRECT for r in decided) / len(decided)


def brier_score(records: Sequence[PredictionRecord]) -> float | None:
    """Mean squared error of the home-win probability. Lower is better."""
    scored = [r for r in _decided(records) if r.home_win_probability is not None]
    if not scored:
        return None
    return sum((r.home_win_probability - _home_won(r)) ** 2 for r in scored) / len(
        scored
    )


def log_loss(records: Sequence[PredictionRecord]) -> float | None:
    """Mean negative log likelihood of the real result. Lower is better."""
    scored = [r for r in _decided(records) if r.home_win_probability is not None]
    if not scored:
        return None
    total = 0.0
    for r in scored:
        p = min(max(r.home_win_probability, PROBABILITY_FLOOR), 1 - PROBABILITY_FLOOR)
        total -= math.log(p if _home_won(r) else 1 - p)
    return total / len(scored)


def coverage(records: Sequence[PredictionRecord]) -> float | None:
    """Share of games the model produced a prediction for."""
    if not records:
        return None
    return sum(r.outcome != Outcome.NO_PREDICTION for r in records) / len(records)


DEFAULT_METRICS: "MappingProxyType[str, Metric]" = MappingProxyType(
    {
        "accuracy": accuracy,
        "brier_score": brier_score,
        "log_loss": log_loss,
        "coverage": coverage,
    }
)
