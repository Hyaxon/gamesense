"""Offline JSON Schema validation and cross-object execution invariants."""

import json
import math
import sysconfig
from collections.abc import Mapping
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from uuid import UUID

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.validators import extend
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

from .contracts import ModelExecutionError, to_json_value

PROBABILITY_TOLERANCE = 0.000001


class ContractValidationError(ValueError):
    """An invalid structure or semantic relationship, with a JSON Pointer."""

    def __init__(self, message, path=""):
        super().__init__(message)
        self.path = path


def pointer(parts):
    return "".join(
        "/" + str(part).replace("~", "~0").replace("/", "~1") for part in parts
    )


def check_json(value, path=""):
    """Reject non-JSON containers, non-string keys, and NaN/Infinity anywhere."""
    if type(value) is dict:
        for key, item in value.items():
            if type(key) is not str:
                raise ContractValidationError("JSON object keys must be strings", path)
            check_json(item, path + pointer([key]))
    elif type(value) is list:
        for index, item in enumerate(value):
            check_json(item, path + pointer([index]))
    elif type(value) is float:
        if not math.isfinite(value):
            raise ContractValidationError("Numbers must be finite", path)
    elif value is not None and type(value) not in (str, int, bool):
        raise ContractValidationError("Value is not a JSON primitive", path)


_STRICT_VALIDATOR = Draft202012Validator.TYPE_CHECKER.redefine(
    "integer", lambda _checker, value: type(value) is int
)
StrictValidator = extend(Draft202012Validator, type_checker=_STRICT_VALIDATOR)


def _strict_resource(schema):
    """Retain draft resolution while preventing references from changing validators.

    jsonschema.evolve selects its stock validator when a referenced schema has
    $schema. Remove that dispatch annotation from private validation copies so
    the strict integer policy survives nested/external references. Canonical
    schema files and annotations inside configuration data remain untouched.
    """
    resource = Resource.from_contents(
        json.loads(json.dumps(schema)), default_specification=DRAFT202012
    )

    def strip_dispatch(current):
        for child in current.subresources():
            strip_dispatch(child)
        if isinstance(current.contents, dict):
            current.contents.pop("$schema", None)

    strip_dispatch(resource)
    return resource


class SchemaCatalog:
    """Trusted local schemas; unresolved references never trigger network access."""

    def __init__(self, schema_directory: Path):
        self.schemas = {
            path.name.removesuffix(".schema.json"): json.loads(path.read_text())
            for path in sorted(schema_directory.glob("*.schema.json"))
        }
        if "model-execution-response" not in self.schemas:
            raise ValueError("Shared prediction schemas are unavailable")
        snapshot_path = Path(__file__).parent / "resources" / "snapshot.schema.json"
        self.schemas["snapshot"] = json.loads(snapshot_path.read_text())
        for schema in self.schemas.values():
            Draft202012Validator.check_schema(schema)
        resources = {
            name: _strict_resource(schema) for name, schema in self.schemas.items()
        }
        self.registry = Registry().with_resources(
            (self.schemas[name]["$id"], resource)
            for name, resource in resources.items()
        )
        self.validators = {
            name: StrictValidator(
                resource.contents,
                registry=self.registry,
                format_checker=FormatChecker(),
            )
            for name, resource in resources.items()
        }

    @staticmethod
    @lru_cache(maxsize=1)
    def default():
        repository = Path(__file__).resolve().parents[3] / "shared" / "schemas"
        installed = Path(sysconfig.get_path("data")) / "share/gamesense/schemas"
        return SchemaCatalog(repository if repository.is_dir() else installed)

    @staticmethod
    def validate_with(validator, payload, *, prefix=""):
        check_json(payload)
        error = next(validator.iter_errors(payload), None)
        if error is not None:
            # Keep payload values out of public exception text.
            raise ContractValidationError(
                "Value does not satisfy its schema",
                prefix + pointer(error.absolute_path),
            )

    def validate(self, schema_name, payload):
        self.validate_with(self.validators[schema_name], payload)

    def configuration_validator(self, schema):
        check_json(schema)
        schema = json.loads(json.dumps(schema))  # Detach trusted caller-owned settings.
        if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
            raise ValueError("Configuration schemas must declare draft 2020-12")
        Draft202012Validator.check_schema(schema)
        resource = _strict_resource(schema)
        base = schema.get("$id", "urn:gamesense:configuration")
        registry = self.registry.with_resource(base, resource)

        def check_references(resource, resolver):
            node = resource.contents
            if isinstance(node, dict):
                for keyword in ("$ref", "$dynamicRef"):
                    if keyword in node:
                        resolver.lookup(node[keyword])
            # Visit schemas, not arbitrary dictionaries in defaults/examples.
            for child in resource.subresources():
                check_references(child, resolver.in_subresource(child))

        # Fail at registration rather than accepting a schema with unresolved URLs.
        check_references(resource, registry.resolver(base))
        return StrictValidator(
            resource.contents, registry=registry, format_checker=FormatChecker()
        )


