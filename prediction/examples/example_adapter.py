"""Runnable contract demonstration, not an Elo or Monte Carlo implementation.

Run from prediction/: python examples/example_adapter.py
"""

import json
from pathlib import Path

from gamesense_prediction.contracts import (
    ExecutionKind,
    Matchup,
    ModelDescriptor,
    ModelExecutionRequest,
    PredictionMethod,
    SimulationOutcome,
)
from gamesense_prediction.execution_helpers import (
    ExecutionTimer,
    new_identifier,
    utc_now,
)
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider

CONFIGURATION_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": "urn:gamesense:example:configuration",
    "type": "object",
    "properties": {
        "homeProbability": {
            "type": "number",
            "minimum": 0,
            "maximum": 1,
            "default": 0.6,
        }
    },
    "additionalProperties": False,
}


class ExampleAdapter:
    """Return an illustrative probability, without claiming to run trials.

    All modes intentionally share fixed synthetic values. Seed is echoed but
    is rejected by registration because this example uses no randomness.
    Real adapters must implement the declared mode and document reproducibility.
    """

    def get_descriptor(self):
        return ModelDescriptor(
            model_id="example-v1",
            display_name="Contract example",
            method=PredictionMethod.HOME_GROWN,
            version="1.0.0",
            supported_execution_kinds=tuple(ExecutionKind),
            configuration_schema_id=CONFIGURATION_SCHEMA["$id"],
        )

    def execute(self, request, context):
        timer = ExecutionTimer()
        context.require_team(request.matchup.home_team_id)
        context.require_team(request.matchup.away_team_id)
        configuration = {"homeProbability": 0.6, **(request.configuration or {})}
        probability = configuration["homeProbability"]
        winner = (
            request.matchup.home_team_id
            if probability >= 0.5
            else (request.matchup.away_team_id)
        )
        descriptor = self.get_descriptor()
        simulation = None
        if request.execution_kind == ExecutionKind.SIMULATION:
            simulation = SimulationOutcome(
                id=new_identifier(),
                method=descriptor.method,
                trials=request.trials,
                home_win_probability=probability,
                away_win_probability=1 - probability,
                simulated_at=utc_now(),
            )
        return timer.success(
            request,
            descriptor,
            winner_team_id=winner,
            confidence=max(probability, 1 - probability),
            simulation=simulation,
            configuration=configuration,
        )


def main():
    registry = ModelRegistry(
        [
            ModelRegistration(
                adapter=ExampleAdapter(), configuration_schema=CONFIGURATION_SCHEMA
            )
        ],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    fixture = Path(__file__).parents[1] / "tests/fixtures/season-2025.json"
    snapshots = FileSnapshotProvider([fixture])
    runner = PredictionRunner(registry, snapshots)
    for kind in ExecutionKind:
        request = ModelExecutionRequest(
            execution_id=new_identifier(),
            model_id=registry.resolve_method(PredictionMethod.HOME_GROWN),
            matchup=Matchup(
                home_team_id="b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
                away_team_id="1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                season=2025,
                is_neutral_site=True,
            ),
            data_snapshot_id="development-2025",
            execution_kind=kind,
            trials=10 if kind == ExecutionKind.SIMULATION else None,
        )
        response = runner.execute(request)
        if response.to_wire().get("status") != "SUCCESS":
            raise RuntimeError("Example adapter failed contract validation")
        print(json.dumps(response.to_wire(), indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
