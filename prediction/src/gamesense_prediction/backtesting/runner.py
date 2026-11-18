"""Replays completed games through the prediction runner, using pre-game data only.

Backtester runs every chosen model on every selected game, and BacktestModel says
how to run one model. Each game is predicted from a context holding only earlier
games, so no model can see the result it is being scored on.
"""

from collections.abc import Collection, Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime

from ..context import CompletedGame, MissingContextError, PredictionContext
from ..contracts import (
    ExecutionKind,
    Matchup,
    ModelExecutionError,
    ModelExecutionRequest,
    ModelExecutionResult,
)
from ..execution_helpers import new_identifier
from ..registry import ModelRegistry
from ..runner import PredictionRunner
from .as_of import GameTimeline
from .metrics import DEFAULT_METRICS
from .results import BacktestResult, Metric, ModelSummary, PredictionRecord


@dataclass(frozen=True, kw_only=True)
class BacktestModel:
    """A registered model and how to run it, as in a normal execution request."""

    model_id: str
    execution_kind: ExecutionKind = ExecutionKind.DETERMINISTIC
    configuration: Mapping | None = None
    seed: int | None = None
    trials: int | None = None


class _CurrentContextProvider:
    """Serves the pre-game context of the game being evaluated, and nothing else."""

    def __init__(self):
        self.context: PredictionContext | None = None

    def get(self, data_snapshot_id: str, season: int) -> PredictionContext:
        context = self.context
        if (
            context is None
            or context.data_snapshot_id != data_snapshot_id
            or context.season != season
        ):
            raise MissingContextError("Required snapshot or season is unavailable")
        return context


class Backtester:
    def __init__(
        self, registry: ModelRegistry, metrics: Mapping[str, Metric] | None = None
    ):
        self.registry = registry
        self.metrics = DEFAULT_METRICS if metrics is None else metrics

    def run(
        self,
        contexts: Sequence[PredictionContext],
        models: Sequence[BacktestModel],
        *,
        seasons: Collection[int] | None = None,
        start: datetime | None = None,
        end: datetime | None = None,
    ) -> BacktestResult:
        """Evaluate each model on every completed game in the chosen range.

        ``contexts`` supply the games and teams; games from several seasons
        build on each other, so history carries across seasons. ``seasons``,
        ``start`` and ``end`` (inclusive, timezone-aware) pick which games are
        scored. The games before the range still count as history.
        """
        self._check_models(models)
        for bound in (start, end):
            if bound is not None and bound.tzinfo is None:
                raise ValueError("start and end must be timezone-aware")

        timeline = GameTimeline(contexts)
        provider = _CurrentContextProvider()
        runner = PredictionRunner(self.registry, provider)
        records = []
        for game in timeline.select(seasons=seasons, start=start, end=end):
            provider.context = timeline.context_before(game)
            for model in models:
                request = _request(model, game, provider.context)
                records.append(_record(model.model_id, game, runner.execute(request)))

        summaries = {
            model.model_id: ModelSummary.from_records(
                model.model_id,
                [r for r in records if r.model_id == model.model_id],
                self.metrics,
            )
            for model in models
        }
        return BacktestResult(records=tuple(records), summaries=summaries)

    def _check_models(self, models: Sequence[BacktestModel]) -> None:
        if not models:
            raise ValueError("At least one model is required")
        ids = [model.model_id for model in models]
        if len(set(ids)) != len(ids):
            raise ValueError("Each model may appear only once")
        for model_id in ids:
            if self.registry.get(model_id) is None:
                raise ValueError(f"Unknown model: {model_id}")


def _request(
    model: BacktestModel, game: CompletedGame, context: PredictionContext
) -> ModelExecutionRequest:
    return ModelExecutionRequest(
        execution_id=new_identifier(),
        model_id=model.model_id,
        matchup=Matchup(
            home_team_id=game.home_team_id,
            away_team_id=game.away_team_id,
            season=game.season,
            is_neutral_site=game.is_neutral_site,
        ),
        data_snapshot_id=context.data_snapshot_id,
        execution_kind=model.execution_kind,
        configuration=model.configuration,
        seed=model.seed,
        trials=model.trials,
    )


def _record(model_id: str, game: CompletedGame, response) -> PredictionRecord:
    """Pair a model's response with the real result of the game."""
    known = {
        "model_id": model_id,
        "game_id": game.id,
        "season": game.season,
        "scheduled_at": game.scheduled_at,
        "home_team_id": game.home_team_id,
        "away_team_id": game.away_team_id,
        "actual_winner_team_id": game.winner_team_id,
    }
    if isinstance(response, ModelExecutionResult):
        prediction = response.prediction
        if response.simulation is not None:
            home_probability = response.simulation.home_win_probability
        elif prediction.predicted_winner_team_id == game.home_team_id:
            home_probability = prediction.confidence
        else:
            home_probability = 1 - prediction.confidence
        return PredictionRecord(
            **known,
            predicted_winner_team_id=prediction.predicted_winner_team_id,
            home_win_probability=home_probability,
        )
    error = response.error if isinstance(response, ModelExecutionError) else response
    return PredictionRecord(**known, error_code=str(error.code))
