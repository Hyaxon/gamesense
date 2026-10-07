# XGBoost POC

`xgboost-v1` uses the XGBoost library's `XGBClassifier` to predict a winner.
Its method is `MACHINE_LEARNING`, version is `1.0.0`, and execution kind is
`DETERMINISTIC`. The adapter owns explicit training, saving, and loading methods;
its `execute()` method performs inference only. It accepts no request
configuration, seed, or trials.

`algorithm.py` owns features, offline training, and matchup probabilities;
`adapter.py` owns model setup and translates probabilities into the shared result
contract. Scripts use the package API without handling XGBoost classifiers directly.

## Model setup

```python
from gamesense_prediction.models.xgboost import XGBoostModel

# Separate offline training operation; returns an adapter and writes the model.
adapter = XGBoostModel.train_and_save(historical_context, "model.json")

# Load once during setup, then register the adapter with the shared runner.
adapter = XGBoostModel.from_file("model.json")
```

`train_and_save()` accepts `.json` and `.ubj` output paths. `from_file()` loads the
classifier without training; subsequent executions reuse it without reading the
file again. `XGBoostModel.train(context)` creates a trained adapter in memory for
the local demo, and `XGBoostModel(classifier)` still accepts an existing classifier.
Training uses the supplied snapshot; the model package does not fetch sports data.

Paths must come from trusted setup configuration. Callers choose the artifact
location and provide an existing parent directory. Automatic storage management,
artifact metadata/version selection, and FastAPI startup wiring remain future work.

## Inputs and training

The seven inputs, in fixed order, are home average points scored, home average
points allowed, home win rate, the same three away statistics, and a home advantage
flag (`0` at neutral sites, `1` otherwise). Ties count as half a win in history.
Team IDs are lookup keys, not classifier inputs. No external ratings are required.

`build_training_data(context)` walks completed games chronologically and creates
each row from strictly earlier history. Same-time results cannot enter each other's
features. Tied games and games where either team has no earlier history have no
training label, but contribute history for subsequent games. Labels are `1` for
home wins and `0` for away wins. `train_model(context)` rejects data that lacks
either class after these filters.

Training uses 50 trees, depth 2, learning rate 0.1, binary logistic loss, CPU
histogram trees, one worker, and a fixed training random state of 42. The snapshot
is never modified. A new trained artifact should receive a new model version when
integrated into the service.

## Prediction

`predict_matchup(model, home_id, away_id, context, is_neutral_site)` builds the same
features from the supplied snapshot and returns `P(home wins)` via `predict_proba`.
Both teams must have completed history; the runner returns `NOT_FOUND` otherwise.
The adapter chooses the larger probability, selecting home at exactly 0.5, and
returns the winner's probability as confidence. Supporting scores named
`winProbability` with unit `probability` expose each team's probability.
This classifier does not estimate final scores or run simulations.

Predictions repeat for the same trained artifact, snapshot, matchup, and runtime.
The caller must supply history available before the matchup when backtesting;
inference uses all history in its supplied snapshot. Keep evaluation games out of
the training snapshot as well. Probabilities are uncalibrated, and no real-world
accuracy is claimed. The included 24-game synthetic fixture demonstrates execution
and serialization, not model quality. Real usage requires more historical data,
chronological held-out evaluation, and probability calibration.

## Run

From `prediction/`, install dependencies and run the local example:

```sh
python -m pip install -e ".[dev]"
python src/examples/run_xgboost.py
python -m pytest tests/models/test_xgboost.py
```

Train and save a model separately, then load it for inference:

```sh
python src/scripts/train_xgboost.py --output /tmp/xgboost-poc.json
python src/examples/run_xgboost.py --model /tmp/xgboost-poc.json
```

The training CLI also accepts `--snapshot PATH --snapshot-id ID --season YEAR`
for a trusted snapshot in the service's existing JSON format. Saved artifacts must
use the seven features above in the same order. File paths belong to trusted
startup code, not request configuration. This POC adds no HTTP endpoint.

On macOS, install `libomp` if the XGBoost import reports a missing OpenMP runtime.
See the [installation guide](https://xgboost.readthedocs.io/en/stable/install.html),
[classifier API](https://xgboost.readthedocs.io/en/stable/python/python_api.html), and
[model IO guide](https://xgboost.readthedocs.io/en/stable/tutorials/saving_model.html).
