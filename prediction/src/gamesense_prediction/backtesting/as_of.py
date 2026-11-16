"""Pre-game views of history, so a backtest can never see the future.

GameTimeline merges the completed games of every supplied context and builds,
for any game, a context holding only the games played strictly before it.
"""

from bisect import bisect_left
from collections.abc import Collection, Iterator, Sequence
from datetime import datetime

from ..context import CompletedGame, PredictionContext


def game_time(game: CompletedGame) -> datetime:
    return datetime.fromisoformat(game.scheduled_at)


class GameTimeline:
    """Every completed game from the given contexts, in time order."""

    def __init__(self, contexts: Sequence[PredictionContext]):
        teams = {}
        games = {}
        for context in contexts:
            teams.update(context.teams)
            for game in context.completed_games:
                games.setdefault(game.id, game)
        self.teams = teams
        # Same order as PredictionContext: game time, then canonical game ID.
        self.games = tuple(sorted(games.values(), key=lambda g: (game_time(g), g.id)))
        self._times = [game_time(game) for game in self.games]

    def select(
        self,
        *,
        seasons: Collection[int] | None = None,
        start: datetime | None = None,
        end: datetime | None = None,
    ) -> Iterator[CompletedGame]:
        """Games to evaluate. ``start`` and ``end`` are both inclusive."""
        for game, time in zip(self.games, self._times, strict=True):
            if seasons is not None and game.season not in seasons:
                continue
            if start is not None and time < start:
                continue
            if end is not None and time > end:
                continue
            yield game

    def context_before(self, game: CompletedGame) -> PredictionContext:
        """Context with only games played strictly before ``game``.

        Games at the same instant are excluded too. Date-only sources give every
        game that day the same substituted time, so same-day games must never
        inform each other. Static ratings are dropped: nothing says they were
        known before the game.
        """
        cutoff = bisect_left(self._times, game_time(game))
        return PredictionContext(
            data_snapshot_id=f"backtest-{game.id}",
            season=game.season,
            teams=self.teams,
            completed_games=self.games[:cutoff],
            provenance={
                "source": "Backtest view of games before the target game",
                "targetGameId": game.id,
                "cutoff": game.scheduled_at,
            },
            ratings={},
        )
