"""Validated in-memory and trusted file-backed snapshot providers."""

import json
from collections.abc import Iterable
from datetime import datetime
from pathlib import Path
from threading import Lock
from typing import Protocol

from .context import CompletedGame, MissingContextError, PredictionContext, Team
from .execution_validation import SchemaCatalog, canonical_identifier, require


class SnapshotProvider(Protocol):
    def get(self, data_snapshot_id: str, season: int) -> PredictionContext: ...


def context_from_wire(payload, *, catalog=None) -> PredictionContext:
    """Turn a wire-format snapshot into validated, immutable model input.

    Reject inconsistent records rather than silently changing the dataset a model
    sees. Files and HTTP publications share these rules so their contexts agree.
    """
    catalog = catalog or SchemaCatalog.default()
    # Check field shapes first; the checks below enforce relationships that the
    # schema cannot express, such as team membership and agreement with scores.
    catalog.validate("snapshot", payload)
    teams = {}
    # Duplicate identities would otherwise overwrite a team in this lookup and
    # make the meaning of a game's team reference depend on payload order.
    for item in payload["teams"]:
        canonical_identifier(item["id"], "/teams/id")
        require(item["id"] not in teams, "Duplicate team identity", "/teams")
        teams[item["id"]] = Team.from_wire(item, catalog=catalog)
    completed = []
    seen = set()
    # Validate every game's identity and references, even if its status keeps it
    # out of model history. Duplicate games could count the same result twice.
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
        # A timezone is needed to order history by actual instants rather than
        # interpreting game times using the server's local timezone.
        require(
            datetime.fromisoformat(game["scheduledAt"]).tzinfo is not None,
            "Game time requires a timezone",
            "/games",
        )
        # Only final results become history. Partial, postponed, and cancelled
        # games must not influence records as if their outcomes were settled.
        if game["status"] == "FINAL":
            home_score, away_score = game.get("homeScore"), game.get("awayScore")
            require(
                home_score is not None and away_score is not None,
                "Completed games require measured scores",
                "/games",
            )
            # Derive the expected winner to check the supplied result, not repair
            # it. Equal scores require no winner and remain a tie in the context.
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
            # Missing location information must not silently mean home advantage.
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
            # Zero is a measured score too; an unplayed game must use absent or
            # null result fields so placeholders cannot be mistaken for results.
            require(
                all(
                    game.get(key) is None
                    for key in ("homeScore", "awayScore", "winnerTeamId")
                ),
                "Scheduled games cannot contain measured scores",
                "/games",
            )
    # Ratings are optional because models have different input requirements, but
    # any supplied rating must belong to a team in this snapshot.
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
    """Keep each published ID tied to one immutable dataset for model executions.

    Storage is local to this process; restarting Python discards HTTP publications.
    """

    def __init__(self, snapshots: Iterable[dict] = (), *, catalog=None):
        self._catalog = catalog or SchemaCatalog.default()
        self._lock = Lock()
        self._contexts: dict[str, PredictionContext] = {}
        for payload in snapshots:
            self.publish(payload)

    def publish(self, payload: dict) -> str:
        """Validate before atomically adding a snapshot without replacing any ID."""
        # Validate before taking the lock so a large or invalid payload does not
        # hold up readers. A failure leaves every published snapshot untouched.
        context = context_from_wire(payload, catalog=self._catalog)
        with self._lock:
            # Checking and inserting must share a lock: concurrent publishers
            # must not overwrite an ID or lose each other's additions.
            if context.data_snapshot_id in self._contexts:
                raise ValueError("Duplicate snapshot ID")
            # Replace the index as a whole; existing contexts remain unchanged
            # for predictions that already hold a reference to them.
            self._contexts = {
                **self._contexts,
                context.data_snapshot_id: context,
            }
        return context.data_snapshot_id

    def get(self, data_snapshot_id: str, season: int) -> PredictionContext:
        with self._lock:
            context = self._contexts.get(data_snapshot_id)
        # The context is immutable, so callers can use it after releasing the lock.
        # Never substitute another season just because the requested ID exists.
        if context is None or context.season != season:
            raise MissingContextError("Required snapshot or season is unavailable")
        return context


class FileSnapshotProvider(InMemorySnapshotProvider):
    """Load explicitly configured local paths at startup, never request paths.

    Files are read once: subsequent file changes do not alter a published ID.
    """

    def __init__(self, paths: Iterable[Path], *, catalog=None):
        # Files keep their IDs but use the same validation and duplicate policy
        # as snapshots accepted while the service is running.
        super().__init__(
            (json.loads(Path(path).read_text()) for path in paths), catalog=catalog
        )
