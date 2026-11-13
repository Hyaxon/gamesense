# Data Ingestion and Retrieval

This document describes how game data gets from the mentor's feed (historical files and the live current-season feed) to the rest of the backend. For what the feed itself looks like and its quirks, see [Ingestion Notes](ingestion-notes.md). For the canonical objects, see the [Shared Domain Model](domain-model.md).

## Overview

Both sources have the same shape, so they share one set of source records and one mapper. They differ only in how the data is obtained.

```text
historical file ──► HistoricalJsonReader ─┐
                                          ├─► SourceFeed ─► SourceFeedMapper ─► InMemoryCanonicalDataStore ─► CanonicalDataProvider
current-season feed ─► CurrentSeasonClient ┘   (source        (canonical Team,    (merges by canonical ID)      (what consumers use)
                                                records)       Game, Conference)
```

Source records and field names (`SourceFeed`, `SourceTeam`, `SourceScheduleEntry`) stay inside the ingestion layer (`datasource` and `ingestion` packages). Nothing in `data` or `domain` depends on them.

## Packages

| Package | Responsibility |
| --- | --- |
| `datasource` | HTTP client for the current-season feed: configuration, timeouts, and `DataSourceException`. |
| `ingestion.source` | The source records. One set, shared by both paths. |
| `ingestion.mapper` | Mapping to canonical objects: `TeamMapper`, `GameMapper`, `ConferenceMapper`, and `CanonicalIds`. |
| `ingestion` | `SourceFeedMapper` (the shared pipeline) and `IngestionResult`. |
| `ingestion.historical` | `HistoricalJsonReader` and `HistoricalIngestionService`. |
| `ingestion.currentseason` | `CurrentSeasonIngestionService`. |
| `data` | `CanonicalDataProvider`, `InMemoryCanonicalDataStore`, and the existing `PredictionDataProvider` and `PredictionSnapshot`. |
| `domain` | The canonical records. No parallel models exist for historical or current-season data. |

## Canonical identity

IDs are derived from source identifiers, not generated randomly:

* **Team:** the ID comes from the full team name, which is the feed's only identifier. `Team.externalId` keeps the name.
* **Game:** the ID comes from the game key, which is the date plus the two team names in alphabetical order. The key is the same whichever team's row it is built from, so the two mirrored rows of a game give one game. `Game.externalId` keeps the key.

The same team or game therefore gets the same ID from any source, on every load, and after every restart. IDs are valid version 4 UUIDs, as the prediction service requires, but they are derived from a hash of the source identifier instead of being random. The IDs must never change between releases, and a test pins example values to guard that.

## Overlapping records

Because IDs are deterministic, a game that appears in more than one source is stored once. When a game is loaded again:

* A newer record replaces the stored one.
* A finished game (`FINAL`) is never replaced by one that is not finished. Loading an older current-season feed cannot undo a result that is already known.

Teams are replaced the same way, so the latest load wins for fields such as `conference`.

## Retrieval

Consumers depend on `CanonicalDataProvider`, which extends the existing `PredictionDataProvider`:

| Method | Returns |
| --- | --- |
| `findGame(gameId)` | The game with that canonical ID, if loaded. |
| `findGames(season)` | Every loaded game in a season. |
| `findAllGames()` | Every loaded game across all seasons. |
| `findTeam(teamId)` | A team by canonical ID. Use a game's `homeTeamId` and `awayTeamId` to get its teams. |
| `findSnapshot(season)` | The acknowledged Python snapshot ID, or an explicit season-specific fallback; otherwise empty. |
| `findTeam(snapshot, teamId)` | A team, only if it plays at least one game in the snapshot's season. |

The `InMemoryCanonicalDataStore` is registered as a Spring bean and is the application's `PredictionDataProvider`, so prediction orchestration and every other consumer use the same single access path.

### Seasons and snapshots

A game's season comes from its date: July to December belong to that calendar year, January to June to the previous one. The feed gives dates only, so `scheduledAt` is midnight UTC.

After startup ingestion succeeds, the backend builds and publishes a snapshot for each season represented in the live feed. The builder uses `findGames(season)`, includes the teams referenced by those games, and adds provenance. Malformed final or scheduled records fail the whole publish; games are not silently dropped. Python validates the complete payload before storing it.

`POST /snapshots` receives the payload without `dataSnapshotId`. Python returns a versioned ID such as `season-2026-<uuid>`. Java stores that exact ID by season only after a successful response, and prediction orchestration uses it in `ModelExecutionRequest`. Loading games alone no longer creates a snapshot reference.

Publication failures are logged and do not stop backend startup or game endpoints. Ingestion failures still stop startup. Set `PREDICTION_SNAPSHOT_PUBLISH_ENABLED=false` to disable publishing. For file-based development, configure both `PREDICTION_SNAPSHOT_FALLBACK_SEASON` and `PREDICTION_SNAPSHOT_FALLBACK_ID` to match a file loaded by Python; canonical Java teams and games must also match that dataset. An acknowledged publication takes precedence over the fallback.

Python snapshots and Java's selected IDs are held in memory. Run Python with one worker. A Python restart loses published data; restart the backend after Python to republish it. Automatic refresh, restart recovery, durable storage, and historical-season publication are outside this startup flow.

## Loading

At startup, `BackendApplication` loads the live feed and then the packaged historical dataset before publishing live-season snapshots. Set `GAMESENSE_INGESTION_ENABLED=false` to skip this startup flow. The ingestion services also support explicit loading; there is no scheduled refresh.

```java
IngestionResult historicalResult = historicalIngestionService.loadFile(path);
IngestionResult currentResult = currentSeasonIngestionService.loadCurrentSeason();
```

* Data is available through the provider as soon as the call returns.
* Each load adds to the store. Loading another source or season never discards what is already loaded.
* `IngestionResult.skipped()` lists records that could not be mapped, with the reason. One bad record never fails the rest.
* If the current-season feed is unreachable or unusable, `loadCurrentSeason()` throws `DataSourceException` and the store is unchanged.
* Calling the loaders at startup or on a schedule is left to later work. Fetching the live feed on every prediction request is not recommended, because the feed is large and comes from a single host.

## Adding another source

Produce a `SourceFeed` (or map to the same canonical objects), run it through `SourceFeedMapper`, and pass the result to `InMemoryCanonicalDataStore.load(...)`. No consumer changes are needed.

## Known limits

* A game is skipped if either team is missing from the same feed. This includes opponents that are not in the feed's team list, and they appear in `skipped()`.
* The feed has no venue, week, ranking, record, or short name, so those fields stay empty.
* For a neutral-site game, the home team is chosen alphabetically, so both rows of the game agree.
* The two mirrored rows of a game are not cross-checked. The first one mapped wins.
* The store is in memory only. Data is lost on restart and must be loaded again.
