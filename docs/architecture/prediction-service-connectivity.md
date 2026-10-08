# Prediction Service Connectivity

Java sends one prepared model execution to Python over HTTP. The shared
[model interface](prediction-model-interface.md) defines the payloads; this guide
defines the implemented transport and how to exercise it.

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
| Python | `PREDICTION_SNAPSHOT_PATH` | Required path to one validated snapshot JSON file |
| Java | `PREDICTION_SERVICE_BASE_URL` | `http://127.0.0.1:8000` |
| Java | `PREDICTION_SERVICE_CONNECT_TIMEOUT` | `2s` |
| Java | `PREDICTION_SERVICE_REQUEST_TIMEOUT` | `30s` |

Java binds these through `prediction.service.base-url`, `connect-timeout`, and
`request-timeout` in `application.properties`. The URL must be an HTTP(S) root
address without credentials, a query, or a fragment. Timeouts must be positive.
The connection timeout applies when establishing a connection; the request
timeout is configured on each HTTP request.

Python resolves relative file paths against its working directory and does not
automatically load a `.env` file. Missing configuration, unreadable files, and
invalid snapshots prevent startup. Snapshots are loaded once; restart the
service after replacing data or changing configuration. The supplied fixture is
synthetic and identifies itself as `development-2025`, season `2025`.
Requests reference that ID, not the snapshot's path.

## Responses and failures

All execution responses use `application/json`. Parameters such as
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
Do not instantiate a client for every prediction. Starting the backend creates
the client but does not contact Python until it is called.

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

The HTTP test fixture configures the synthetic snapshot before importing the app.
No manual snapshot environment variable is needed for these tests.

## Local Java-to-Python smoke check

The following commands use a macOS/Linux shell and JDK 17. Install Python
development dependencies as described in the [prediction README](../../prediction/README.md).
Start Python from `prediction/`:

```sh
export PREDICTION_SNAPSHOT_PATH=tests/fixtures/season-2025.json
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
import com.ou.capstone.prediction.*;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;

var context = new SpringApplicationBuilder(BackendApplication.class).run("--server.port=0");
var client = context.getBean(PythonPredictionClient.class);
var matchup = new Matchup("b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a", "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", 2025, true);
var request = new ModelExecutionRequest(UUID.randomUUID().toString(), "coin-flip-v1", matchup, "development-2025", ExecutionKind.SIMULATION, null, 42, 10);
var response = client.execute(request);
System.out.println(response);
context.close();
```

Expect a `ModelExecutionResult` with 10 trials, seed 42, home probability 0.3,
away probability 0.7, and the away team selected with confidence 0.7. The returned
execution ID must equal the request ID. Python's access log should show
`POST /model-executions` with HTTP 200.

Before closing the context, optionally repeat with model ID `missing-model`;
expect a correlated `ModelExecutionError` with `NOT_FOUND` and HTTP 404 in
Python's log. The automated client tests cover refused connections, timeout,
malformed responses, and status mismatches reproducibly.

Exit JShell with `/exit` and stop Python with Ctrl+C. This check calls the
Spring-managed client without adding a frontend-facing controller.

## Remaining boundaries

Production ingestion, snapshot publication/refresh, additional model loading,
request orchestration, aggregation, and frontend integration are separate work.
The endpoint currently runs the explicitly registered coin-flip baseline.
Deterministic and stochastic response transport is covered by shared fixtures
and Java tests, not by additional installed production models.
