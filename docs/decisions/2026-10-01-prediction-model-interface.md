# ADR 3: Common Prediction Model Execution Interface

## Status

Proposed — pending team review.

## Context

The prediction service will host deterministic, stochastic, machine-learning, and simulation adapters. Shared domain models and JSON API contracts define matchup and result fields, but do not define model invocation, capability discovery, or model-specific configuration. Custom matchups need no scheduled game. The MVP uses no database.

## Decision

- Each adapter provides get_descriptor() and execute(request, context), returning a standardized success/error union. One invocation executes one model for one matchup.
- Select adapters through a trusted explicit startup registry keyed by modelId. Keep the shared method enum as algorithm family and return displayName and implementation/artifact version in a descriptor. Multiple implementations can belong to one method.
- Supply an immutable service-owned data snapshot context, identified in requests and execution metadata. Model adapters derive their private features without exposing native model structures to shared consumers.
- Distinguish DETERMINISTIC, STOCHASTIC, and SIMULATION as execution kinds. Capabilities are explicit; neither method name nor computational cost determines the kind.
- Always return the existing SinglePredictionResult shape on success. SIMULATION also returns SimulationOutcome. This preserves winner/confidence semantics and lets Java interpret every method through the same DTOs.
- For two-outcome predictions select the more probable team; at exactly equal probabilities select the home-labeled side. Simulation winner/confidence must agree with its probabilities. No tie outcome is introduced.
- Permit model-specific configuration only inside a dedicated JSON object, validated against a trusted registered schema. Return effective settings when used. Keep common trials and seed controls outside it.
- Reuse existing error codes with execution correlation. Invalid adapter output is an internal failure; expected user input errors are validation failures. No successful partial result accompanies an error.
- Use the existing trial and seed bounds. Seed support and repeatability limits must be documented per adapter. Deterministic execution prohibits random seed controls.
- Return generic named team scores for optional diagnostics and common timing/snapshot metadata. Consumers do not need to understand rating names to interpret predictions.

## Consequences

Model authors can normalize native outputs behind one interface. The backend consumes a predictable success/error envelope without algorithm-specific parsing. Configuration and artifact versions can evolve per model without adding fields to the common wrapper.

Structural JSON Schema validation cannot check snapshot availability, registry capability membership, winner membership, configuration semantics, or numerical consistency between nested outputs. The runner must perform these checks before returning results. The validation tooling checks representative examples, not an implemented service.

The [Python framework](../architecture/prediction-model-development.md) implements immutable data contexts and snapshot providers, a registry, execution helpers, DTOs, and common validation. Model algorithms, production ingestion, and Java integration remain future work. Team review is required before accepting the interface. Training, aggregation, performance comparison, and persistence are outside this decision.
