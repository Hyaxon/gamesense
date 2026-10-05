"""Service-owned, immutable season snapshots, not model training state."""

from collections.abc import Mapping
from dataclasses import dataclass

from .contracts import WireModel, freeze


class MissingContextError(LookupError):
    """Required snapshot, season, team, history, or rating is unavailable."""


@dataclass(frozen=True, kw_only=True)
class Team(WireModel):
    schema_name = "team"
    id: str
    name: str
    conference: str
    external_id: str | None = None
    short_name: str | None = None
    logo_url: str | None = None


@dataclass(frozen=True, kw_only=True)
class CompletedGame:
    id: str
    season: int
    home_team_id: str
    away_team_id: str
    scheduled_at: str
    home_score: int
    away_score: int
    winner_team_id: str | None
    is_neutral_site: bool


@dataclass(frozen=True, kw_only=True)
class TeamRecord:
    wins: int = 0
    losses: int = 0
    ties: int = 0


@dataclass(frozen=True, kw_only=True)
class PredictionContext:
    """Snapshot history is ordered by UTC game time, then canonical game ID.

    This is the selected snapshot's history, not an implicit as-of cutoff.
    Ratings are optional named values; adapters must explicitly require the
    history/ratings they need and own any initialization policies.
    """

    data_snapshot_id: str
    season: int
    teams: Mapping[str, Team]
    completed_games: tuple[CompletedGame, ...]
    provenance: Mapping
    ratings: Mapping[str, Mapping[str, float]]

    def __post_init__(self):
        from datetime import datetime

        object.__setattr__(self, "teams", freeze(self.teams))
        object.__setattr__(self, "ratings", freeze(self.ratings))
        object.__setattr__(self, "provenance", freeze(self.provenance))
        object.__setattr__(
            self,
            "completed_games",
            tuple(
                sorted(
                    self.completed_games,
                    key=lambda game: (
                        datetime.fromisoformat(game.scheduled_at),
                        game.id,
                    ),
                )
            ),
        )

    def require_team(self, team_id: str) -> Team:
        try:
            return self.teams[team_id]
        except KeyError:
            raise MissingContextError("Required team is unavailable") from None

    def games_for(self, team_id: str) -> tuple[CompletedGame, ...]:
        self.require_team(team_id)
        return tuple(
            game
            for game in self.completed_games
            if team_id in (game.home_team_id, game.away_team_id)
        )

    def require_history(self, team_id: str) -> tuple[CompletedGame, ...]:
        games = self.games_for(team_id)
        if not games:
            raise MissingContextError("Required completed-game history is unavailable")
        return games

    def record_for(self, team_id: str) -> TeamRecord:
        games = self.games_for(team_id)
        wins = sum(game.winner_team_id == team_id for game in games)
        ties = sum(game.winner_team_id is None for game in games)
        return TeamRecord(wins=wins, losses=len(games) - wins - ties, ties=ties)

    def require_rating(self, team_id: str, name: str) -> float:
        self.require_team(team_id)
        try:
            return self.ratings[team_id][name]
        except KeyError:
            raise MissingContextError("Required rating is unavailable") from None
