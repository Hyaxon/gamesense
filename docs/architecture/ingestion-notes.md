# Notes on Adam's Data Feed

This doc is for whoever builds data ingestion. It looks at the real files Adam gave us (`Schedule.csv` and `Schedule.json`) and explains how they map to `domain-model.md`. It is not the ingestion code itself, just what to expect and what to watch out for.

## What the feed looks like

The JSON file is one object with a `teams` list. Each team has a `name`, a `league`, and a `schedule` list. The CSV has the same data, just laid out as one block per team instead of one JSON object per team.

Each item in a team's `schedule` is **one calendar day**, not one game. Most days are `BYE` (no game that day). A team playing 12 games a season still has hundreds of rows, because every day without a game gets its own row too.

## Things to watch out for

**Every real game shows up twice.** Team A's schedule has a row for the game from Team A's side (`location: "vs."` or `"@"`, their own score first). Team B's schedule has a separate row for the same game from their side. There is no shared game ID linking the two rows. To build one `Game` record, match rows by `(date, team A name, team B name)` and pick one side to be home (the row with `"vs."`).

**BYE rows are not games.** Skip them. Don't try to turn them into a `Game` with some kind of "no game" status.

**No IDs anywhere.** Teams and conferences are only given as name strings (`"Western Michigan Broncos"`, `"Big Ten Conference"`). There's no team code or number to key off of. This means:
- `Team.externalId` for this feed is just the team's full name string. There's nothing else to use.
- Matching a team across different games means matching on the exact name string. Watch for name mismatches (typos, "St." vs "State", etc.) — this is the most likely source of ingestion bugs.

**Everything is a string.** Scores (`"pointsScored": "23"`), dates (`"08/29/2025"`), all of it. Ingestion has to convert:
- Scores: string to integer.
- Dates: `MM/DD/YYYY` to an ISO 8601 timestamp (`scheduledAt`). The feed has no game time, only a date, so pick a fixed time (e.g. midnight UTC) or leave that as a known gap.

**One dead field.** `index` is `"0"` on every single row we checked, for every team. It doesn't look like it's used for anything. Don't bother mapping it to anything in our model.

**Neutral site games.** `location: "N"` means neutral site. The feed never gives an actual venue name, city, or state for any game. So:
- Set `Game.isNeutralSite = true` when `location` is `"N"`.
- Don't try to build a `venue` object — there's nothing to put in it. Leave `venue` out entirely for data from this feed.
- This is exactly why `isNeutralSite` is its own field on `Game` and not stuck inside `venue`. See the note in `domain-model.md` under [Game](domain-model.md#game).

**This particular export is fully historical.** Every non-BYE row we checked already has a result (`W` or `L`, both scores filled in). So every `Game` built from this file should get `status: "FINAL"`, with `winnerTeamId` set by comparing the two scores (don't just trust the feed's `W`/`L` value blindly — recompute it from the scores as a sanity check).

## Quick mapping summary

| Feed field | Domain model field | Notes |
|---|---|---|
| `name` (team) | `Team.name` | Also becomes `Team.externalId` since there's no other ID. |
| `league` | `Conference.name` | No `shortName`/abbreviation given; leave that field empty. |
| `timestamp` | `Game.scheduledAt` | Reparse `MM/DD/YYYY` into ISO 8601. |
| `location` + `opponent` | `Game.homeTeamId` / `Game.awayTeamId` | Match the two mirrored rows for the same game first. |
| `location: "N"` | `Game.isNeutralSite` | `true` when `"N"`, otherwise `false`. No venue data available. |
| `outcome`, `pointsScored`, `pointsAllowed` | `Game.homeScore`, `Game.awayScore`, `Game.winnerTeamId`, `Game.status` | Cast scores to integers. Set `status` to `FINAL`. Derive `winnerTeamId` from the scores. |
| `index` | (nothing) | Always `"0"`. Not used. |
| — | `Game.id` | Not in the feed. Generate a new UUID during ingestion. |
| — | `Team.id`, `Conference.id` | Not in the feed. Generate a UUID the first time each name is seen, then reuse it. |
