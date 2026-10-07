# Shared API Contracts

This directory contains JSON contracts used by the Java backend, Python prediction service, and JavaScript/TypeScript frontend. The MVP uses no database; these contracts describe data exchanged between components independently of storage.

## Directory layout

| Directory | Contents |
|---|---|
| [schemas/](schemas/) | JSON Schema draft 2020-12 definitions for entities, requests, results, errors, and reusable types. |
| [examples/](examples/) | Example payloads named after their corresponding schemas: `prediction-response.json` validates against `prediction-response.schema.json`. |
| [validation/](validation/) | Python validation script and its dependency requirements. |

## Start here

- [API schema documentation](../docs/architecture/api-schemas.md): payload inventory, field tables, examples, validation rules, and deferred features.
- [Prediction model interface](../docs/architecture/prediction-model-interface.md): standard invocation, registration, configuration, execution metadata, and model examples.
- [Prediction model development](../docs/architecture/prediction-model-development.md): Python framework implementation and model author guide.
- [Shared domain models](../docs/architecture/domain-model.md): application concepts and relationships.
- [Shared domain conventions ADR](../docs/decisions/2026-09-22-shared-domain-model-conventions.md): naming, identifiers, timestamps, and enums.
- [Proposed API contracts ADR](../docs/decisions/2026-10-01-core-api-contracts.md): custom matchups, result context, nullability, and other contract decisions.

JSON fields use camelCase and enum values use UPPER_SNAKE_CASE. Probabilities are numeric values from 0 to 1. New contracts omit unavailable optional fields rather than sending null. Consult the API documentation for exceptions in the existing Game schema.

Custom prediction requests describe a matchup independent of the schedule. A prediction response carries that matchup and its results; `gameId` is included only when referring to an existing game. LLM prediction summaries and persistence are deferred.

## Validate schemas and examples

Run from the repository root with Python 3.9 or newer:

```sh
python3 -m venv /tmp/gamesense-schema-validation
/tmp/gamesense-schema-validation/bin/pip install -r shared/validation/requirements.txt
/tmp/gamesense-schema-validation/bin/python shared/validation/validate_schemas.py
```

The script validates schema definitions, resolves references through a local registry, checks every example with format validation enabled, checks enum consistency, and tests representative invalid payloads and probability boundaries. Schema IDs are identifiers; validation does not fetch them from the internet.

CI runs this validator on pull requests targeting `main` and pushes to `main`, with both normal and optimized (`-O`) Python execution.

Additional deterministic, stochastic, and simulation execution examples live in [examples/model-execution/](examples/model-execution/). The validator checks their schemas and representative request/result relationships; these checks do not implement service runtime validation.

Schema validation does not establish whether referenced teams exist, a winner belongs to the matchup, or simulation probabilities sum to one. The Python prediction runner implements common service-level checks; model authors remain responsible for their required inputs and algorithm correctness.

## Updating a contract

1. Update the schema and its matching example together. Reusable definitions in `common.schema.json` do not require a standalone payload example.
2. Update the field documentation and add validation cases for meaningful new constraints.
3. Record significant contract decisions in an ADR and run the validator.
4. Review changes with the team before merging. Unknown properties are rejected, so adding fields requires coordinated consumer updates.

## Contract review

The API contracts are proposed for team review. Confirm season semantics and the proposed simulation trial limit before accepting them. Structural validation and application-level semantic checks are documented separately in the API schema documentation.
