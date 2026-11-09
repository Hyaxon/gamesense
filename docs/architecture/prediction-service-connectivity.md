# Prediction Service Connectivity

Java publishes immutable season snapshots and sends prepared model executions to Python over HTTP. The shared
[model interface](prediction-model-interface.md) defines the payloads; this guide
defines the implemented transport and how to exercise it.

## Snapshot publication

Start Python before the backend. With startup ingestion enabled, Java loads the live
feed and historical data, builds a snapshot for each season represented in the live
feed, and calls `POST /snapshots`. The payload contains `season`, `provenance`,
`teams`, and `games`, with optional `ratings`; callers must omit `dataSnapshotId`.
Python validates the whole dataset, stores it, and returns HTTP 200 with
`{"dataSnapshotId": "season-2026-<uuid>"}`. Java stores that returned ID by season
and uses it in subsequent execution requests.

A malformed record rejects the entire publication. Publish failures are logged
without stopping backend startup or game browsing; ingestion failures still stop
startup. Loading games alone does not create a snapshot reference. Publication
does not automatically execute a prediction.

Python exposes `GET /health`, `POST /snapshots`, and `POST /model-executions`.

## Execution flow

1. A backend caller supplies a complete `ModelExecutionRequest`.
2. Spring's reusable `PythonPredictionClient` serializes it and sends
   `POST /model-executions`.
3. Python's `PredictionRunner` validates the request, retrieves the registered
   adapter and snapshot, executes the model, and validates its result.
4. Python serializes the result with `.to_wire()` and selects an HTTP status.
5. Java checks content type, decodes and validates the body against the original
   request, and verifies that the HTTP status agrees with the response.

The client uses Java 17's HTTP client and Jackson. It does not select models,
retrieve games, coordinate several executions, aggregate predictions, or retry
failed requests automatically. Multiple models require separate client calls.
There is no backend prediction controller or automatic execution trigger yet.

## Startup and configuration

Python currently registers `coin-flip-v1` explicitly in `main.py`, with method
`RANDOM`, simulation support, and seed support. It constructs the registry,
snapshot provider, and runner once when the module is imported. Adding another
adapter requires trusted startup registration; request fields cannot select
Python modules or filesystem paths. Model-selection configuration is not yet
externalized.

| Service | Environment variable | Default / requirement |
| --- | --- | --- |
| Python | `PREDICTION_SNAPSHOT_PATH` | Optional path to one validated snapshot file; unset or empty starts an empty provider |
| Java | `PREDICTION_SERVICE_BASE_URL` | `http://127.0.0.1:8000` |
| Java | `PREDICTION_SERVICE_CONNECT_TIMEOUT` | `2s` |
| Java | `PREDICTION_SERVICE_REQUEST_TIMEOUT` | `30s` |
| Java | `PREDICTION_SNAPSHOT_PUBLISH_ENABLED` | `true`; publish after startup ingestion |
| Java | `PREDICTION_SNAPSHOT_FALLBACK_SEASON` | `0` (disabled); configure with fallback ID |
| Java | `PREDICTION_SNAPSHOT_FALLBACK_ID` | Empty (disabled); configure with a positive season |
| Java | `GAMESENSE_INGESTION_ENABLED` | `true`; disabling skips startup ingestion and publication |

Java binds service connection settings through `prediction.service.base-url`, `connect-timeout`, and
`request-timeout` in `application.properties`. The URL must be an HTTP(S) root
address without credentials, a query, or a fragment. Timeouts must be positive.
The connection timeout applies when establishing a connection; the request
timeout is configured on each HTTP request.

Python resolves relative file paths against its working directory and does not
automatically load a `.env` file. An unset or empty `PREDICTION_SNAPSHOT_PATH`
starts an empty provider that can accept HTTP publications. An explicitly configured
unreadable file or invalid snapshot still prevents startup. Files are loaded once;
restart Python to reload a changed file. File and HTTP snapshots can coexist.

