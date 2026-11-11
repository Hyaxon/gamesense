"""Rank models by their backtest summaries.

rank_models orders models by accuracy, highest first, then by Brier score, lowest
first. Models equal on every criterion share a rank. A model with too few decided
games, or with no value for the first criterion, goes last with no rank.
"""

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import NamedTuple

from .results import ModelSummary


class Criterion(NamedTuple):
    metric: str  # a key of ModelSummary.metrics
    higher_is_better: bool


DEFAULT_CRITERIA = (
    Criterion("accuracy", higher_is_better=True),
    Criterion("brier_score", higher_is_better=False),
)
DEFAULT_MIN_DECIDED_GAMES = 30


@dataclass(frozen=True, kw_only=True)
class RankedModel:
    """One model's place in the ranking. A rank of None means unranked."""

    rank: int | None
    summary: ModelSummary


def _sort_key(
    summary: ModelSummary, criteria: Sequence[Criterion]
) -> tuple[tuple[bool, float], ...]:
    """Return a key where smaller means better. A missing value sorts last."""
    key = []
    for metric, higher_is_better in criteria:
        value = summary.metrics[metric]
        if value is None:
            key.append((True, 0.0))
        else:
            key.append((False, -value if higher_is_better else value))
    return tuple(key)


def rank_models(
    summaries: Iterable[ModelSummary],
    *,
    criteria: Sequence[Criterion] = DEFAULT_CRITERIA,
    min_decided_games: int = DEFAULT_MIN_DECIDED_GAMES,
) -> list[RankedModel]:
    """List ranked models best first, then unranked models by model ID."""
    eligible = []
    unranked = []
    for summary in sorted(summaries, key=lambda s: s.model_id):
        # Every model gets a key, so a misspelt metric name always raises KeyError.
        key = _sort_key(summary, criteria)
        has_first_value = summary.metrics[criteria[0].metric] is not None
        decided = summary.correct + summary.incorrect
        if decided >= min_decided_games and has_first_value:
            eligible.append((key, summary))
        else:
            unranked.append(RankedModel(rank=None, summary=summary))

    # The sort is stable, so models with equal keys stay in model-ID order.
    eligible.sort(key=lambda pair: pair[0])
    ranked = []
    previous_key = None
    for position, (key, summary) in enumerate(eligible, start=1):
        rank = ranked[-1].rank if key == previous_key else position
        ranked.append(RankedModel(rank=rank, summary=summary))
        previous_key = key
    return ranked + unranked
