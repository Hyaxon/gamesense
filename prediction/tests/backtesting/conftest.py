"""Small hand-checkable dataset and stub models shared by the backtesting tests.

Four teams play nine games over two seasons. Two games share each kickoff time
(to test same-instant exclusion) and one game ends tied.
"""

import pytest

from gamesense_prediction.context import CompletedGame, PredictionContext, Team
from gamesense_prediction.contracts import (
    ExecutionKind,
    ModelDescriptor,
    PredictionMethod,
)
from gamesense_prediction.execution_helpers import ExecutionTimer
from gamesense_prediction.registry import ModelRegistration, ModelRegistry

A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
D = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
TEAMS = {team: Team(id=team, name=team[:1], conference="Test") for team in (A, B, C, D)}


def game(number, season, home, away, day, home_score, away_score, neutral=False):
    winner = home if home_score > away_score else away
    if home_score == away_score:
        winner = None
    return CompletedGame(
        id=f"00000000-0000-4000-8000-{number:012d}",
        season=season,
        home_team_id=home,
        away_team_id=away,
        scheduled_at=f"{day}T00:00:00Z",
        home_score=home_score,
        away_score=away_score,
        winner_team_id=winner,
        is_neutral_site=neutral,
    )


# (home, away, score) per game; home wins in games 1, 3, 6 and 9 only.
GAMES_2024 = (
    game(1, 2024, A, B, "2024-09-01", 21, 14),
    game(2, 2024, C, D, "2024-09-01", 10, 17),
    game(3, 2024, A, C, "2024-09-08", 28, 3),
    game(4, 2024, B, D, "2024-09-08", 14, 14),  # tie
    game(5, 2024, D, A, "2024-09-15", 7, 24),
)
GAMES_2025 = (
    game(6, 2025, A, D, "2025-09-06", 17, 10),
    game(7, 2025, B, C, "2025-09-06", 20, 24),
    game(8, 2025, C, A, "2025-09-13", 14, 21, neutral=True),
    game(9, 2025, D, B, "2025-09-13", 31, 13),
)


def season_context(season, games):
    return PredictionContext(
        data_snapshot_id=f"test-{season}",
        season=season,
        teams=TEAMS,
        completed_games=games,
        provenance={"source": "Synthetic backtesting test data"},
        ratings={},
    )


@pytest.fixture
def contexts():
    return [season_context(2024, GAMES_2024), season_context(2025, GAMES_2025)]


class FixedWinnerModel:
    """Always picks the home (or away) team with a fixed confidence."""

    def __init__(self, model_id, *, pick_home, confidence=0.7):
        self.model_id = model_id
        self.pick_home = pick_home
        self.confidence = confidence

    def get_descriptor(self):
        return ModelDescriptor(
            model_id=self.model_id,
            display_name=self.model_id,
            method=PredictionMethod.RANDOM,
            version="1.0.0",
            supported_execution_kinds=(ExecutionKind.DETERMINISTIC,),
        )

    def execute(self, request, context):
        matchup = request.matchup
        winner = matchup.home_team_id if self.pick_home else matchup.away_team_id
        return ExecutionTimer().success(
            request,
            self.get_descriptor(),
            winner_team_id=winner,
            confidence=self.confidence,
        )


class SpyModel(FixedWinnerModel):
    """Picks home, and records exactly what each execution was allowed to see."""

    def __init__(self):
        super().__init__("spy", pick_home=True, confidence=0.6)
        self.seen = []

    def execute(self, request, context):
        self.seen.append(
            {
                "target": context.provenance["targetGameId"],
                "history": [game.id for game in context.completed_games],
                "ratings": dict(context.ratings),
                "season": context.season,
            }
        )
        return super().execute(request, context)


def registry_of(*models):
    return ModelRegistry(
        [ModelRegistration(adapter=model) for model in models],
        {PredictionMethod.RANDOM: models[0].get_descriptor().model_id},
    )
