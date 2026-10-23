# Xander Meadows
"""Glicko-2 matchup calculations over a validated prediction context."""

from dataclasses import dataclass

from ...context import PredictionContext
from .glicko2 import (
    Team,
    expected_score,
    mu_to_rating,
    rating_to_mu,
    rd_to_phi,
    update_rating,
)

HOME_ADVANTAGE_RATING_POINTS = 50.0
RATING_SCALE = 173.7178


@dataclass(frozen=True)
class MatchupPrediction:
    home_win_probability: float
    away_probability: float
    home_elo: float
    away_elo: float


def _update_team(
    team: Team,
    opponent: Team,
    result: float,
    *,
    team_home_advantage: float = 0.0,
    opponent_home_advantage: float = 0.0,
) -> None:
    """Update one team using both teams' ratings from before the game."""
    team_mu = rating_to_mu(team.rating) + team_home_advantage / RATING_SCALE
    opponent_mu = rating_to_mu(opponent.rating) + opponent_home_advantage / RATING_SCALE
    updated_mu, updated_phi = update_rating(
        team_mu,
        rd_to_phi(team.rd),
        [opponent_mu],
        [rd_to_phi(opponent.rd)],
        [result],
    )
    team.rating = mu_to_rating(updated_mu - team_home_advantage / RATING_SCALE)
    team.rd = updated_phi * RATING_SCALE


def predict_matchup(
    home_team_id: str,
    away_team_id: str,
    context: PredictionContext,
    is_neutral_site: bool,
) -> MatchupPrediction:
    """Rate all completed context games, then predict the requested matchup.

    The context is immutable, so ratings are kept in private ``Team`` objects.
    Historical games use their own neutral-site flag; the requested matchup
    uses ``is_neutral_site``.
    """
    context.require_team(home_team_id)
    context.require_team(away_team_id)
    teams = {team_id: Team() for team_id in context.teams}

    for game in context.completed_games:
        home = teams[game.home_team_id]
        away = teams[game.away_team_id]
        home_rating, home_rd = home.rating, home.rd
        away_rating, away_rd = away.rating, away.rd

        if game.winner_team_id == game.home_team_id:
            home_result = 1.0
        elif game.winner_team_id == game.away_team_id:
            home_result = 0.0
        else:
            home_result = 0.5

        advantage = (
            0.0 if game.is_neutral_site else HOME_ADVANTAGE_RATING_POINTS
        )
        home_snapshot = Team()
        home_snapshot.rating, home_snapshot.rd = home_rating, home_rd
        away_snapshot = Team()
        away_snapshot.rating, away_snapshot.rd = away_rating, away_rd
        _update_team(
            home,
            away_snapshot,
            home_result,
            team_home_advantage=advantage,
        )
        _update_team(
            away,
            home_snapshot,
            1.0 - home_result,
            opponent_home_advantage=advantage,
        )

    home = teams[home_team_id]
    away = teams[away_team_id]
    advantage = 0.0 if is_neutral_site else HOME_ADVANTAGE_RATING_POINTS
    home_mu = rating_to_mu(home.rating) + advantage / RATING_SCALE
    away_mu = rating_to_mu(away.rating)
    home_probability = expected_score(home_mu, away_mu, rd_to_phi(away.rd))

    return MatchupPrediction(
        home_win_probability=home_probability,
        away_probability=1.0 - home_probability,
        home_elo=home.rating,
        away_elo=away.rating,
    )
