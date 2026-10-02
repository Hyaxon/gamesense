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

Main source code directory. `controller/` holds the HTTP endpoints; future application logic (services, models, data access, etc.) should be added under their own sub-packages here as it's implemented.

### `src/test/java/com/ou/capstone/`

JUnit tests that mirror the `main/` package structure. New tests should be written for every feature added to `src/main`.

---

## Requirements

* JDK 17 or newer
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

`verify` compiles the project, runs the test suite, and runs Checkstyle against the shared style rules in `config/checkstyle/checkstyle.xml`.

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

This also runs automatically as part of `./mvnw verify`.

---

## Development Validation

Before submitting changes, run:

```bash
./mvnw clean verify
```

This should pass (tests + Checkstyle) before opening or updating a pull request.

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
