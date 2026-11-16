# ADR: Embed stored predictions in game HTTP responses

## Status

Proposed. Implemented for issue #68; pending team review. Extends
[Embed teams in game HTTP responses](2026-11-03-enriched-game-responses.md).

## Context

The Games page and Game Breakdown show the same game and team data already returned
by `GET /api/games`, plus each model's prediction. A separate breakdown endpoint
would repeat that data and add a request for every selected game. Game History
compares each completed game's pregame prediction with its final result.

Predictions for scheduled games depend only on data the backend has already loaded,
so they can be calculated once after startup ingestion and read from memory. The
prediction service may be unavailable or slow, and game browsing must not depend on it.

## Decision

- Add an optional `predictions` object to `GameResponse` for both game endpoints,
  following `game-predictions.schema.json`.
- After startup ingestion and snapshot publication, run the configured models once
  for every `SCHEDULED` game in each live season. The job runs on one background
  thread, so startup and game retrieval never wait on Python.
- Store results in a `PredictionResultStore` keyed by canonical game ID. The MVP
  implementation is in memory; persistence (#59) can replace it behind the interface.
- Report a `state` for each queued game:
  - `PENDING`: the job has not reached the game yet.
  - `AVAILABLE`: at least one model succeeded.
  - `UNAVAILABLE`: no model succeeded, or the game could not be predicted (for
    example, because snapshot publication failed).
- Return only successful model results. Each contains `modelId`, the model's
  `displayName`, the existing `single-prediction-result` (method, predicted winner,
  confidence, optional predicted scores, generation time), and the
  `simulation-outcome` for simulation models. Failed models are logged and left out.
- Reserve an optional `aggregate` (`aggregated-prediction-outcome`), omitted until
  the consensus calculation (#56) is connected (#137).
- Omit `predictions` for games that were never queued: games completed before
  startup, other seasons, and every game when startup predictions or orchestration
  are disabled (`prediction.startup.enabled`, `prediction.orchestration.enabled`).
- Add only data the backend already has. The response does not add kickoff times,
  venues, rankings, records, or logos, which the current data feeds do not provide.

## Consequences

The frontend renders the Games list, Game Breakdown, and Game History from one
request per list. Selecting a game needs no additional request.

Predictions are lost on restart and calculated again for the remaining scheduled
games. A game that becomes `FINAL` while the backend runs keeps its stored pregame
prediction, but the backend does not refresh game data after startup (#65), so in
practice Game History shows pregame predictions only once refresh exists.
Games completed before startup have no pregame prediction until persistence (#59).

Startup prediction makes one request per configured model per scheduled game, run
sequentially. Remaining games show `PENDING` until the job reaches them.

Leaving failed models out keeps the contract simple, but the frontend cannot tell a
failed model from one that is not configured. Per-model failure reporting can be
added as an optional field later without breaking consumers.

Strict HTTP consumers must accept the new optional `predictions` field.
