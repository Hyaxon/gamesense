"""End-to-end backtests: no future data, correct scoring, several models, filters."""

import importlib.util
import json
import math
from datetime import UTC, datetime
from pathlib import Path

import pytest

from gamesense_prediction.backtesting import Backtester, BacktestModel, Outcome
from gamesense_prediction.contracts import ExecutionKind, PredictionMethod
from gamesense_prediction.models.bradley_terry import BradleyTerryModel
from gamesense_prediction.models.elo import EloModel
from gamesense_prediction.models.glicko import Glicko2Model
from gamesense_prediction.models.monte_carlo import MonteCarloModel
from gamesense_prediction.registry import ModelRegistration, ModelRegistry

from .conftest import (
    GAMES_2024,
    GAMES_2025,
    FixedWinnerModel,
    SpyModel,
    registry_of,
)

ALL_GAMES = GAMES_2024 + GAMES_2025


def test_predictions_never_see_the_game_being_scored_or_anything_later(contexts):
    spy = SpyModel()

    Backtester(registry_of(spy)).run(contexts, [BacktestModel(model_id="spy")])

    assert len(spy.seen) == len(ALL_GAMES)
    for seen, target in zip(spy.seen, ALL_GAMES, strict=True):
        assert seen["target"] == target.id
        # Worked out here from the raw data, not by the code under test.
        earlier = [g.id for g in ALL_GAMES if g.scheduled_at < target.scheduled_at]
        assert seen["history"] == earlier
        assert target.id not in seen["history"]
        assert seen["ratings"] == {}
        assert seen["season"] == target.season


def test_scoring_against_hand_worked_numbers(contexts):
    registry = registry_of(
        FixedWinnerModel("always-home", pick_home=True, confidence=0.7),
        FixedWinnerModel("always-away", pick_home=False, confidence=0.7),
    )
    models = [
        BacktestModel(model_id="always-home"),
        BacktestModel(model_id="always-away"),
    ]

    result = Backtester(registry).run(contexts, models)

    home = result.summaries["always-home"]
    # Nine games, one tied. Home wins in games 1, 3, 6 and 9: 4 right, 4 wrong.
    assert (home.games, home.predicted, home.ties) == (9, 9, 1)
    assert (home.correct, home.incorrect, home.no_prediction) == (4, 4, 0)
    assert home.metrics["accuracy"] == pytest.approx(0.5)
    # Home wins were predicted at 0.7 (error 0.3), home losses at 0.7 (error 0.7).
    assert home.metrics["brier_score"] == pytest.approx((0.09 + 0.49) / 2)
    assert home.metrics["log_loss"] == pytest.approx(
        (-math.log(0.7) - math.log(0.3)) / 2
    )
    assert home.metrics["coverage"] == 1.0

    away = result.summaries["always-away"]
    assert (away.correct, away.incorrect) == (4, 4)
    assert away.metrics["brier_score"] == pytest.approx((0.49 + 0.09) / 2)


def test_each_model_is_scored_on_every_game_and_kept_separate(contexts):
    registry = registry_of(
        FixedWinnerModel("always-home", pick_home=True),
        FixedWinnerModel("always-away", pick_home=False),
    )
    models = [
        BacktestModel(model_id="always-home"),
        BacktestModel(model_id="always-away"),
    ]

    result = Backtester(registry).run(contexts, models)

    assert len(result.records) == len(ALL_GAMES) * 2
    for model in models:
        mine = [r for r in result.records if r.model_id == model.model_id]
        assert [r.game_id for r in mine] == [g.id for g in ALL_GAMES]
    tie = next(r for r in result.records if r.game_id == GAMES_2024[3].id)
    assert tie.actual_winner_team_id is None
    assert tie.outcome == Outcome.TIE


def test_season_and_date_range_pick_what_is_scored_but_history_still_counts(contexts):
    spy = SpyModel()
    backtester = Backtester(registry_of(spy))

    result = backtester.run(contexts, [BacktestModel(model_id="spy")], seasons=[2025])

    assert [r.game_id for r in result.records] == [g.id for g in GAMES_2025]
    # 2024 games are not scored, but they are history for 2025 games.
    assert len(spy.seen[0]["history"]) == len(GAMES_2024)

    spy.seen.clear()
    day = datetime(2025, 9, 13, tzinfo=UTC)
    ranged = backtester.run(
        contexts, [BacktestModel(model_id="spy")], start=day, end=day
    )
    assert [r.game_id for r in ranged.records] == [g.id for g in GAMES_2025[2:]]


def test_a_model_error_is_recorded_as_no_prediction_not_a_wrong_guess(contexts):
    registry = ModelRegistry(
        [ModelRegistration(adapter=EloModel())], {PredictionMethod.ELO: "elo-v1"}
    )

    result = Backtester(registry).run(contexts, [BacktestModel(model_id="elo-v1")])

    first = result.records[0]
    assert first.outcome == Outcome.NO_PREDICTION
    assert first.error_code == "NOT_FOUND"  # Elo needs history for both teams
    assert first.home_win_probability is None
    summary = result.summaries["elo-v1"]
    assert summary.no_prediction >= 1
    assert summary.metrics["coverage"] < 1.0
    assert summary.correct + summary.incorrect > 0


