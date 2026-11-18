# Historical Backtesting

The backtesting framework replays completed games through the prediction models and records how often each model picked the real winner. Its main job is to guarantee that a model never sees the result it is being scored on.

The code is in `prediction/src/gamesense_prediction/backtesting/`. It runs on the Python prediction service and reuses the normal `PredictionRunner`, so a backtest exercises the same validation, registry, and models as a live request.

## How a backtest works

For every game in the chosen range, the backtester:

1. Builds a **pre-game context** that holds only the games played strictly before that game.
2. Runs each chosen model on the game through `PredictionRunner`, as an ordinary request.
3. Pairs the response with the real result and stores a `PredictionRecord`.
4. Rolls the records up into one `ModelSummary` per model.

Every model sees the same pre-game context for a given game, so models are compared on equal terms.

## No future data

The rules that keep results honest:

* **Strictly earlier games only.** A game's history contains games with an earlier kickoff time. The game itself is never included.
* **Same-instant games are excluded.** Date-only sources give every game on a day the same substituted time (midnight UTC). Games on the same day therefore never inform each other, even though one of them may really have been played first.
* **No static ratings.** A snapshot's optional `ratings` are dropped, because nothing says they were known before the game.
* **History carries across seasons.** When several seasons are supplied, a game in 2025 sees all of 2024. `seasons`, `start`, and `end` choose which games are *scored*; earlier games still count as history.

A test uses a spy model to record exactly what each prediction could see and compares it with a hand-built list of earlier games.

## Running a backtest

From code:

```python
from datetime import UTC, datetime

from gamesense_prediction.backtesting import Backtester, BacktestModel

backtester = Backtester(registry)  # a ModelRegistry containing the models
result = backtester.run(
    [context_2024, context_2025],  # PredictionContext objects, one per season
    [BacktestModel(model_id="elo-v1"), BacktestModel(model_id="glicko2")],
    seasons=[2025],  # optional: score only these seasons
    start=datetime(2025, 9, 1, tzinfo=UTC),  # optional, inclusive, timezone-aware
)
print(result.summaries["elo-v1"].metrics["accuracy"])
```

From the command line, in `prediction/`:

```bash
python src/scripts/run_backtest.py --snapshot tests/fixtures/xgboost-poc-2025.json \
    --models elo glicko2 --output /tmp/backtest.json
```

Options: `--season`, `--start`, `--end` (a bare end date covers the whole day), `--trials` and `--seed` for the simulation models, and `--output` to save the full results as JSON. Available models are `elo`, `glicko2`, `bradley-terry`, `coin-flip`, and `monte-carlo`.

A simulation model needs `execution_kind=ExecutionKind.SIMULATION` and `trials` on its `BacktestModel`. Use a `seed` to make the run repeat exactly.

## Records and outcomes

Each `PredictionRecord` holds one model's result for one game and has an outcome:

| Outcome | Meaning |
| --- | --- |
| `CORRECT` | The predicted winner won. |
| `INCORRECT` | The other team won. |
| `TIE` | A prediction was made, but the game ended tied. |
| `NO_PREDICTION` | The model returned an error, such as `NOT_FOUND` when it needs history a team does not have yet. |

A `NO_PREDICTION` is never counted as a wrong guess. The error code is kept on the record.

The home-win probability stored on a record comes from the simulation result when there is one, and otherwise from the prediction's confidence.

## Metrics

The metrics are **provisional**. The team has not yet defined the final set (ticket 44, issue #48). Each metric is a plain function, so the final definitions can replace or extend `DEFAULT_METRICS` without changing the backtester, and `Backtester` accepts a custom `metrics` mapping.

| Metric | Definition | Better is |
| --- | --- | --- |
| `accuracy` | Correct predictions divided by decided games. | Higher |
| `brier_score` | Mean squared difference between the home-win probability and the result (1 if home won, else 0). | Lower |
| `log_loss` | Mean negative log of the probability given to what really happened, clipped at 1e-15. | Lower |
| `coverage` | Games with a prediction divided by all games. | Higher |

Provisional policies:

* Tied games are left out of `accuracy`, `brier_score`, and `log_loss`, and counted as predicted in `coverage`.
* A metric with nothing to score is `null` in the output, never zero.
* A coin flip scores about 0.50 accuracy, 0.25 Brier score, and 0.693 log loss, which makes a useful baseline.

## Caveats when comparing models

* **Models handle missing history differently.** Elo and Bradley-Terry return no prediction when a team has no completed games, while Glicko-2 predicts anyway from default ratings. Compare accuracy together with coverage.
* **XGBoost is not leakage-safe.** Its trained file has already seen its training games, so scoring it on those games leaks the answers. The command-line script leaves it out. Backtesting it needs a file trained only on games before the scored range.
* **The fixtures are synthetic.** Results on `tests/fixtures/` data show that the pipeline works, not how good any model is.
* **Speed.** Models rebuild their ratings from the available history on every call. On 800 synthetic games, Elo took about 2 seconds, Glicko-2 about 4 seconds, and Bradley-Terry about 50 seconds. Bradley-Terry roughly quadruples each time the season doubles, so a full real season of about 1,600 games should take several minutes, and multi-season runs much longer. Caching ratings between predictions would remove most of this cost.

## Output format

`result.to_wire()` returns JSON-ready data with `camelCase` names:

```json
{
  "summaries": [
    {
      "modelId": "elo-v1", "games": 24, "predicted": 23, "correct": 19,
      "incorrect": 3, "ties": 1, "noPrediction": 1,
      "metrics": {"accuracy": 0.864, "brierScore": 0.115, "logLoss": 0.401, "coverage": 0.958}
    }
  ],
  "records": [
    {
      "modelId": "elo-v1", "gameId": "...", "season": 2025, "scheduledAt": "2025-09-06T00:00:00Z",
      "homeTeamId": "...", "awayTeamId": "...", "actualWinnerTeamId": "...",
      "predictedWinnerTeamId": "...", "homeWinProbability": 0.71, "outcome": "CORRECT"
    }
  ]
}
```

Fields with no value, such as `errorCode` on a successful prediction, are left out of a record.

## Out of scope

Defining the final metrics, ranking models, combining predictions, saving results to a database, and any frontend display are left to their own tickets.
