"""Startup validation and model selection without algorithm-name branching."""

import copy
from dataclasses import replace

import pytest

from gamesense_prediction.contracts import ExecutionKind, PredictionMethod
from gamesense_prediction.registry import ModelRegistration, ModelRegistry


def registration(adapter, example_module):
    return ModelRegistration(
        adapter=adapter, configuration_schema=example_module.CONFIGURATION_SCHEMA
    )


def test_method_default_and_immutable_entries(registry):
    assert registry.resolve_method(PredictionMethod.HOME_GROWN) == "example-v1"
    with pytest.raises(KeyError):
        registry.resolve_method(PredictionMethod.ELO)
    with pytest.raises(TypeError):
        registry.entries["other"] = registry.get("example-v1")


def test_duplicate_ids_fail(adapter, example_module):
    item = registration(adapter, example_module)
    with pytest.raises(ValueError, match="Duplicate model"):
        ModelRegistry([item, item], {PredictionMethod.HOME_GROWN: "example-v1"})


@pytest.mark.parametrize(
    "defaults",
    [
        {},
        {PredictionMethod.ELO: "example-v1"},
        {PredictionMethod.HOME_GROWN: "missing"},
    ],
)
def test_defaults_must_match_registered_families(adapter, example_module, defaults):
    with pytest.raises(ValueError):
        ModelRegistry([registration(adapter, example_module)], defaults)


def test_multiple_models_can_share_family(adapter, example_module, monkeypatch):
    second = type(adapter)()
    descriptor = replace(
        second.get_descriptor(), model_id="example-v2", version="2.0.0"
    )
    monkeypatch.setattr(second, "get_descriptor", lambda: descriptor)
    registry = ModelRegistry(
        [registration(adapter, example_module), registration(second, example_module)],
        {PredictionMethod.HOME_GROWN: "example-v2"},
    )
    assert len(registry.entries) == 2
    assert registry.resolve_method(PredictionMethod.HOME_GROWN) == "example-v2"


def test_invalid_adapter_rejected():
    with pytest.raises(ValueError, match="PredictionModel"):
        ModelRegistry([ModelRegistration(adapter=object())], {})


@pytest.mark.parametrize(
    "kinds", [(), (ExecutionKind.SIMULATION, ExecutionKind.SIMULATION)]
)
def test_invalid_descriptor_rejected(adapter, example_module, monkeypatch, kinds):
    descriptor = replace(adapter.get_descriptor(), supported_execution_kinds=kinds)
    monkeypatch.setattr(adapter, "get_descriptor", lambda: descriptor)
    with pytest.raises(ValueError):
        ModelRegistry([registration(adapter, example_module)], {})


@pytest.mark.parametrize(
    "mutation",
    [
        lambda s: s.update({"$id": "different"}),
        lambda s: s.update({"$schema": "https://json-schema.org/draft-07/schema"}),
        lambda s: s.update(type="invalid-type"),
        lambda s: s["properties"]["homeProbability"].update(
            {"$ref": "https://unregistered.example/schema.json"}
        ),
    ],
)
def test_invalid_configuration_schemas_fail_startup(adapter, example_module, mutation):
    schema = copy.deepcopy(example_module.CONFIGURATION_SCHEMA)
    mutation(schema)
    with pytest.raises(ValueError):
        ModelRegistry(
            [ModelRegistration(adapter=adapter, configuration_schema=schema)],
            {PredictionMethod.HOME_GROWN: "example-v1"},
        )


def test_configuration_schema_required_when_declared(adapter):
    with pytest.raises(ValueError, match="paired"):
        ModelRegistry([ModelRegistration(adapter=adapter)], {})


def test_configuration_schema_is_detached(adapter, example_module):
    schema = copy.deepcopy(example_module.CONFIGURATION_SCHEMA)
    registry = ModelRegistry(
        [ModelRegistration(adapter=adapter, configuration_schema=schema)],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    schema["properties"]["homeProbability"]["maximum"] = 99
    validator = registry.get("example-v1").configuration_validator
    assert not validator.is_valid({"homeProbability": 2})


def test_local_configuration_references(adapter, example_module):
    schema = copy.deepcopy(example_module.CONFIGURATION_SCHEMA)
    schema["$defs"] = {"probability": {"type": "number", "minimum": 0, "maximum": 1}}
    schema["properties"]["homeProbability"] = {"$ref": "#/$defs/probability"}
    registry = ModelRegistry(
        [ModelRegistration(adapter=adapter, configuration_schema=schema)],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    assert registry.get("example-v1").configuration_validator.is_valid(
        {"homeProbability": 0.5}
    )


@pytest.mark.parametrize("name", ["seed", "trials"])
def test_configuration_cannot_redeclare_common_controls(adapter, example_module, name):
    schema = copy.deepcopy(example_module.CONFIGURATION_SCHEMA)
    schema["properties"][name] = {"type": "integer"}
    with pytest.raises(ValueError, match="common request"):
        ModelRegistry(
            [ModelRegistration(adapter=adapter, configuration_schema=schema)],
            {PredictionMethod.HOME_GROWN: "example-v1"},
        )


def test_schema_like_default_data_is_not_resolved(adapter, example_module):
    schema = copy.deepcopy(example_module.CONFIGURATION_SCHEMA)
    value = {"$schema": "not-a-schema", "$ref": "https://unregistered.example/data"}
    schema["properties"]["blob"] = {"type": "object", "default": value}
    registry = ModelRegistry(
        [ModelRegistration(adapter=adapter, configuration_schema=schema)],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    assert (
        registry.get("example-v1").configuration_validator.schema["properties"]["blob"][
            "default"
        ]
        == value
    )
