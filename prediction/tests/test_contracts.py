"""Canonical example compatibility and strict wire-boundary behavior."""

import json
from dataclasses import replace
from pathlib import Path

import pytest

from gamesense_prediction.contracts import (
    ErrorResponse,
    Matchup,
    ModelDescriptor,
    ModelExecutionError,
    ModelExecutionRequest,
    ModelExecutionResult,
    SimulationOutcome,
    SinglePredictionResult,
    response_from_wire,
)
from gamesense_prediction.execution_validation import (
    ContractValidationError,
    SchemaCatalog,
)

EXAMPLES = Path(__file__).resolve().parents[2] / "shared/examples"


@pytest.mark.parametrize(
    "name,cls",
    [
        ("matchup", Matchup),
        ("model-descriptor", ModelDescriptor),
        ("model-execution-request", ModelExecutionRequest),
        ("model-execution-result", ModelExecutionResult),
        ("model-execution-error", ModelExecutionError),
        ("error", ErrorResponse),
        ("single-prediction-result", SinglePredictionResult),
        ("simulation-outcome", SimulationOutcome),
    ],
)
def test_shared_examples_round_trip(name, cls):
    payload = json.loads((EXAMPLES / f"{name}.json").read_text())
    value = cls.from_wire(payload)
    assert value.to_wire() == payload


@pytest.mark.parametrize("kind", ["deterministic", "stochastic", "simulation"])
def test_complete_execution_examples(kind):
    for suffix, parse in [
        ("request", ModelExecutionRequest.from_wire),
        ("result", response_from_wire),
    ]:
        payload = json.loads(
            (EXAMPLES / "model-execution" / f"{kind}-{suffix}.json").read_text()
        )
        assert parse(payload).to_wire() == payload


def test_error_union():
    payload = json.loads((EXAMPLES / "model-execution-error.json").read_text())
    assert isinstance(response_from_wire(payload), ModelExecutionError)
    payload["prediction"] = {}
    with pytest.raises(ContractValidationError):
        response_from_wire(payload)


@pytest.mark.parametrize("value", ["10", True, 1.0, 0, 100001])
def test_simulation_trials_are_strict_integers(execution_request, value):
    payload = execution_request.to_wire()
    payload.update(executionKind="SIMULATION", trials=value)
    with pytest.raises(ContractValidationError):
        ModelExecutionRequest.from_wire(payload)


@pytest.mark.parametrize("value", ["42", True, 1.0, -1, 2147483648])
def test_seed_boundaries(execution_request, value):
    payload = execution_request.to_wire()
    payload.update(executionKind="STOCHASTIC", seed=value)
    with pytest.raises(ContractValidationError):
        ModelExecutionRequest.from_wire(payload)


@pytest.mark.parametrize(
    "mutation",
    [
        lambda p: p.update(extra=True),
        lambda p: p.update(configuration=None),
        lambda p: p["matchup"].update(season="2025"),
        lambda p: p["matchup"].update(season=2025.0),
        lambda p: p["matchup"].update(isNeutralSite="false"),
        lambda p: p.update(seed=42),
        lambda p: p.update(trials=1),
        lambda p: p.update(configuration={"nested": [float("nan")]}),
        lambda p: p.update(configuration={"nested": float("inf")}),
        lambda p: p.update(configuration={1: "invalid"}),
    ],
)
def test_invalid_request_values(execution_request, mutation):
    payload = execution_request.to_wire()
    mutation(payload)
    with pytest.raises(ContractValidationError):
        ModelExecutionRequest.from_wire(payload)


def test_optional_fields_and_configuration_nulls(execution_request):
    assert "seed" not in execution_request.to_wire()
    configured = replace(execution_request, configuration={"nullableSetting": None})
    assert configured.to_wire()["configuration"] == {"nullableSetting": None}


def test_configuration_detached_and_immutable(execution_request):
    original = {"nested": {"values": [1, 2]}}
    configured = replace(execution_request, configuration=original)
    original["nested"]["values"].append(3)
    assert configured.to_wire()["configuration"] == {"nested": {"values": [1, 2]}}
    with pytest.raises(TypeError):
        configured.configuration["nested"]["other"] = True


def test_validation_paths_escape_json_pointer():
    catalog = SchemaCatalog.default()
    validator = catalog.configuration_validator(
        {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "urn:example:escaped",
            "type": "object",
            "properties": {"a/b~c": {"type": "integer"}},
        }
    )
    with pytest.raises(ContractValidationError) as error:
        catalog.validate_with(validator, {"a/b~c": "SECRET"}, prefix="/configuration")
    assert error.value.path == "/configuration/a~1b~0c"
    assert "SECRET" not in str(error.value)


def test_strict_integers_survive_references():
    catalog = SchemaCatalog.default()
    validator = catalog.configuration_validator(
        {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": "urn:example:referenced-trials",
            "type": "object",
            "properties": {
                "count": {
                    "$ref": "https://gamesense.dev/schemas/model-execution-request.schema.json#/properties/trials"
                }
            },
        }
    )
    with pytest.raises(ContractValidationError):
        catalog.validate_with(validator, {"count": 1.0})
    catalog.validate_with(validator, {"count": 1})
