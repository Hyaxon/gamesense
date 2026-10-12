# SimpleNamespace lets us make quick fake game objects with whatever fields we want, without needing the real game class.
from types import SimpleNamespace

import pytest

from gamesense_prediction.models.elo.algorithm import (
    build_ratings,
    expected_home_win,
    predict_matchup,
)


# simulate game with only elo needed fields
def game(game_id, date, home, away, home_score, away_score, neutral=False):
    return SimpleNamespace(
        id=game_id,
        scheduled_at=date,
        home_team_id=home,
        away_team_id=away,
        home_score=home_score,
        away_score=away_score,
        is_neutral_site=neutral,
    )


# same rating and no home bonus should be  50/50
def test_equal_teams_on_neutral_site_is_a_coin_flip():
    assert expected_home_win(1500, 1500, True) == 0.5


# same rating but at home so the home team favored
def test_home_field_helps_home_team():
    assert expected_home_win(1500, 1500, False) > 0.5


# check that winning home team increases and losing team decreases
def test_home_win_moves_ratings_the_right_way():
    ratings = build_ratings([game("g1", 1, "OU", "TEXAS", 30, 20)])
    assert ratings["OU"] > 1500
    assert ratings["TEXAS"] < 1500


# make sure that what one team loses, the other gains
# roughly 3000 total
def test_ratings_only_move_points_between_teams():
    ratings = build_ratings([game("g1", 1, "OU", "TEXAS", 30, 20)])
    assert ratings["OU"] + ratings["TEXAS"] == pytest.approx(3000)


# make sure that game order doesn't affect answer
def test_games_are_replayed_in_date_order():
    early = game("g1", 1, "OU", "TEXAS", 30, 20)
    late = game("g2", 2, "TEXAS", "OU", 10, 27)
    assert build_ratings([late, early]) == build_ratings([early, late])


# check that games are only counted once
def test_duplicate_game_is_counted_once():
    g = game("g1", 1, "OU", "TEXAS", 30, 20)
    result = predict_matchup("OU", "TEXAS", [g], [g], True)
    single = build_ratings([g])
    assert result.home_elo == pytest.approx(single["OU"])


# make sure probability doesn't exceed 1
def test_probabilities_add_to_one():
    g = game("g1", 1, "OU", "TEXAS", 30, 20)
    result = predict_matchup("OU", "TEXAS", [g], [g], False)
    assert result.home_win_probability + result.away_probability == pytest.approx(1)