def require(condition, message, path=""):
    if not condition:
        raise ContractValidationError(message, path)


def canonical_identifier(value, path):
    identifier = UUID(value)
    require(
        identifier.version == 4 and str(identifier) == value, "Expected UUID v4", path
    )


def validate_request(request, catalog):
    catalog.validate("model-execution-request", request.to_wire())
    canonical_identifier(request.execution_id, "/executionId")
    canonical_identifier(request.matchup.home_team_id, "/matchup/homeTeamId")
    canonical_identifier(request.matchup.away_team_id, "/matchup/awayTeamId")
    require(
        request.matchup.home_team_id != request.matchup.away_team_id,
        "Matchup teams must differ",
        "/matchup",
    )


def validate_response(
    request, response, descriptor, catalog, configuration_validator=None
):
    catalog.validate("model-execution-response", response.to_wire())
    require(response.execution_id == request.execution_id, "Execution ID mismatch")
    if isinstance(response, ModelExecutionError):
        require(response.model_id == request.model_id, "Model ID mismatch")
        return
    require(response.model == descriptor, "Registered descriptor mismatch", "/model")
    require(response.matchup == request.matchup, "Matchup mismatch", "/matchup")
    metadata = response.metadata
    require(
        metadata.execution_kind == request.execution_kind, "Execution kind mismatch"
    )
    require(metadata.data_snapshot_id == request.data_snapshot_id, "Snapshot mismatch")
    require(
        datetime.fromisoformat(metadata.completed_at)
        >= datetime.fromisoformat(metadata.started_at),
        "Completion precedes start",
    )
    if request.seed is not None:
        require(metadata.seed == request.seed, "Seed mismatch", "/metadata/seed")
    if configuration_validator is not None:
        effective = to_json_value(response.configuration or {})
        require(
            not {"seed", "trials"} & effective.keys(),
            "Common controls cannot appear in configuration",
            "/configuration",
        )
        catalog.validate_with(
            configuration_validator, effective, prefix="/configuration"
        )
        require(
            response.configuration is not None
            or not request.configuration
            and not _has_defaults(configuration_validator.schema),
            "Effective configuration is required",
            "/configuration",
        )
        require(
            all(
                key in effective and effective[key] == to_json_value(value)
                for key, value in (request.configuration or {}).items()
            ),
            "Explicit configuration must be honored",
            "/configuration",
        )
    else:
        require(not response.configuration, "Unregistered configuration returned")
    prediction = response.prediction
    canonical_identifier(prediction.id, "/prediction/id")
    home = request.matchup.home_team_id
    away = request.matchup.away_team_id
    require(
        prediction.predicted_winner_team_id in (home, away), "Winner outside matchup"
    )
    require(prediction.method == descriptor.method, "Prediction method mismatch")
    require(prediction.confidence >= 0.5, "Winner confidence below 0.5")
    if prediction.confidence == 0.5:
        require(prediction.predicted_winner_team_id == home, "Equal odds select home")
    seen = set()
    for score in response.supporting_scores or ():
        require(score.team_id in (home, away), "Supporting score outside matchup")
        key = (score.team_id, score.name)
        require(key not in seen, "Duplicate supporting score")
        seen.add(key)
    if response.simulation is not None:
        simulation = response.simulation
        canonical_identifier(simulation.id, "/simulation/id")
        require(simulation.id != prediction.id, "Result identifiers must be unique")
        require(simulation.method == descriptor.method, "Simulation method mismatch")
        require(simulation.trials == request.trials, "Trial count mismatch")
        home_probability = simulation.home_win_probability
        away_probability = simulation.away_win_probability
        require(
            abs(home_probability + away_probability - 1) <= PROBABILITY_TOLERANCE,
            "Simulation probabilities must sum to one",
        )
        winner = home if home_probability >= away_probability else away
        require(
            prediction.predicted_winner_team_id == winner, "Simulation winner mismatch"
        )
        require(
            abs(prediction.confidence - max(home_probability, away_probability))
            <= PROBABILITY_TOLERANCE,
            "Simulation confidence mismatch",
        )


def _has_defaults(value):
    if isinstance(value, Mapping):
        return "default" in value or any(_has_defaults(item) for item in value.values())
    if isinstance(value, list):
        return any(_has_defaults(item) for item in value)
    return False
