# GameSense Backend API

Java/Spring Boot service that sits between the frontend and the prediction service, handling application requests and coordinating application logic.

---

## Project Structure

```text
backend/
├── examples/                 # Standalone prediction orchestration demo
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/ou/capstone/
    │   │   ├── BackendApplication.java
    │   │   ├── controller/        # HealthController and GamesController
    │   │   ├── domain/            # Canonical Team, Game, Conference, Venue, GameStatus
    │   │   ├── data/              # Provider interfaces, GameFilter, snapshots, and in-memory storage
    │   │   ├── datasource/        # HTTP client for the current-season feed
    │   │   ├── ingestion/         # Source records, shared mapping, historical and current-season loaders
    │   │   └── prediction/
    │   │       ├── contract/      # Shared execution records and enums
    │   │       ├── client/        # Python HTTP transport, decoder, configuration
    │   │       ├── orchestration/ # Input validation, model coordination, outcomes
    │   │       └── prototype/     # Earlier standalone prediction validator
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/com/ou/capstone/  # Mirrors the production packages
        └── resources/             # Feed fixtures used by the ingestion tests
```

Historical datasets live outside the backend at `../data/historical/`; they are separate
from the small test fixtures in `src/test/resources/`. Shared API schemas live in
`../shared/schemas/`.

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

- JDK 17 (matching CI; set `JAVA_HOME` to your JDK 17 installation)
- Maven (or use the included wrapper, no local install required)

Check your Java version:

```bash
./mvnw -version
```

---

On macOS, select JDK 17 in the current terminal before running Maven:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

The JDK running Maven also runs Spotless. A newer incompatible JDK can cause formatter
errors involving `com.sun.tools.javac`, even if compilation targets Java 17.

## Building

Run the following commands from the `backend/` directory.

```bash
./mvnw clean verify
```

`verify` compiles the project, runs the test suite, and runs Checkstyle and Spotless against both production and test sources. Shared lint rules live in `config/checkstyle/checkstyle.xml`; the pinned formatter is configured in `pom.xml`.

---

## Running the Backend

Run from `backend/` so the historical file's relative path resolves correctly:

```bash
./mvnw spring-boot:run
```

By default, the service will run at:

```text
http://localhost:8080
```

---

Startup ingestion is enabled by default. `BackendApplication` implements `ApplicationRunner`
and loads the live current-season feed first, then `../data/historical/Schedule-2025.json`.
Both network access and that historical file are required. An ingestion error fails startup;
if the live fetch fails, the historical load is not attempted. Wait for startup to complete
before testing loaded results. The store is rebuilt on every restart and has no scheduled refresh.

To start without either data source:

```bash
GAMESENSE_INGESTION_ENABLED=false ./mvnw spring-boot:run
```

In that mode, `/games` returns `[]` until another caller loads data. Python is not required
for the health or games endpoints. Stop the backend with `Ctrl+C`.

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

## Current-Season Data Source

The backend can pull current-season college football data from the mentor-provided feed. `CurrentSeasonClient.fetchCurrentSeason()` calls the feed and returns a `SourceFeed` (teams, each with a day-by-day schedule).

