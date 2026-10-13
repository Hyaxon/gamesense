# GameSense Backend API

Java/Spring Boot service that sits between the frontend and the prediction service, handling application requests and coordinating application logic.

---

## Project Structure

```text
backend/
├── pom.xml
├── mvnw / mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ou/capstone/
│   │   │       ├── BackendApplication.java
│   │   │       ├── controller/       # HTTP endpoints
│   │   │       ├── domain/           # Canonical Team, Game, Venue, GameStatus
│   │   │       ├── data/             # Canonical provider and snapshot reference
│   │   │       └── prediction/
│   │   │           ├── contract/     # Shared execution records and enums
│   │   │           ├── client/       # Python HTTP transport, decoder, configuration
│   │   │           ├── orchestration/ # Input validation, model coordination, outcomes
│   │   │           └── prototype/    # Earlier standalone prediction validator
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/ou/capstone/
│               ├── controller/
│               └── prediction/      # Mirrors production subpackages
```

### `src/main/java/com/ou/capstone/`

Main source code directory. Packages group related responsibilities. Within
`prediction/`, start with `orchestration/PredictionOrchestrator.java` for the
application flow, `client/PythonPredictionClient.java` for HTTP communication,
and `contract/ModelExecutionRequest.java` for the outgoing payload. Shared response
records and enums stay together in `contract/`; client exceptions stay with their
transport code in `client/`. Tests mirror these packages.

### `src/test/java/com/ou/capstone/`

JUnit tests that mirror the `main/` package structure. New tests should be written for every feature added to `src/main`.

---

## Requirements

* JDK 17 (matching CI; set `JAVA_HOME` to your JDK 17 installation)
* Maven (or use the included wrapper, no local install required)

Check your Java version:

```bash
java -version
```

---

## Building

Run the following commands from the `backend/` directory.

```bash
./mvnw clean verify
```

`verify` compiles the project, runs the test suite, and runs Checkstyle and Spotless against both production and test sources. Shared lint rules live in `config/checkstyle/checkstyle.xml`; the pinned formatter is configured in `pom.xml`.

---

## Running the Backend

```bash
./mvnw spring-boot:run
```

By default, the service will run at:

```text
http://localhost:8080
```

---

## Health Check

The service exposes a basic health endpoint used to verify that the application is running.

```text
GET /health
```

Open:

```text
http://localhost:8080/health
```

Expected response:

```json
{
  "status": "ok"
}
```

---

## Running Tests

```bash
./mvnw test
```

---

## Linting / Formatting

This project shares a Checkstyle configuration with the rest of the Java code in this repo, located at `config/checkstyle/checkstyle.xml`. It enforces the naming conventions from the team's Code Formatting Standards doc (PascalCase types, camelCase methods/variables, SCREAMING_SNAKE_CASE constants, lowercase packages).

```bash
./mvnw checkstyle:check
```

Check or apply Google Java Format using Spotless:

```bash
./mvnw spotless:check
./mvnw spotless:apply
```

Both Checkstyle and Spotless checks also run automatically as part of `./mvnw verify`. On Windows, use `mvnw.cmd`.

---

## Development Validation

Before submitting changes, run:

```bash
./mvnw clean verify
```

This should pass (tests + Checkstyle + Spotless) before opening or updating a pull request.

---

## Dependencies

Dependencies are defined in `pom.xml` and managed by the Spring Boot parent POM.

* **Spring Boot Starter Web** — exposes the backend as an HTTP API.
* **Spring Boot Starter Test** — JUnit, AssertJ, and Spring test utilities.

---

## API

### `GET /health`

Used to verify that the backend service is available.

**Response**

```json
{
  "status": "ok"
}
```

---

## Architecture

Spring creates one reusable `PythonPredictionClient`. Backend services inject it
and call `execute(ModelExecutionRequest)` to send a single execution to Python's
`POST /model-executions` endpoint. The client returns a validated
`PredictionServiceResponse` or throws a transport/invalid-response exception.
Starting the backend alone does not send a prediction request.

| Environment variable | Default |
| --- | --- |
| `PREDICTION_SERVICE_BASE_URL` | `http://127.0.0.1:8000` |
| `PREDICTION_SERVICE_CONNECT_TIMEOUT` | `2s` |
| `PREDICTION_SERVICE_REQUEST_TIMEOUT` | `30s` |

Set overrides in the backend process environment. Spring binds them through
`prediction.service.*` in `application.properties`. The URL must be an HTTP(S)
root address; timeouts must be positive.

