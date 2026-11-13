# ADR: Publish immutable prediction snapshots from Java to Python

## Status

Proposed — implemented for issue #67; pending team review.

## Context

Java owns ingestion and canonical sports data. Python models need a fixed dataset
identified by `dataSnapshotId` so executions can reference the same evidence.
Previously, Java constructed `season-<year>` references without transferring the
data, while Python only knew the snapshot loaded from its startup file. Prediction
requests therefore referenced unavailable snapshots.

The MVP uses in-memory storage. Publishing must not prevent the backend from
starting and serving games when Python is unavailable. Local file-based prediction
development must remain supported.

## Decision

- Java pushes a snapshot through `POST /snapshots` after startup ingestion succeeds.
  It publishes the seasons represented in the live feed, using canonical games,
  their referenced teams, and provenance. Python holds immutable execution copies;
  Java remains the owner of canonical sports data.
- Python assigns each HTTP publication an opaque ID of the form
  `season-<year>-<uuid>`. Java omits the ID from the request and records the returned
  value only after Python acknowledges success. Execution requests use that exact
  ID. IDs identify versions, not content hashes or chronological sequence numbers.
- Python validates the complete payload before storing it. Malformed records reject
  the entire publication; no games are silently discarded by the snapshot builder
  or provider. The provider rejects duplicate IDs and serializes storage updates
  with a lock. Existing snapshots cannot be overwritten.
- Repeated content creates a new ID. Content deduplication and idempotent retries
  are not part of this implementation.
- Publication failures are logged without failing backend startup. Ingestion
  failures still stop startup. Publication can be disabled independently with
  `prediction.snapshot.publish.enabled`.
- `PREDICTION_SNAPSHOT_PATH` is optional. Files retain their supplied IDs and pass
  through the same validation and storage path as published snapshots. Java can
  use an explicit `prediction.snapshot.fallback-season` and
  `prediction.snapshot.fallback-id` pair for matching file-based data. A published
  ID takes precedence. Without either source, snapshot lookup returns empty.

Python-generated IDs keep publication acknowledgement and identity assignment in
one place. Java-generated IDs would also work, but would still require a successful
publish before Java could use them. Push delivery follows the point where Java
knows ingestion has completed. Having Python fetch data on every prediction would
add an execution-time dependency on Java; fetching and caching by ID would require
Java to expose immutable versioned datasets through another endpoint.

## Consequences

Predictions can reference validated, stable input data without resending a season
for every execution. Publishing and execution use the same Python provider, and
file-based development remains available.

Snapshots are process-local, so Python must run with one worker. Restarting Java
while Python remains running creates another snapshot, even if the content is
unchanged. Older snapshots remain in memory; there is no eviction policy.

Restarting Python loses HTTP-published snapshots. Java may retain an ID that no
longer exists, causing `NOT_FOUND` until data is published again. For this MVP,
start Python before Java and restart Java to republish after a Python restart.
A lost publish response can also leave an unused snapshot in Python.

Automatic refresh or republishing (#65), persistence, restart recovery, retention,
and content deduplication are deferred. Shared durable snapshot storage with Python
loading and caching by ID is a possible future approach, not a selected design.
Model registration (#58) and backtesting cutoffs (#54) are outside this decision.

See [data ingestion](../architecture/data-ingestion.md#seasons-and-snapshots) and
[the prediction interface](../architecture/prediction-model-interface.md#snapshot-delivery)
for the implemented flow and contract.
