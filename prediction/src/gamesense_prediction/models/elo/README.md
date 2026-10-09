# Elo Model

Predicts a matchup from team ratings. Every team starts at 1500, and the model
replays completed games in the snapshot, moving ratings up or down
based on how surprising each result was. The final rating gap becomes the home
team's win probability.

## Run it

From `prediction/`, with the service installed in your active Python environment:

```sh
python examples/run_elo.py
python -m pytest tests/models/test_elo.py
```

## Read the files in this order

1. `algorithm.py`: builds ratings from game history and predicts the matchup.
2. `adapter.py`: provides the descriptor and `execute()`, reads the shared
   request, calls the algorithm, and constructs the standard result.
3. `__init__.py`: exports `EloModel` for a simple import.
4. `prediction/examples/run_elo.py`: registers the model, loads fixture data,
   creates a request, and calls the common runner.
5. `prediction/tests/models/test_elo.py`: tests the rating calculations.

## Contract choices

| Setting | Value |
| --- | --- |
| Model ID | `elo-v1` |
| Algorithm family (`method`) | `ELO` |
| Execution kind | `DETERMINISTIC` |
| Trials / seed | Not supported; the same data always gives the same result |
| Configuration | None; omitted or empty only |
| Required context | Both team identities in the selected season snapshot |
| History/ratings | Required: at least one completed game for each team |

A team with no completed games in the snapshot returns `NOT_FOUND`. Supporting
scores report each team's rating (`"rating"`, in `rating_points`) for
diagnostic use.

## How it works

| Constant | Value | Meaning |
| --- | --- | --- |
| `K_FACTOR` | 20 | How much one game can move a rating |
| `HOME_FIELD_BONUS` | 60 | Elo points added to the home team (not at neutral sites) |
| `STARTING_ELO` | 1500 | Rating for a team with no games yet |
| `MAX_MARGIN_MULTIPLIER` | 3 | Caps the extra weight given to blowouts |

## Known simplifications

- No offseason regression toward the mean; each season snapshot starts fresh.
- Constants are fixed in code and not configurable per request.