The existing `prediction.prototype` types `Prediction` and `PredictionValidator` are prototype
types, not the Java/Python wire contract. Model selection, game-data retrieval,
aggregation, and a frontend prediction controller are not part of this client.

See the [connectivity guide](../docs/architecture/prediction-service-connectivity.md)
for constructor injection, response/error handling, automated coverage, and a
reproducible local Java-to-Python smoke check. Client tests run a local test server
and do not require Python.

---

## Prediction orchestration

`PredictionOrchestrator` retrieves canonical inputs through `PredictionDataProvider`,
validates them, and runs the configured models sequentially through the existing
client. Backend callers use `predictGame(gameId)` or `predictMatchup(matchup)`.
Both return a backend-only `PredictionOrchestrationResult` containing complete
successes and categorized failures in execution order. Repeated model IDs retain
separate execution IDs. Independent failures do not discard successes; an all-failed
request returns its failures. Interruption aborts orchestration and preserves the
thread interrupt flag. Requests are not automatically retried.

Orchestration is disabled by default, allowing the backend to start before a
production canonical data provider exists. To enable it, supply a Spring bean
implementing `PredictionDataProvider` and configure at least one model selection.
For example, in a local Spring properties file:

```properties
prediction.orchestration.enabled=true
prediction.orchestration.models[0].model-id=coin-flip-v1
prediction.orchestration.models[0].execution-kind=SIMULATION
prediction.orchestration.models[0].trials=1000
prediction.orchestration.models[0].seed=42
```

The enable flag can also be set with `PREDICTION_ORCHESTRATION_ENABLED=true`.
Add entries at indices `[1]`, `[2]`, etc. for additional executions. Model IDs must
be registered in Python. The current Python startup registers only coin flip;
Java configuration does not install adapters or discover their capabilities.

Optional `configuration-json` supplies a JSON object of model-specific settings.
For a model that supports such a setting, an example is
`prediction.orchestration.models[0].configuration-json={"homeAdvantage":0}`.
JSON preserves numbers, booleans, nested arrays/objects, and configuration nulls;
omit the property entirely when no configuration is needed. Coin flip accepts
only absent or empty configuration. Common `seed` and `trials` remain separate
properties. Python validates each model's configuration schema and capabilities.

Enabling orchestration without a provider, with no selections, or with invalid
common controls fails startup. Startup performs no data lookups or model calls.
The orchestrator requires canonical team/game identities and an existing snapshot
reference; the provider is responsible for team membership in that snapshot.
The selected snapshot must also be available to Python. Unknown game location
is rejected rather than inferred, and game status is not derived or changed.

Production source integration, mapping, historical ingestion, and game-status
handling belong to their separate tickets. Tests supply canonical fixtures and a
fake client. No frontend endpoint, aggregation, or persistence is introduced.

### Run the orchestration example

[RunOrchestration.java](examples/RunOrchestration.java) supplies explicit canonical
fixture records and retrieves the real Spring-managed orchestrator. It runs two
seeded coin-flip executions with an intentionally missing model between them,
demonstrating partial failure and repeated model IDs. It exercises both a custom
matchup and the scheduled game in the synthetic snapshot. This is a standalone
example, not a production data provider or ingestion implementation.

In one terminal, from `prediction/`, start Python:

```sh
PREDICTION_SNAPSHOT_PATH=tests/fixtures/season-2025.json .venv/bin/python -m uvicorn gamesense_prediction.main:app --host 127.0.0.1 --port 8000
```

In another terminal, from `backend/`, with JDK 17 selected:

```sh
./mvnw --batch-mode compile dependency:build-classpath -Dmdep.outputFile=target/smoke-classpath.txt
PREDICTION_SERVICE_BASE_URL=http://127.0.0.1:8000 java --class-path "target/classes:$(cat target/smoke-classpath.txt)" examples/RunOrchestration.java
```

The Java source-file launcher compiles and runs the standalone example. It starts
Spring without an HTTP server and registers a demo-only canonical provider.
The example supplies its own enable flag and model selections; the service URL
still comes from normal backend configuration. Nothing is persisted.

For each matchup, expect two successes with home probability `0.3`, away
probability `0.7`, and away winner confidence `0.7`, plus a `MODEL_ERROR` with
`NOT_FOUND` for `demo-missing-model`. Every execution has a distinct ID. The example
checks these outcomes and exits unsuccessfully if they do not match; a passing run
ends with `PASS`. Python should log six execution requests: `200`, `404`, `200`
for each matchup. Stop the Python process with Ctrl+C afterward.
