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
│   │   │       └── controller/
│   │   │           └── HealthController.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/ou/capstone/
│               └── controller/
│                   └── HealthControllerTest.java
```

### `src/main/java/com/ou/capstone/`

Main source code directory. `controller/` holds HTTP endpoints. `prediction/` contains the Python HTTP client, execution-contract records, strict response decoder, and Spring configuration.

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

The existing root-package `Prediction` and `PredictionValidator` are prototype
types, not the Java/Python wire contract. Model selection, game-data retrieval,
aggregation, and a frontend prediction controller are not part of this client.

See the [connectivity guide](../docs/architecture/prediction-service-connectivity.md)
for constructor injection, response/error handling, automated coverage, and a
reproducible local Java-to-Python smoke check. Client tests run a local test server
and do not require Python.

---

## Notes
