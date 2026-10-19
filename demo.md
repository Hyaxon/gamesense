# GameSense demo

## What works today

- **Java backend:** ingests `Schedule.json`, resolves teams and games, coordinates predictions, and serves HTTP responses.
- **Python prediction service:** validates requests and runs six prediction models.
- **React frontend:** navigation and Games page layout. It runs independently and is not connected to the prediction endpoints yet.
- **Shared schemas:** define the JSON exchanged between services.

```text
Schedule.json -> Java ingestion -> dataset.json + snapshot.json

curl -> Java /demo/predictions -> PredictionOrchestrator
     -> PythonPredictionClient -> Python /model-executions
     -> PredictionRunner -> registered model adapter -> model algorithm
     -> prediction result -> Java -> JSON in your terminal
```

Java and Python use the same exported team and snapshot IDs. Python reads the
snapshot at startup. Each adapter supplies its algorithm with historical data
and returns a common prediction format.

| Model         | Request modelId    | Execution                                        |
| ------------- | ------------------ | ------------------------------------------------ |
| Elo           | `elo-v1`           | Deterministic                                    |
| Glicko-2      | `glicko2`          | Deterministic                                    |
| Bradley-Terry | `bradley-terry-v1` | Deterministic                                    |
| Monte Carlo   | `monte-carlo-v1`   | 10,000 trials, seed 42                           |
| Coin flip     | `coin-flip-v1`     | 10,000 trials, seed 42                           |
| XGBoost       | `xgboost-v1`       | Trained once at startup; deterministic inference |

The demo uses results strictly before **November 15, 2025** to predict games on
or after that date. XGBoost trains on that same historical snapshot. Coin flip
is a random baseline; the other models use game history. This demonstrates
execution, not prediction accuracy.

## 1. Install, build, and ingest — terminal A

Start in the repository root. These are macOS commands; you need JDK 17,
Python 3.12+, `curl`, and `jq`. Stop any previous demo servers first.

```sh
cd prediction
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[dev]'

cd ../backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw clean compile dependency:build-classpath -Dmdep.outputFile=target/smoke-classpath.txt

"$JAVA_HOME/bin/java" --class-path "target/classes:$(cat target/smoke-classpath.txt)" examples/IngestSchedule.java ../Schedule.json target/schedule-demo 2025 2025-11-15
```

This rebuilds the backend and regenerates the demo data in
`backend/target/schedule-demo/`: 265 teams, 1,238 historical games, and 387 games
available to predict. The ingester reports 62 skipped rows with missing opponents.
The `clean` command removes previous generated backend artifacts.

## 2. Start Python — terminal B

Start this terminal in the repository root:

```sh
cd prediction
export PREDICTION_SNAPSHOT_PATH=../backend/target/schedule-demo/snapshot.json
.venv/bin/python -m uvicorn schedule_demo_app:app --app-dir examples --host 127.0.0.1 --port 8000
```

Leave it running. All six models are registered. XGBoost training finishes before
the service accepts requests; requests reuse the trained model.

## 3. Start Java — terminal A

Terminal A is still in `backend/`:

```sh
export PREDICTION_SERVICE_BASE_URL=http://127.0.0.1:8000
export PREDICTION_ORCHESTRATION_ENABLED=false
./mvnw spring-boot:run -Dspring-boot.run.profiles=schedule-demo -Dspring-boot.run.arguments="--server.address=127.0.0.1 --server.port=8080 --demo.data-directory=target/schedule-demo"
```

Leave it running. The demo selects the execution settings for each model.

## 4. List models and choose a game — terminal C

```sh
curl -fsS http://127.0.0.1:8000/health | jq
curl -fsS http://127.0.0.1:8080/health | jq
curl -fsS http://127.0.0.1:8080/demo/models | jq
curl -fsS 'http://127.0.0.1:8080/demo/games?team=Oklahoma%20Sooners' | jq

GAME_ID=$(curl -fsS 'http://127.0.0.1:8080/demo/games?team=Oklahoma%20Sooners' | jq -er '.[0].gameId')
```

This selects Oklahoma at Alabama on November 15, 2025. Run the following examples
in this same terminal so they share `GAME_ID`.

## 5. Run predictions — terminal C

**Elo:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"elo-v1\",\"gameId\":\"$GAME_ID\"}" | jq
```

**Glicko-2:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"glicko2\",\"gameId\":\"$GAME_ID\"}" | jq
```

**Bradley-Terry:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"bradley-terry-v1\",\"gameId\":\"$GAME_ID\"}" | jq
```

**Monte Carlo:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"monte-carlo-v1\",\"gameId\":\"$GAME_ID\"}" | jq
```

**Coin flip:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"coin-flip-v1\",\"gameId\":\"$GAME_ID\"}" | jq
```

**XGBoost:**

```sh
curl -sS --fail-with-body http://127.0.0.1:8080/demo/predictions \
  -H 'Content-Type: application/json' \
  -d "{\"modelId\":\"xgboost-v1\",\"gameId\":\"$GAME_ID\"}" | jq
```

Each response includes `predictedWinner` and
`execution.outcomes[0].result.prediction.confidence`. Simulation models also
return home/away win probabilities and trial counts. Monte Carlo includes
predicted scores. Confidence is the selected winner's probability.

Press `Ctrl+C` in terminals A and B to stop the services.

## Frontend by itself — another terminal

From the repository root, with Node.js 22.12+ installed:

```sh
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173` (or the URL Vite prints). Show the Games layout,
navigation, and responsive resizing. The other routes currently have blank
content areas. Neither backend service is required for this frontend preview;
the HTTP prediction examples above are demonstrated separately.

Press `Ctrl+C` to stop the frontend.
