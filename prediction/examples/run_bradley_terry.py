"""Run the Bradley-Terry model against a snapshot.

From prediction/: python examples/run_bradley_terry.py
Pass --snapshot / --snapshot-id / --season to use other data; the home and
away teams default to the first two teams in the snapshot.
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
from gamesense_prediction.models.bradley_terry import BradleyTerryModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider

DEFAULT_SNAPSHOT = (
    Path(__file__).resolve().parents[1] / "tests/fixtures/season-2025.json"
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Predict a matchup using the Bradley-Terry rating model."
    )
    parser.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument("--snapshot-id", default="development-2025")
    parser.add_argument("--season", type=int, default=2025)
    parser.add_argument("--home", help="Home team ID (default: first team in snapshot)")
    parser.add_argument(
        "--away", help="Away team ID (default: second team in snapshot)"
    )
    args = parser.parse_args()

    snapshots = FileSnapshotProvider([args.snapshot])
    context = snapshots.get(args.snapshot_id, args.season)
    default_home, default_away = tuple(context.teams)[:2]

    registry = ModelRegistry(
        [ModelRegistration(adapter=BradleyTerryModel(), supports_seed=False)],
        {PredictionMethod.BRADLEY_TERRY: "bradley-terry-v1"},
    )
    request = ModelExecutionRequest(
        execution_id=new_identifier(),
        model_id=registry.resolve_method(PredictionMethod.BRADLEY_TERRY),
        matchup=Matchup(
            home_team_id=args.home or default_home,
            away_team_id=args.away or default_away,
            season=context.season,
            is_neutral_site=True,
        ),
        data_snapshot_id=context.data_snapshot_id,
        execution_kind=ExecutionKind.DETERMINISTIC,
    )
    response = PredictionRunner(registry, snapshots).execute(request)
    print(json.dumps(response.to_wire(), indent=2, allow_nan=False))
    return 0 if isinstance(response, ModelExecutionResult) else 1


if __name__ == "__main__":
    raise SystemExit(main())
