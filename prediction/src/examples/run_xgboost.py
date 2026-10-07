"""Run the synthetic POC: python src/examples/run_xgboost.py [--model MODEL.json]."""

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
from gamesense_prediction.models.xgboost import XGBoostModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Predict a matchup with the XGBoost POC."
    )
    parser.add_argument(
        "--model", type=Path, help="Load an offline trained JSON/UBJ model"
    )
    args = parser.parse_args()

    fixture = (
        Path(__file__).resolve().parents[2] / "tests/fixtures/xgboost-poc-2025.json"
    )
    snapshots = FileSnapshotProvider([fixture])
    context = snapshots.get("xgboost-poc-2025", 2025)
    # This local demo trains during setup. The adapter only performs inference.
    if args.model is None:
        adapter = XGBoostModel.train(context)
    else:
        adapter = XGBoostModel.from_file(args.model)
    registry = ModelRegistry(
        [ModelRegistration(adapter=adapter)],
        {PredictionMethod.MACHINE_LEARNING: "xgboost-v1"},
    )
    home_team_id, away_team_id = tuple(context.teams)[:2]
    request = ModelExecutionRequest(
        execution_id=new_identifier(),
        model_id=registry.resolve_method(PredictionMethod.MACHINE_LEARNING),
        matchup=Matchup(
            home_team_id=home_team_id,
            away_team_id=away_team_id,
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
