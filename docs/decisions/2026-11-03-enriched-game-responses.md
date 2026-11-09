# ADR: Embed teams in game HTTP responses

## Status

Proposed

## Context

Game screens need team names, while canonical games reference teams by ID. Returning
only Game requires additional frontend requests to display each matchup. Both existing
Game and Team schemas reject unknown properties.

## Decision

Use a separate GameResponse contract for both game endpoints. Preserve top-level game
fields and add optional homeTeam and awayTeam objects conforming to the full Team schema.
Resolve teams through CanonicalDataProvider. Omit unavailable teams while retaining IDs.
Keep canonical Game and ingestion/prediction contracts unchanged.

## Consequences

Clients can display available team names with one request. Payloads repeat team data
across games. Strict HTTP consumers must adopt the new response schema. Team details
reflect currently loaded data, not historical team attributes. Separate team endpoints
can be introduced for independent team browsing. Review this contract before merging.
