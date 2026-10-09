"""Elo calculations, independent of the wire contract and runner."""

import math
from dataclasses import dataclass

K_FACTOR = 20  # how much rating move after a game
HOME_FIELD_BONUS = 60  # elo points added to the home team
STARTING_ELO = 1500  # rating every new team will begin with
MAX_MARGIN_MULTIPLIER = 3  # caps max victory bonus


@dataclass(frozen=True)
class EloResult:
    home_elo: float
    away_elo: float
    home_win_probability: float

    # since there are only two outcomes (win/lose), away team's chance is just 1 minus the home team's
    @property
    def away_probability(self) -> float:
        return 1 - self.home_win_probability


# turn two ratings into the home team's chance of winning
def expected_home_win(home_elo, away_elo, is_neutral_site) -> float:
    bonus = (
        0 if is_neutral_site else HOME_FIELD_BONUS
    )  # only give the home bonus if the game isn't at a neutral site
    rating_gap = away_elo - (
        home_elo + bonus
    )  # rating gap after giving the home team its bonus
    return 1 / (1 + 10 ** (rating_gap / 400))  # standard elo formula


# replay completed games in date order and return {team_id: elo}
def build_ratings(games) -> dict:
    team_elo = {}  # team id -> current rating

    # oldest game first, because a team's rating should only reflect games played before
    for g in sorted(games, key=lambda g: g.scheduled_at):
        home_elo = team_elo.get(g.home_team_id, STARTING_ELO)
        away_elo = team_elo.get(g.away_team_id, STARTING_ELO)

        # what the model expected before the game was played
        expected = expected_home_win(home_elo, away_elo, g.is_neutral_site)

        # what actually happened, (1 = win, 0 = loss)
        home_won = 1 if g.home_score > g.away_score else 0

        # bigger wins count for more and min() caps it
        margin = abs(g.home_score - g.away_score)
        margin_multiplier = min(math.log(margin + 1), MAX_MARGIN_MULTIPLIER)

        # (home_won - expected) is how surprising the result was
        elo_change = K_FACTOR * margin_multiplier * (home_won - expected)

        # whatever the home team gains, the away team loses
        team_elo[g.home_team_id] = home_elo + elo_change
        team_elo[g.away_team_id] = away_elo - elo_change
    return team_elo


# build ratings from game history, then predict one matchup
def predict_matchup(
    home_team_id: str,
    away_team_id: str,
    games,  # every completed game in snapshot
    is_neutral_site: bool,
) -> EloResult:

    # replay everything to get current ratings, then pull out our two teams
    team_elo = build_ratings(games)
    home_elo = team_elo[home_team_id]
    away_elo = team_elo[away_team_id]

    return EloResult(
        home_elo=home_elo,
        away_elo=away_elo,
        home_win_probability=expected_home_win(home_elo, away_elo, is_neutral_site),
    )
