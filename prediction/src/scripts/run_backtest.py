"""Backtest models on completed games: python src/scripts/run_backtest.py --help.

Example from prediction/:
    python src/scripts/run_backtest.py --snapshot tests/fixtures/xgboost-poc-2025.json \
        --models elo glicko2 --output /tmp/backtest.json

XGBoost is left out on purpose: its trained file has already seen its training
games, so scoring it on those games would leak the answers.
"""

import argparse
import json
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path

from gamesense_prediction.backtesting import Backtester, BacktestModel
from gamesense_prediction.contracts import ExecutionKind
from gamesense_prediction.models.bradley_terry import BradleyTerryModel
from gamesense_prediction.models.coin_flip import CoinFlipModel
from gamesense_prediction.models.elo import EloModel
from gamesense_prediction.models.glicko import Glicko2Model
from gamesense_prediction.models.monte_carlo import MonteCarloModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.snapshots import context_from_wire

MODELS = {
    "elo": EloModel,
    "glicko2": Glicko2Model,
    "bradley-terry": BradleyTerryModel,
    "coin-flip": CoinFlipModel,
    "monte-carlo": MonteCarloModel,
}


def parse_bound(text: str, *, is_end: bool) -> datetime:
    """Read an ISO date or datetime as UTC. A bare end date covers the whole day."""
    moment = datetime.fromisoformat(text)
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=UTC)
    if is_end and len(text) == 10:
        moment += timedelta(days=1) - timedelta(microseconds=1)
    return moment


def build_backtester(names: list[str], trials: int, seed: int | None):
    """Register the chosen models and say how to run each one."""
    adapters = [MODELS[name]() for name in names]
    descriptors = [adapter.get_descriptor() for adapter in adapters]
    registry = ModelRegistry(
        [
            ModelRegistration(
                adapter=adapter,
                supports_seed=ExecutionKind.SIMULATION in d.supported_execution_kinds,
            )
            for adapter, d in zip(adapters, descriptors, strict=True)
        ],
        {d.method: d.model_id for d in descriptors},
    )
    models = []
    for d in descriptors:
        if ExecutionKind.DETERMINISTIC in d.supported_execution_kinds:
            models.append(BacktestModel(model_id=d.model_id))
        else:
            models.append(
                BacktestModel(
                    model_id=d.model_id,
                    execution_kind=ExecutionKind.SIMULATION,
                    trials=trials,
                    seed=seed,
                )
            )
    return Backtester(registry), models


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Backtest prediction models.")
    parser.add_argument("--snapshot", type=Path, nargs="+", required=True)
    parser.add_argument("--models", nargs="+", choices=sorted(MODELS), required=True)
    parser.add_argument("--season", type=int, nargs="+", help="seasons to score")
    parser.add_argument("--start", help="first date or time to score (UTC)")
    parser.add_argument("--end", help="last date or time to score (UTC, inclusive)")
    parser.add_argument("--trials", type=int, default=1000, help="simulation trials")
    parser.add_argument("--seed", type=int, help="simulation seed, for repeatability")
    parser.add_argument("--output", type=Path, help="write full results as JSON")
    args = parser.parse_args(argv)

    # Snapshot files are trusted local files, as with the service's own snapshots.
    contexts = [
        context_from_wire(json.loads(path.read_text())) for path in args.snapshot
    ]
    backtester, models = build_backtester(args.models, args.trials, args.seed)
    result = backtester.run(
        contexts,
        models,
        seasons=args.season,
        start=parse_bound(args.start, is_end=False) if args.start else None,
        end=parse_bound(args.end, is_end=True) if args.end else None,
    )

    for summary in result.summaries.values():
        shown = {
            name: "n/a" if value is None else f"{value:.3f}"
            for name, value in summary.metrics.items()
        }
        print(
            f"{summary.model_id}: {summary.games} games, {summary.predicted} predicted,"
            f" {summary.ties} ties, {summary.no_prediction} without a prediction"
        )
        print("  " + ", ".join(f"{name}={value}" for name, value in shown.items()))
    if args.output:
        args.output.write_text(json.dumps(result.to_wire(), indent=2, allow_nan=False))
        print(f"Saved results to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
