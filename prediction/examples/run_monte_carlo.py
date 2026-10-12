"""Run the real Monte Carlo package against the development snapshot.

From prediction/: python examples/run_monte_carlo.py --trials 10000 --seed 42
"""

import argparse
import json
from pathlib import Path

from gamesense_prediction.contracts import (
    ExecutionKind,
    Matchup,
    ModelExecutionRequest,
    ModelExecutionResult,
    PredictionMethod,
)
from gamesense_prediction.execution_helpers import new_identifier
from gamesense_prediction.models.monte_carlo import MonteCarloModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider


def build_runner() -> PredictionRunner:
    """Explicit local wiring; this does not register a production HTTP endpoint."""
    registry = ModelRegistry(
        [ModelRegistration(adapter=MonteCarloModel(), supports_seed=True)],
        {PredictionMethod.MONTE_CARLO: "monte-carlo-v1"},
    )
    fixture = Path(__file__).resolve().parents[1] / "tests/fixtures/season-2025.json"
    return PredictionRunner(registry, FileSnapshotProvider([fixture]))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Simulate a matchup by sampling random scores."
    )
    parser.add_argument(
        "--trials",
        type=int,
        default=10000,
        help="Number of simulated games, from 1 to 100000 (default: 10000)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help="Optional repeatable seed, from 0 to 2147483647",
    )
    args = parser.parse_args()

    runner = build_runner()
    request = ModelExecutionRequest(
        execution_id=new_identifier(),
        model_id=runner.registry.resolve_method(PredictionMethod.MONTE_CARLO),
        matchup=Matchup(
            home_team_id="b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
            away_team_id="1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
            season=2025,
            is_neutral_site=True,
        ),
        data_snapshot_id="development-2025",
        execution_kind=ExecutionKind.SIMULATION,
        trials=args.trials,
        seed=args.seed,
    )
    response = runner.execute(request)
    print(json.dumps(response.to_wire(), indent=2, allow_nan=False))
    return 0 if isinstance(response, ModelExecutionResult) else 1


if __name__ == "__main__":
    raise SystemExit(main())