- The `Source*` records (in `ingestion/source/`) mirror the external format exactly (every value is a raw string). They are shared by the current-season and historical paths and stay separate from the domain models; the shared mapper in `ingestion/` converts them into `Team`/`Game`. See [Data Ingestion and Retrieval](#data-ingestion-and-retrieval).
- The feed puts a small HTML stub in front of the JSON, so the client skips ahead to the first `{` before parsing.
- Any failure (unreachable, timeout, non-2xx status, invalid JSON, or a response with no teams) is thrown as a `DataSourceException`, so callers can handle it. The startup runner lets this exception propagate, failing startup.
- Loaded canonical games are exposed through `/games`. There is no HTTP endpoint to trigger ingestion or return the raw feed.

### Configuration

Set in `src/main/resources/application.properties`:

| Property                               | Default         | Description                                                                            |
| -------------------------------------- | --------------- | -------------------------------------------------------------------------------------- |
| `gamesense.ingestion.enabled`          | `true`          | Load live and historical data at startup; override with `GAMESENSE_INGESTION_ENABLED`. |
| `gamesense.datasource.url`             | mentor feed URL | Full URL of the data source.                                                           |
| `gamesense.datasource.connect-timeout` | `5s`            | How long to wait to connect.                                                           |
| `gamesense.datasource.read-timeout`    | `30s`           | How long to wait for the response. The feed is large, so keep this generous.           |

Override without changing code using environment variables, e.g. `GAMESENSE_DATASOURCE_URL=http://localhost:9000/feed ./mvnw spring-boot:run`.

Tests use `MockRestServiceServer` and a fixture in `src/test/resources/datasource/`, so they never call the live source.

---

## Data Ingestion and Retrieval

Two sources feed one in-memory store: historical schedule files and the current-season feed. Both go through the same `SourceFeedMapper`, which produces the canonical domain objects. Everything downstream reads them through `CanonicalDataProvider`, which extends the existing `PredictionDataProvider`, so consumers never see `Source*` records.

```java
// These calls are made by the startup runner when ingestion is enabled.
currentSeasonIngestionService.loadCurrentSeason();
historicalIngestionService.loadFile(Path.of("../data/historical/Schedule-2025.json"));

// Retrieval, regardless of which source supplied the data.
List<Game> games = canonicalDataProvider.findGames(2026);
List<Game> finals = canonicalDataProvider.findGames(
    new GameFilter(null, LocalDate.of(2025, 9, 6), GameStatus.FINAL, 2025));
Optional<Game> game = canonicalDataProvider.findGame(gameId);
Optional<Team> home = canonicalDataProvider.findTeam(games.get(0).homeTeamId());
```

- Team and game IDs are deterministic, so the same team or game keeps one identity across sources and reloads, and overlapping records collapse into one.
- Loading adds to the store and never discards other seasons. A final result is not replaced by an unfinished game. The store is in memory only.
- The feed provides dates without kickoff times. Mapping stores midnight UTC in `scheduledAt`; `week` is absent from the feed.
- `GamesController` parses HTTP parameters, `GameFilter` carries optional criteria, and the store applies them by scanning loaded games.
- Full design, rules, and limits: [Data Ingestion and Retrieval](../docs/architecture/data-ingestion.md).

---

## Running Tests

```bash
./mvnw test
```

---

Full-application tests set `gamesense.ingestion.enabled=false` so configuration and health
checks do not fetch live data or require historical files. Ingestion tests load fixtures
explicitly. `GamesControllerTest` uses standalone MockMvc and a local test provider to check
parameter binding, JSON responses, `404`, and invalid-input `400` responses.

For the games API and store tests only:

```bash
./mvnw -Dtest=GamesControllerTest,InMemoryCanonicalDataStoreTest test
```

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

- **Spring Boot Starter Web** — exposes the backend as an HTTP API.
- **Spring Boot Starter Test** — JUnit, AssertJ, and Spring test utilities.

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

### `GET /games`

Returns `200` with a JSON array of canonical games. No matches returns `[]`.
All parameters are optional; multiple parameters combine with AND.

| Parameter | Meaning                                                           |
| --------- | ----------------------------------------------------------------- |
| `teamId`  | Canonical team ID; matches home or away.                          |
| `date`    | UTC calendar date in `YYYY-MM-DD` format; time of day is ignored. |
| `status`  | `SCHEDULED`, `IN_PROGRESS`, `FINAL`, `POSTPONED`, or `CANCELLED`. |
| `season`  | Integer season year, such as `2025`.                              |

Malformed dates, unknown status values, and non-integer seasons return `400`.
An unknown team ID returns an empty array. There is no pagination or explicit sorting.
Games without a scheduled timestamp do not match a supplied date.

### `GET /games/{gameId}`

Returns `200` with one game object, or `404` when the canonical ID is not stored.
Use `id`, not `externalId`, from a listing response. Responses follow the
[shared game schema](../shared/schemas/game.schema.json); null fields are omitted.

### Try the games endpoints

Run these in another terminal after starting the backend. Replace `GAME_ID` and `TEAM_ID`
with IDs returned by the listing endpoint. Quote query URLs so the shell preserves `?` and `&`.

```bash
curl -i 'http://localhost:8080/games'
curl -i 'http://localhost:8080/games?date=2025-09-06&status=FINAL&season=2025'
curl -i 'http://localhost:8080/games?teamId=TEAM_ID&season=2025'
curl -i 'http://localhost:8080/games/GAME_ID'
curl -i 'http://localhost:8080/games/nonexistent'
```

For readable JSON, omit headers and pipe the body to a formatter:

```bash
curl -s 'http://localhost:8080/games?season=2025' | python3 -m json.tool
```

## Architecture

Spring creates one reusable `PythonPredictionClient`. Backend services inject it
and call `execute(ModelExecutionRequest)` to send a single execution to Python's
`POST /model-executions` endpoint. The client returns a validated
`PredictionServiceResponse` or throws a transport/invalid-response exception.
Starting the backend alone does not send a prediction request.

| Environment variable                 | Default                 |
| ------------------------------------ | ----------------------- |
| `PREDICTION_SERVICE_BASE_URL`        | `http://127.0.0.1:8000` |
| `PREDICTION_SERVICE_CONNECT_TIMEOUT` | `2s`                    |
| `PREDICTION_SERVICE_REQUEST_TIMEOUT` | `30s`                   |

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

Orchestration is disabled by default. The in-memory canonical store already implements
`PredictionDataProvider`. To enable orchestration, configure at least one model selection
and ensure the required data is loaded.
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
common controls fails startup. Orchestration configuration performs no model calls;
the separate startup ingestion runner loads game data by default.
The orchestrator requires canonical team/game identities and an existing snapshot
reference; the provider is responsible for team membership in that snapshot.
The selected snapshot must also be available to Python. Unknown game location
is rejected rather than inferred, and game status is not derived or changed.

Source integration, mapping, historical ingestion, and game-status handling live in
the ingestion packages. Orchestration tests supply canonical fixtures and a fake client.
There is no HTTP prediction endpoint, aggregation layer, or persistent storage yet.

### Orchestration example

The example predates the shared canonical store. It registers a second
`PredictionDataProvider`, which can cause ambiguous injection alongside the in-memory
store. Its provider wiring needs updating before treating it as a working smoke test.
The commands below document its intended execution; disabling ingestion avoids external
loading but does not resolve the duplicate-provider wiring.

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
GAMESENSE_INGESTION_ENABLED=false PREDICTION_SERVICE_BASE_URL=http://127.0.0.1:8000 java --class-path "target/classes:$(cat target/smoke-classpath.txt)" examples/RunOrchestration.java
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