For file-based development, Python can load `tests/fixtures/season-2025.json`,
whose ID is `development-2025`. Java can use that ID through
`PREDICTION_SNAPSHOT_FALLBACK_SEASON=2025` and
`PREDICTION_SNAPSHOT_FALLBACK_ID=development-2025`. These bind to
`prediction.snapshot.fallback-season` and `prediction.snapshot.fallback-id`.
Java's canonical teams and games must match the file; fallback settings do not load
that dataset into Java. Set `PREDICTION_SNAPSHOT_PUBLISH_ENABLED=false`
(`prediction.snapshot.publish.enabled`) to use file-based data without publishing.
A successfully published ID takes precedence over the fallback for its season.

Snapshots are stored in Python's process memory; run a single worker. Repeated
publication creates new IDs even for identical data, and older versions remain
until Python restarts. Restarting Python loses HTTP-published snapshots while Java
may still hold their IDs, causing `NOT_FOUND`. Restart Java after Python to republish;
automatic recovery, refresh, persistence, and eviction are not implemented.

## Responses and failures

Publication and execution responses use `application/json`. Parameters such as
`charset=utf-8` are accepted by Java.

| Outcome / error code | HTTP status | Java response |
| --- | --- | --- |
| Successful execution | 200 | `ModelExecutionResult` |
| `VALIDATION_ERROR` | 400 | `ModelExecutionError` or plain `ErrorResponse` |
| `NOT_FOUND` | 404 | `ModelExecutionError` |
| `UNSUPPORTED_METHOD` | 422 | `ModelExecutionError` |
| `PREDICTION_FAILED` | 500 | `ModelExecutionError` |
| `INTERNAL_ERROR` | 500 | `ModelExecutionError` |

A correlated model error contains `status: ERROR`, `executionId`, `modelId`,
and a nested `error`. Requests rejected before a valid execution identity exists
receive the plain shared error object. FastAPI request-validation failures also
use that plain format. This policy covers the execution endpoint; framework
errors such as an unknown URL are not execution results.

`PredictionServiceResponse` is the common Java interface for the three response
records. A valid model failure is returned, allowing later orchestration to
retain partial success across separate requests.

For publication, `publishSnapshot(...)` returns the ID on HTTP 200. HTTP 400
`VALIDATION_ERROR` uses a plain `ErrorResponse`, including malformed JSON,
schema/semantic failures, caller-supplied IDs, and generated-ID collisions.
A valid error response with a matching HTTP status throws
`SnapshotPublicationRejectedException`, preserving the error code and message.
Publication errors have no execution correlation fields.

Client failures are separate:

- `PredictionTransportException`: reason `TIMEOUT`, `CONNECTION_FAILURE`, or
  `INTERRUPTED`. Interrupted execution restores the thread's interrupt flag.
- `InvalidPredictionResponseException`: invalid JSON, content type, status,
  contract fields, correlation, or shared semantic relationships.
- `IOException` from request serialization: a local JSON-writing failure,
  before the transport operation.

The decoder preserves model version, timestamps, seed, simulation details,
configuration, and supporting scores. It rejects unknown properties, missing
required fields, numeric coercions, and inconsistent winner/probability data.
Model-specific configuration schemas and adapter capabilities remain owned by
Python; Java does not download the schema identified by a descriptor.

## Use from a backend service

Inject the configured client through the consuming service's constructor:

```java
private final PythonPredictionClient predictionClient;

public SomeService(PythonPredictionClient predictionClient) {
  this.predictionClient = predictionClient;
}
```

When that service has prepared an execution request, call
`predictionClient.execute(request)` and handle its typed response or exceptions.
Do not instantiate a client for every prediction. The startup runner calls
`SnapshotPublicationService.publishSeason(season)` after ingestion; that service
uses `PredictionSnapshotBuilder`, calls `predictionClient.publishSnapshot(payload)`,
and records the returned ID in the canonical store. This contacts Python by default,
even when prediction orchestration is disabled.

## Automated checks

