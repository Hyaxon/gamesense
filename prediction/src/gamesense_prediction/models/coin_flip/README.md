# Coin-Flip Example Model

This is a working example of a prediction package and a random baseline.
It flips a fair coin for each trial: heads wins for home, tails wins for away.
It uses no football statistics and makes no claim to predict real team strength.

## Run it

From `prediction/`, with the service installed in your active Python environment:

```sh
python examples/run_coin_flip.py --trials 1000 --seed 42
python examples/run_coin_flip.py --trials 1 --seed 42
python -m pytest tests/models/test_coin_flip.py
```

Omit `--seed` to generate a new seed. The result records the actual seed in
`metadata.seed`; pass that seed on your next run to replay the sampled outcomes.
The seed controls an invocation-local `random.Random`, not global random state.
Reproducibility is promised for the same model version, request, and Python
runtime; generated IDs and timestamps still change.

## Read the files in this order

1. `algorithm.py`: counts the actual flips and calculates their fractions.
2. `adapter.py`: provides the descriptor and `execute()`, reads the shared request,
   checks team identities, calls the algorithm, and constructs the standard result.
3. `__init__.py`: exports `CoinFlipModel` for a simple import.
4. `prediction/examples/run_coin_flip.py`: registers the model, loads fixture
   data, creates a request, and calls the common runner.
5. `prediction/tests/models/test_coin_flip.py`: tests exact outcomes and framework
   integration, including seed replay and tie handling.

## Contract choices

| Setting | Value |
| --- | --- |
| Model ID | `coin-flip-v1` |
| Algorithm family (`method`) | `RANDOM` |
| Execution kind | `SIMULATION` |
| Trials | 1 through 100000 |
| Explicit seed | Supported, 0 through 2147483647 |
| Configuration | None; omitted or empty only |
| Required context | Both team identities in the selected season snapshot |
| History/ratings | Not required |

This model supports simulation only. Use one trial for a single flip. The standard
prediction is the team with more sampled wins; a tie selects the home-labeled
team, even at a neutral site. Confidence is the larger sampled win fraction.

The underlying fair-coin odds are always 50/50. Reported probabilities are sample
frequencies, so they can differ from 50/50. With one trial, the reported confidence
is 1.0 because the selected team won that sole simulated trial; it is not a claim
of certainty about a real game. The home tie policy can favor home selections
across repeated runs with an even number of trials.

The script wires this model locally. It does not add an HTTP endpoint or install
the model in production startup. For your own package, follow the
[step-by-step guide](../../../../../docs/architecture/prediction-model-walkthrough.md).