def test_real_models_run_through_the_same_workflow(contexts):
    registry = ModelRegistry(
        [
            ModelRegistration(adapter=EloModel()),
            ModelRegistration(adapter=Glicko2Model()),
            ModelRegistration(adapter=BradleyTerryModel()),
            ModelRegistration(adapter=MonteCarloModel(), supports_seed=True),
        ],
        {
            PredictionMethod.ELO: "elo-v1",
            PredictionMethod.GLICKO2: "glicko2",
            PredictionMethod.BRADLEY_TERRY: "bradley-terry-v1",
            PredictionMethod.MONTE_CARLO: "monte-carlo-v1",
        },
    )
    models = [
        BacktestModel(model_id="elo-v1"),
        BacktestModel(model_id="glicko2"),
        BacktestModel(model_id="bradley-terry-v1"),
        BacktestModel(
            model_id="monte-carlo-v1",
            execution_kind=ExecutionKind.SIMULATION,
            trials=200,
            seed=3,
        ),
    ]
    backtester = Backtester(registry)

    result = backtester.run(contexts, models)

    assert len(result.records) == len(ALL_GAMES) * 4
    assert set(result.summaries) == {m.model_id for m in models}
    for record in result.records:
        if record.home_win_probability is not None:
            assert 0.0 <= record.home_win_probability <= 1.0
    # Models differ in their no-history policy: Glicko-2 predicts the very first
    # game, Elo does not. Compare coverage along with accuracy.
    by_model = {(r.model_id, r.game_id): r for r in result.records}
    first = ALL_GAMES[0].id
    assert by_model[("glicko2", first)].outcome != Outcome.NO_PREDICTION
    assert by_model[("elo-v1", first)].outcome == Outcome.NO_PREDICTION
    # A seeded simulation repeats exactly.
    assert backtester.run(contexts, models).records == result.records


def test_results_serialize_to_json(contexts):
    registry = registry_of(FixedWinnerModel("always-home", pick_home=True))

    result = Backtester(registry).run(contexts, [BacktestModel(model_id="always-home")])

    wire = json.loads(json.dumps(result.to_wire(), allow_nan=False))
    assert wire["summaries"][0]["modelId"] == "always-home"
    assert wire["summaries"][0]["metrics"]["brierScore"] == pytest.approx(0.29)
    assert len(wire["records"]) == len(ALL_GAMES)
    assert wire["records"][0]["outcome"] == "CORRECT"
    assert "errorCode" not in wire["records"][0]


def test_bad_arguments_are_rejected_before_any_game_is_run(contexts):
    backtester = Backtester(registry_of(FixedWinnerModel("m", pick_home=True)))
    model = BacktestModel(model_id="m")

    with pytest.raises(ValueError, match="At least one model"):
        backtester.run(contexts, [])
    with pytest.raises(ValueError, match="Unknown model"):
        backtester.run(contexts, [BacktestModel(model_id="missing")])
    with pytest.raises(ValueError, match="only once"):
        backtester.run(contexts, [model, model])
    with pytest.raises(ValueError, match="timezone-aware"):
        backtester.run(contexts, [model], start=datetime(2025, 1, 1))


def test_no_matching_games_gives_empty_summaries(contexts):
    backtester = Backtester(registry_of(FixedWinnerModel("m", pick_home=True)))

    result = backtester.run(contexts, [BacktestModel(model_id="m")], seasons=[1999])

    assert result.records == ()
    assert result.summaries["m"].games == 0
    assert result.summaries["m"].metrics["accuracy"] is None


def load_script():
    path = Path(__file__).parents[2] / "src/scripts/run_backtest.py"
    spec = importlib.util.spec_from_file_location("run_backtest", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_script_backtests_a_snapshot_file_and_writes_json(tmp_path, capsys):
    script = load_script()
    snapshot = Path(__file__).parents[1] / "fixtures/xgboost-poc-2025.json"
    output = tmp_path / "result.json"

    exit_code = script.main(
        ["--snapshot", str(snapshot), "--models", "elo", "glicko2", "coin-flip"]
        + ["--seed", "1", "--output", str(output)]
    )

    assert exit_code == 0
    assert "elo-v1: 24 games" in capsys.readouterr().out
    saved = json.loads(output.read_text())
    assert [s["modelId"] for s in saved["summaries"]] == [
        "elo-v1",
        "glicko2",
        "coin-flip-v1",
    ]
    assert len(saved["records"]) == 24 * 3


def test_script_end_date_covers_the_whole_day():
    script = load_script()

    assert script.parse_bound("2025-09-13", is_end=False) == datetime(
        2025, 9, 13, tzinfo=UTC
    )
    last = script.parse_bound("2025-09-13", is_end=True)
    assert last.date().isoformat() == "2025-09-13"
    assert (last.hour, last.minute, last.second) == (23, 59, 59)