From `backend/`, with JDK 17:

```sh
./mvnw --batch-mode verify
```

The Java suite checks shared request/result examples, decoder rejection cases,
configuration binding, and HTTP behavior against a temporary loopback server.
It needs permission to bind local ports, but not a running Python service.

From `prediction/`, with development dependencies installed:

```sh
.venv/bin/python -m pytest
.venv/bin/python -m ruff check .
.venv/bin/python -m ruff format --check .
```

The tests cover file loading, startup without a snapshot file, publication, and
execution against published IDs. No manual snapshot environment variable is needed.

## Local Java-to-Python smoke check

The following commands use a macOS/Linux shell and JDK 17. Install Python
development dependencies as described in the [prediction README](../../prediction/README.md).
This check publishes a synthetic dataset explicitly, so it needs no external sports
feed or startup snapshot file. Start Python from `prediction/`:

```sh
unset PREDICTION_SNAPSHOT_PATH
.venv/bin/python -m uvicorn gamesense_prediction.main:app --port 8000
```

In a second terminal, from `backend/`, compile Java and prepare a runtime
classpath. This writes only build artifacts under `target/`:

```sh
./mvnw --batch-mode compile dependency:build-classpath -Dmdep.outputFile=target/smoke-classpath.txt
export PREDICTION_SERVICE_BASE_URL=http://127.0.0.1:8000
jshell --class-path "target/classes:$(cat target/smoke-classpath.txt)"
```

Paste these statements into JShell:

```java
import com.ou.capstone.BackendApplication;
import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.*;
import com.ou.capstone.prediction.snapshot.SnapshotPublicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Arrays;
import com.ou.capstone.prediction.client.*;
import com.ou.capstone.prediction.contract.*;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;

var context = new SpringApplicationBuilder(BackendApplication.class).run("--server.port=0", "--gamesense.ingestion.enabled=false", "--prediction.snapshot.publish.enabled=true");
var mapper = context.getBean(ObjectMapper.class);
var fixture = mapper.readTree(Files.readString(Path.of("../prediction/tests/fixtures/season-2025.json")));
var store = context.getBean(InMemoryCanonicalDataStore.class);
store.load(Arrays.asList(mapper.treeToValue(fixture.get("teams"), Team[].class)), Arrays.asList(mapper.treeToValue(fixture.get("games"), Game[].class)));
context.getBean(SnapshotPublicationService.class).publishSeason(2025);
var snapshotId = store.findSnapshot(2025).orElseThrow().dataSnapshotId();
var client = context.getBean(PythonPredictionClient.class);
var matchup = new Matchup("b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a", "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", 2025, true);
var request = new ModelExecutionRequest(UUID.randomUUID().toString(), "coin-flip-v1", matchup, snapshotId, ExecutionKind.SIMULATION, null, 42, 10);
var response = client.execute(request);
System.out.println(response);
context.close();
```

Expect a `ModelExecutionResult` with 10 trials, seed 42, home probability 0.3,
away probability 0.7, and the away team selected with confidence 0.7. The returned
execution ID must equal the request ID. Python's access log should show
`POST /snapshots` followed by `POST /model-executions`, both with HTTP 200.

Before closing the context, optionally repeat with model ID `missing-model`;
expect a correlated `ModelExecutionError` with `NOT_FOUND` and HTTP 404 in
Python's log. The automated client tests cover refused connections, timeout,
malformed responses, and status mismatches reproducibly.

Exit JShell with `/exit` and stop Python with Ctrl+C. This check calls the
Spring-managed client without adding a frontend-facing controller.

## Remaining boundaries

Startup ingestion and snapshot publication are implemented, and backend orchestration
is available when configured. Automatic refresh and restart recovery, persistent
snapshots, additional model loading, aggregation, and frontend integration remain
separate work.
The endpoint currently runs the explicitly registered coin-flip baseline.
Deterministic and stochastic response transport is covered by shared fixtures
and Java tests, not by additional installed production models.
