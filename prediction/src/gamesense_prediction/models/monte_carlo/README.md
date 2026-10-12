# Monte Carlo Model

Predicts a matchup by simulating it many times. Each trial draws a random
score for both teams from a Normal distribution centered on a blend of the
team's own scoring average and the opponent's average points allowed, then
counts how often each side wins.

## Run it

From `prediction/`, with the service installed in your active Python environment:

```sh
python examples/run_monte_carlo.py --trials 10000 --seed 42
python -m pytest tests/models/test_monte_carlo.py
```

Omit `--seed` to generate a new seed. The result records the actual seed in
`metadata.seed`; pass that seed on your next run to replay the sampled outcomes.

## Read the files in this order

1. `algorithm.py`: computes each team's expected score from its own game
   history and simulates the matchup.
2. `adapter.py`: provides the descriptor and `execute()`, reads the shared
   request, calls the algorithm, and constructs the standard result.
3. `__init__.py`: exports `MonteCarloModel` for a simple import.
4. `prediction/examples/run_monte_carlo.py`: registers the model, loads
   fixture data, creates a request, and calls the common runner.
5. `prediction/tests/models/test_monte_carlo.py`: tests the calculations and
   framework integration, including seed replay and error cases.

## Contract choices

| Setting | Value |
| --- | --- |
| Model ID | `monte-carlo-v1` |
| Algorithm family (`method`) | `MONTE_CARLO` |
| Execution kind | `SIMULATION` |
| Trials | 1 through 100000 |
| Explicit seed | Supported, 0 through 2147483647 |
| Configuration | None; omitted or empty only |
| Required context | Both team identities in the selected season snapshot |
| History/ratings | Required: at least one completed game for each team |

A team with no completed games in the snapshot has nothing to compute an
average from, so this model returns `NOT_FOUND` for it rather than inventing
a default score. Supporting scores report each team's blended expected
score (`"expectedScore"`, in points) for diagnostic use.

## Known simplifications

- One shared score standard deviation across every team, not one computed
  per team from its own score history.
- No home-field-advantage adjustment; `isNeutralSite` is accepted on the
  matchup but doesn't change the simulated scores.
