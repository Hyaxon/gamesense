"""Score-simulation calculations, independent of the wire contract and runner."""

from dataclasses import dataclass
from random import Random

from ...context import PredictionContext

# Shared score spread for every team. Computing it per team from history is
# a future improvement.
DEFAULT_SCORE_STANDARD_DEVIATION = 12.0


@dataclass(frozen=True)
class SimulationCounts:
    home_wins: int
    away_wins: int
    home_expected_score: float
    away_expected_score: float

    @property
    def trials(self) -> int:
        return self.home_wins + self.away_wins

    @property
    def home_probability(self) -> float:
        return self.home_wins / self.trials

    @property
    def away_probability(self) -> float:
        return self.away_wins / self.trials


def average_points_scored(team_id, games) -> float:
    scored = [
        g.home_score if g.home_team_id == team_id else g.away_score for g in games
    ]
    return sum(scored) / len(scored)


def average_points_allowed(team_id, games) -> float:
    allowed = [
        g.away_score if g.home_team_id == team_id else g.home_score for g in games
    ]
    return sum(allowed) / len(allowed)


def blended_expected_score(
    own_average_points_scored: float, opponent_average_points_allowed: float
) -> float:
    # Blend what a team usually scores with what this opponent usually
    # allows, instead of using either number alone.
    return (own_average_points_scored + opponent_average_points_allowed) / 2


def simulate_matchup(
    home_team_id: str,
    away_team_id: str,
    context: PredictionContext,
    trials: int,
    rng: Random,
) -> SimulationCounts:
    """Each trial draws a random score for both teams and compares them.

    Requires completed-game history for both teams; use require_history so a
    team with none becomes NOT_FOUND rather than an invented average.
    """
    if type(trials) is not int or trials < 1:
        raise ValueError("Trials must be a positive integer")

    home_games = context.require_history(home_team_id)
    away_games = context.require_history(away_team_id)

    home_mean = blended_expected_score(
        average_points_scored(home_team_id, home_games),
        average_points_allowed(away_team_id, away_games),
    )
    away_mean = blended_expected_score(
        average_points_scored(away_team_id, away_games),
        average_points_allowed(home_team_id, home_games),
    )

    home_wins = 0
    for _ in range(trials):
        # Scores can't be negative in a real game.
        home_score = max(rng.gauss(home_mean, DEFAULT_SCORE_STANDARD_DEVIATION), 0)
        away_score = max(rng.gauss(away_mean, DEFAULT_SCORE_STANDARD_DEVIATION), 0)
        if home_score > away_score:
            home_wins += 1

    return SimulationCounts(
        home_wins=home_wins,
        away_wins=trials - home_wins,
        home_expected_score=home_mean,
        away_expected_score=away_mean,
    )
