# Bradley-Terry Model

Assigns every team a latent strength from the win/loss record in the snapshot,
then predicts a matchup as `P(A beats B) = strength_A / (strength_A + strength_B)`.
Unlike a per-team average, strength accounts for who each team played.

## Run it

From `prediction/`:

```sh
python examples/run_bradley_terry.py
python -m pytest tests/models/test_bradley_terry.py
```

## Read the files in this order

1. `algorithm.py`: fits strengths and computes the matchup probability.
2. `adapter.py`: descriptor and `execute()`; builds the standard result.
3. `__init__.py`: exports `BradleyTerryModel`.
4. `prediction/examples/run_bradley_terry.py`: wires registry, snapshot and runner.
5. `prediction/tests/models/test_bradley_terry.py`: calculation and contract tests.

## Contract choices

| Setting | Value |
| --- | --- |
| Model ID | `bradley-terry-v1` |
| Algorithm family (`method`) | `BRADLEY_TERRY` |
| Execution kind | `DETERMINISTIC` |
| Seed / trials / configuration | Not supported |
| Required context | Both teams in the selected season snapshot |
| History/ratings | At least one completed game for each team |

A team with no completed games returns `NOT_FOUND` rather than a default rating.
Supporting scores named `winProbability` expose each team's probability.

## Method notes

- Fitted by the MM (Zermelo) iteration over every completed game in the snapshot.
- A tie counts as half a win for each team.
- A small prior (0.5 virtual win and loss against an average phantom team) keeps
  winless/unbeaten teams finite and makes 1.0 mean "average team".

## Known simplifications

- No home-field advantage; `isNeutralSite` is accepted but does not change the result.
- Margin of victory is ignored; only who won matters.
- All games in the snapshot weigh equally (no recency weighting).
- Probabilities are uncalibrated and make no real-world accuracy claim.
