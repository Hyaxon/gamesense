"""Pre-game views must contain only strictly earlier games."""

from datetime import UTC, datetime

from gamesense_prediction.backtesting import GameTimeline

from .conftest import GAMES_2024, GAMES_2025, A, B, C, D, season_context


def test_view_holds_only_strictly_earlier_games(contexts):
    timeline = GameTimeline(contexts)
    g1, g2, g3, g4, g5 = GAMES_2024
    g6, g7, g8, g9 = GAMES_2025

    def history(game):
        return [g.id for g in timeline.context_before(game).completed_games]

    assert history(g3) == [g1.id, g2.id]
    assert history(g5) == [g1.id, g2.id, g3.id, g4.id]
    # The next season sees all of the previous one.
    assert history(g6) == [g.id for g in GAMES_2024]
    assert history(g8) == [g.id for g in GAMES_2024] + [g6.id, g7.id]
    assert g9.id not in history(g9)


def test_games_at_the_same_instant_never_see_each_other(contexts):
    timeline = GameTimeline(contexts)
    g1, g2 = GAMES_2024[:2]  # same kickoff time

    assert timeline.context_before(g1).completed_games == ()
    assert timeline.context_before(g2).completed_games == ()


def test_view_is_a_fresh_season_context_without_ratings(contexts):
    timeline = GameTimeline(contexts)
    target = GAMES_2025[0]

    view = timeline.context_before(target)

    assert view.season == 2025
    assert view.ratings == {}
    assert set(view.teams) == {A, B, C, D}
    assert view.data_snapshot_id == f"backtest-{target.id}"
    assert view.provenance["targetGameId"] == target.id


def test_games_are_merged_across_contexts_without_duplicates(contexts):
    overlap = season_context(2025, GAMES_2025[:2])  # repeats two games
    timeline = GameTimeline([*contexts, overlap])

    assert len(timeline.games) == 9


def test_select_filters_by_season_and_inclusive_dates(contexts):
    timeline = GameTimeline(contexts)
    day = datetime(2025, 9, 13, tzinfo=UTC)

    assert [g.id for g in timeline.select(seasons={2025})] == [g.id for g in GAMES_2025]
    assert [g.id for g in timeline.select(start=day, end=day)] == [
        g.id for g in GAMES_2025[2:]
    ]
    assert [g.id for g in timeline.select(end=datetime(2024, 9, 1, tzinfo=UTC))] == [
        g.id for g in GAMES_2024[:2]
    ]
    assert list(timeline.select(seasons={1999})) == []
