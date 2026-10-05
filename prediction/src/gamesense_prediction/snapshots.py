"""Validated in-memory and trusted file-backed snapshot providers."""

import json
from collections.abc import Iterable
from datetime import datetime
from pathlib import Path
from types import MappingProxyType
from typing import Protocol

from .context import CompletedGame, MissingContextError, PredictionContext, Team
from .execution_validation import SchemaCatalog, canonical_identifier, require


class SnapshotProvider(Protocol):
    def get(self, data_snapshot_id: str, season: int) -> PredictionContext: ...


def context_from_wire(payload, *, catalog=None) -> PredictionContext:
    catalog = catalog or SchemaCatalog.default()
    catalog.validate("snapshot", payload)
    teams = {}
    for item in payload["teams"]:
        canonical_identifier(item["id"], "/teams/id")
        require(item["id"] not in teams, "Duplicate team identity", "/teams")
        teams[item["id"]] = Team.from_wire(item, catalog=catalog)
    completed = []
    seen = set()
    for game in payload["games"]:
        canonical_identifier(game["id"], "/games/id")
        require(game["id"] not in seen, "Duplicate game identity", "/games")
        seen.add(game["id"])
        require(game["season"] == payload["season"], "Game season mismatch", "/games")
        home, away = game["homeTeamId"], game["awayTeamId"]
        require(
            home != away and home in teams and away in teams,
            "Game teams must exist and differ",
            "/games",
        )
        require(
            datetime.fromisoformat(game["scheduledAt"]).tzinfo is not None,
            "Game time requires a timezone",
            "/games",
        )
        if game["status"] == "FINAL":
            home_score, away_score = game.get("homeScore"), game.get("awayScore")
            require(
                home_score is not None and away_score is not None,
                "Completed games require measured scores",
                "/games",
            )
            winner = (
                home
                if home_score > away_score
                else (away if away_score > home_score else None)
            )
            require(
                game.get("winnerTeamId") == winner,
                "Winner must agree with completed scores",
                "/games",
            )
            require(
                "isNeutralSite" in game,
                "Completed-game location policy must be explicit",
                "/games",
            )
            completed.append(
                CompletedGame(
                    id=game["id"],
                    season=game["season"],
                    home_team_id=home,
                    away_team_id=away,
                    scheduled_at=game["scheduledAt"],
                    home_score=home_score,
                    away_score=away_score,
                    winner_team_id=winner,
                    is_neutral_site=game["isNeutralSite"],
                )
            )
        elif game["status"] == "SCHEDULED":
            require(
                all(
                    game.get(key) is None
                    for key in ("homeScore", "awayScore", "winnerTeamId")
                ),
                "Scheduled games cannot contain measured scores",
                "/games",
            )
    ratings = payload.get("ratings", {})
    require(all(team_id in teams for team_id in ratings), "Rating team is unavailable")
    return PredictionContext(
        data_snapshot_id=payload["dataSnapshotId"],
        season=payload["season"],
        teams=teams,
        completed_games=tuple(completed),
        provenance=payload["provenance"],
        ratings=ratings,
    )


class InMemorySnapshotProvider:
    """Publish validated snapshots once; duplicate IDs are rejected."""

    def __init__(self, snapshots: Iterable[dict], *, catalog=None):
        contexts = {}
        for payload in snapshots:
            context = context_from_wire(payload, catalog=catalog)
            if context.data_snapshot_id in contexts:
                raise ValueError("Duplicate snapshot ID")
            contexts[context.data_snapshot_id] = context
        self._contexts = MappingProxyType(contexts)

    def get(self, data_snapshot_id: str, season: int) -> PredictionContext:
        context = self._contexts.get(data_snapshot_id)
        if context is None or context.season != season:
            raise MissingContextError("Required snapshot or season is unavailable")
        return context


class FileSnapshotProvider(InMemorySnapshotProvider):
    """Load explicitly configured local paths at startup, never request paths.

    Files are read once: subsequent file changes do not alter a published ID.
    """

    def __init__(self, paths: Iterable[Path], *, catalog=None):
        super().__init__(
            (json.loads(Path(path).read_text()) for path in paths), catalog=catalog
        )
