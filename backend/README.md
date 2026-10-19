# GameSense Backend API

Java/Spring Boot service that sits between the frontend and the prediction service, handling application requests and coordinating application logic.

## Schedule ingestion and HTTP prediction demo

This demo reads the root `Schedule.json`, runs the existing historical ingestion
pipeline, and serves all six implemented models through the Java backend. Use `curl` to list
games and submit a model/game request. It requires JDK 17, the Python environment
described in [prediction/README.md](../prediction/README.md), and `jq` for the CLI
examples below.

The flow is:

```text
Schedule.json -> HistoricalIngestionService -> canonical teams and games
  -> dataset.json (Java provider and candidate games)
  -> snapshot.json (Python's completed-game history)

curl -> POST /demo/predictions -> PredictionOrchestrator
     -> PythonPredictionClient -> Python POST /model-executions -> selected model -> JSON
```

The source contains completed 2025-season games, including January 2026. For a
historical replay, choose a cutoff: only games strictly before that UTC date go
into model history. Games on or after the cutoff can be requested. Their actual
scores stay out of the Python snapshot and prediction request. All predictions
use this fixed cutoff; history does not advance when requesting a later game.

**1. Ingest once.** From `backend/`, with JDK 17 selected (on macOS:
`export JAVA_HOME=$(/usr/libexec/java_home -v 17)`):

```sh
./mvnw --batch-mode compile dependency:build-classpath -Dmdep.outputFile=target/smoke-classpath.txt
"$JAVA_HOME/bin/java" --class-path "target/classes:$(cat target/smoke-classpath.txt)" \
  examples/IngestSchedule.java ../Schedule.json target/schedule-demo 2025 2025-11-15
```

The command prints counts and writes `target/schedule-demo/dataset.json` and
`target/schedule-demo/snapshot.json`. Skipped-row reasons are in `dataset.json`.
The existing ingester ignores BYE rows, deduplicates opposing schedule entries,
maps locations/scores, and skips opponents absent from the feed. There is no
database. The generated files retain the canonical IDs between processes.

The output directory must be new. To re-ingest, choose a new directory and point
both services to it, then restart them. Re-ingestion generates new team, game,
and snapshot IDs. Do not run `mvn clean` during the demo: artifacts are in `target/`.

**2. Start the Python demo service.** In a second terminal, from `prediction/`:

```sh
PREDICTION_SNAPSHOT_PATH=../backend/target/schedule-demo/snapshot.json \
  .venv/bin/python -m uvicorn schedule_demo_app:app --app-dir examples \
  --host 127.0.0.1 --port 8000
```

This example registers `elo-v1`, `glicko2`, `bradley-terry-v1`, and `xgboost-v1`
for deterministic execution, plus `monte-carlo-v1` and `coin-flip-v1` for simulation.
Simulations use 10,000 trials and seed 42. XGBoost trains once during startup on
the exported pre-cutoff snapshot, then reuses that model for requests. The snapshot
must contain enough prior history to train on both home wins and away wins.
The usual
`gamesense_prediction.main:app` still registers only coin flip; use the demo
entry point above. Coin flip is a random baseline; the other models use history.
See [demo.md](../demo.md) for direct commands for every model.

**3. Start the Java backend.** In the first terminal, from `backend/`:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=schedule-demo \
  -Dspring-boot.run.arguments="--server.address=127.0.0.1 --demo.data-directory=target/schedule-demo"
```

The demo controller creates an orchestrator for the requested model using the
Spring-managed HTTP client and file-backed demo provider. Leave the global
`prediction.orchestration.enabled` setting at its default `false`; the demo
supplies its own model selection per request. These endpoints exist only when
the `schedule-demo` profile is active.

**4. Request a prediction from the CLI.** In another terminal:

```sh
curl -fsS http://127.0.0.1:8080/demo/models | jq
curl -fsS 'http://127.0.0.1:8080/demo/games?team=Oklahoma%20Sooners' | jq

GAME_ID=$(curl -fsS 'http://127.0.0.1:8080/demo/games?team=Oklahoma%20Sooners' | jq -er '.[0].gameId')

jq -n --arg gameId "$GAME_ID" '{modelId: "elo-v1", gameId: $gameId}' \
  | curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
      -H 'Content-Type: application/json' --data-binary @- \
  | jq
```

The request body is `{"modelId":"elo-v1","gameId":"<ID from /demo/games>"}`.
Omit the `team` query to list all candidate games; filtering uses an exact team
name, ignoring case. The response includes readable team/winner names, the
history cutoff, and the complete orchestration result. Winner confidence is at
`execution.outcomes[0].result.prediction.confidence`; model-specific diagnostics are in
`execution.outcomes[0].result.supportingScores` when provided. For a compact terminal display,
pipe the prediction response through:

```sh
jq '{homeTeam, awayTeam, predictedWinner, historyBefore,
     confidence: .execution.outcomes[0].result.prediction.confidence}'
```

Use any `modelId` from `GET /demo/models` with the same game; no re-ingestion is
needed to switch models. The model list also shows execution kind, seed, and trials.
Invalid input returns HTTP 400,
an unavailable/earlier game returns 404, and a Python/model failure returns 502
with the categorized outcome in the response. Check both services' `/health`
endpoints if a request fails. Stop both servers with Ctrl+C after the demo.

The history cutoff prevents later scores from entering this replay; this is not
an accuracy evaluation. The existing Elo implementation rebuilds ratings from
the two selected teams' combined histories, rather than the whole league's
history. Glicko-2 processes all completed snapshot games. The demo preserves
these model behaviors. Restart both services after changing model registration.

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
│   │   │       ├── ingestion/historical/ # Schedule JSON reader and canonical mappers
│   │   │       ├── demo/             # Opt-in schedule provider and HTTP demo
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
