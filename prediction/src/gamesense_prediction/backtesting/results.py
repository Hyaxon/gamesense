"""Backtest records and per-model summaries, with JSON-ready output.

Outcome classifies one prediction, PredictionRecord holds one model's result for
one game, ModelSummary rolls a model's records up, and BacktestResult holds all.
"""

from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from enum import StrEnum
from types import MappingProxyType

from ..contracts import camel_case


class Outcome(StrEnum):
    CORRECT = "CORRECT"
    INCORRECT = "INCORRECT"
    TIE = "TIE"  # a prediction was made, but the game ended tied
    NO_PREDICTION = "NO_PREDICTION"  # the model returned an error for this game


@dataclass(frozen=True, kw_only=True)
class PredictionRecord:
    """One model's prediction for one completed game, next to the real result."""

    model_id: str
    game_id: str
    season: int
    scheduled_at: str
    home_team_id: str
    away_team_id: str
    actual_winner_team_id: str | None  # None means the game ended tied
    predicted_winner_team_id: str | None = None
    home_win_probability: float | None = None
    error_code: str | None = None

    @property
    def outcome(self) -> Outcome:
        if self.predicted_winner_team_id is None:
            return Outcome.NO_PREDICTION
        if self.actual_winner_team_id is None:
            return Outcome.TIE
        if self.predicted_winner_team_id == self.actual_winner_team_id:
            return Outcome.CORRECT
        return Outcome.INCORRECT

    def to_wire(self) -> dict:
        wire = {
            camel_case(name): value
            for name, value in vars(self).items()
            if value is not None
        }
        wire["outcome"] = self.outcome.value
        return wire


Metric = Callable[[Sequence[PredictionRecord]], float | None]


@dataclass(frozen=True, kw_only=True)
class ModelSummary:
    """Counts and metric values for one model across the evaluated games."""

    model_id: str
    games: int
    predicted: int
    correct: int
    incorrect: int
    ties: int
    no_prediction: int
    metrics: Mapping[str, float | None]

    @classmethod
    def from_records(
        cls,
        model_id: str,
        records: Sequence[PredictionRecord],
        metrics: Mapping[str, Metric],
    ) -> "ModelSummary":
        outcomes = [record.outcome for record in records]
        return cls(
            model_id=model_id,
            games=len(records),
            predicted=len(records) - outcomes.count(Outcome.NO_PREDICTION),
            correct=outcomes.count(Outcome.CORRECT),
            incorrect=outcomes.count(Outcome.INCORRECT),
            ties=outcomes.count(Outcome.TIE),
            no_prediction=outcomes.count(Outcome.NO_PREDICTION),
            metrics=MappingProxyType(
                {name: metric(records) for name, metric in metrics.items()}
            ),
        )

    def to_wire(self) -> dict:
        wire = {
            camel_case(name): value
            for name, value in vars(self).items()
            if name != "metrics"
        }
        # A metric that cannot be computed stays null so it is not read as zero.
        wire["metrics"] = {camel_case(name): v for name, v in self.metrics.items()}
        return wire


@dataclass(frozen=True)
class BacktestResult:
    """Every record, plus one summary per model, keyed by model ID."""

    records: tuple[PredictionRecord, ...]
    summaries: Mapping[str, ModelSummary]

    def to_wire(self) -> dict:
        return {
            "summaries": [summary.to_wire() for summary in self.summaries.values()],
            "records": [record.to_wire() for record in self.records],
        }
