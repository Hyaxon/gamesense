"""Offline training: python src/scripts/train_xgboost.py --output /tmp/xgboost.json."""

import argparse
from pathlib import Path

from gamesense_prediction.models.xgboost import XGBoostModel
from gamesense_prediction.snapshots import FileSnapshotProvider


def main() -> int:
    parser = argparse.ArgumentParser(description="Train the XGBoost POC offline.")
    parser.add_argument(
        "--snapshot",
        type=Path,
        default=Path(__file__).resolve().parents[2]
        / "tests/fixtures/xgboost-poc-2025.json",
    )
    parser.add_argument("--snapshot-id", default="xgboost-poc-2025")
    parser.add_argument("--season", type=int, default=2025)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    context = FileSnapshotProvider([args.snapshot]).get(args.snapshot_id, args.season)
    try:
        XGBoostModel.train_and_save(context, args.output)
    except ValueError as error:
        parser.error(str(error))
    print(f"Saved XGBoost model to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
