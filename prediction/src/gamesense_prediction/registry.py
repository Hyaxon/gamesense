"""Immutable startup registry with explicit model capabilities and defaults."""

from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from types import MappingProxyType

from jsonschema import SchemaError
from referencing.exceptions import Unresolvable

from .contracts import ModelDescriptor, PredictionMethod
from .execution_validation import SchemaCatalog
from .models.interface import PredictionModel


@dataclass(frozen=True, kw_only=True)
class ModelRegistration:
    adapter: PredictionModel
    configuration_schema: dict | None = None
    supports_seed: bool = False


@dataclass(frozen=True, kw_only=True)
class RegistryEntry:
    adapter: PredictionModel
    descriptor: ModelDescriptor
    configuration_validator: object | None
    supports_seed: bool


class ModelRegistry:
    def __init__(
        self,
        registrations: Iterable[ModelRegistration],
        defaults: Mapping[PredictionMethod, str],
        *,
        catalog=None,
    ):
        self.catalog = catalog or SchemaCatalog.default()
        entries = {}
        for registration in registrations:
            adapter = registration.adapter
            if not isinstance(adapter, PredictionModel):
                raise ValueError("Adapter must implement PredictionModel")
            descriptor = adapter.get_descriptor()
            if not isinstance(descriptor, ModelDescriptor):
                raise ValueError("Adapter must return a ModelDescriptor")
            self.catalog.validate("model-descriptor", descriptor.to_wire())
            if descriptor.model_id in entries:
                raise ValueError("Duplicate model ID")
            if type(registration.supports_seed) is not bool:
                raise ValueError("Seed capability must be boolean")
            schema = registration.configuration_schema
            if (schema is None) != (descriptor.configuration_schema_id is None):
                raise ValueError("Descriptor and configuration schema must be paired")
            validator = None
            if schema is not None:
                if not isinstance(schema, dict):
                    raise ValueError("Configuration schema must be a JSON object")
                if schema.get("$id") != descriptor.configuration_schema_id:
                    raise ValueError("Configuration schema ID mismatch")
                if {"seed", "trials"} & schema.get("properties", {}).keys():
                    raise ValueError("Seed and trials belong to the common request")
                try:
                    validator = self.catalog.configuration_validator(schema)
                except (SchemaError, Unresolvable) as error:
                    raise ValueError("Invalid configuration schema") from error
            entries[descriptor.model_id] = RegistryEntry(
                adapter=adapter,
                descriptor=descriptor,
                configuration_validator=validator,
                supports_seed=registration.supports_seed,
            )
        normalized = {}
        for method, model_id in defaults.items():
            method = PredictionMethod(method)
            entry = entries.get(model_id)
            if entry is None or entry.descriptor.method != method:
                raise ValueError(
                    "Default must reference a registered model of that method"
                )
            normalized[method] = model_id
        if set(normalized) != {entry.descriptor.method for entry in entries.values()}:
            raise ValueError("Every registered method requires an explicit default")
        self.entries = MappingProxyType(entries)
        self.defaults = MappingProxyType(normalized)

    def get(self, model_id: str) -> RegistryEntry | None:
        return self.entries.get(model_id)

    def resolve_method(self, method: PredictionMethod) -> str:
        """Resolve a caller-facing family once; enum membership is not availability."""
        return self.defaults[PredictionMethod(method)]
