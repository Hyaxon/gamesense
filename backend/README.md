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
│   │   │       ├── controller/
│   │   │       │   └── HealthController.java
│   │   │       └── datasource/
│   │   │           ├── CurrentSeasonClient.java
│   │   │           ├── CurrentSeasonProperties.java
│   │   │           ├── DataSourceConfig.java
│   │   │           ├── DataSourceException.java
│   │   │           ├── SourceFeed.java
│   │   │           ├── SourceScheduleEntry.java
│   │   │           └── SourceTeam.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/ou/capstone/
│               ├── controller/
│               │   └── HealthControllerTest.java
│               └── datasource/
│                   ├── CurrentSeasonClientTest.java
│                   └── CurrentSeasonPropertiesTest.java
```

### `src/main/java/com/ou/capstone/`

Main source code directory. `controller/` holds the HTTP endpoints; future application logic (services, models, data access, etc.) should be added under their own sub-packages here as it's implemented.

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

## Current-Season Data Source

The backend can pull current-season college football data from the mentor-provided feed. `CurrentSeasonClient.fetchCurrentSeason()` calls the feed and returns a `SourceFeed` (teams, each with a day-by-day schedule).

* The `Source*` records mirror the external format exactly (every value is a raw string). They are intentionally separate from our internal domain models; converting them into `Team`/`Game` is the job of the normalization step, not this client.
* Any failure (unreachable, timeout, non-2xx status, invalid JSON, or a response with no teams) is thrown as a `DataSourceException`, so callers can handle it without the app crashing.
* This is **not exposed through an HTTP endpoint yet**. It is only available to other backend code until a feature needs it.

### Configuration

Set in `src/main/resources/application.properties`:

| Property | Default | Description |
| --- | --- | --- |
| `gamesense.datasource.url` | mentor feed URL | Full URL of the data source. |
| `gamesense.datasource.connect-timeout` | `5s` | How long to wait to connect. |
| `gamesense.datasource.read-timeout` | `30s` | How long to wait for the response. The feed is large, so keep this generous. |

Override without changing code using environment variables, e.g. `GAMESENSE_DATASOURCE_URL=http://localhost:9000/feed ./mvnw spring-boot:run`.

Tests use `MockRestServiceServer` and a fixture in `src/test/resources/datasource/`, so they never call the live source.

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

> Add a short explanation here describing how this Java backend communicates with the frontend and the Python prediction service as those integrations are implemented.

---

## Notes
