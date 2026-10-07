"""Pregame feature correctness and real-library prediction contract checks."""

from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import numpy as np
import pytest
from xgboost import XGBClassifier

from gamesense_prediction.context import MissingContextError
from gamesense_prediction.contracts import (
    ErrorCode,
    ExecutionKind,
    ModelExecutionError,
    PredictionMethod,
)
from gamesense_prediction.models.xgboost import XGBoostModel
from gamesense_prediction.models.xgboost.algorithm import (
    build_training_data,
    matchup_features,
    predict_matchup,
    team_features,
    train_model,
)
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner
from gamesense_prediction.snapshots import FileSnapshotProvider

from ..conftest import AWAY, HOME, NEW_TEAM, UNKNOWN
from ..contract_checks import check_adapter_contract, check_deterministic_repeatability


@pytest.fixture
def training_context():
    fixture = Path(__file__).parents[1] / "fixtures/xgboost-poc-2025.json"
    return FileSnapshotProvider([fixture]).get("xgboost-poc-2025", 2025)


@pytest.fixture
def classifier(training_context):
    return train_model(training_context)


@pytest.fixture
def xgboost_runner(classifier, snapshots):
    registry = ModelRegistry(
        [ModelRegistration(adapter=XGBoostModel(classifier))],
        {PredictionMethod.MACHINE_LEARNING: "xgboost-v1"},
    )
    return PredictionRunner(registry, snapshots)


def test_features_and_neutral_site(snapshots):
    context = snapshots.get("development-2025", 2025)
    home = context.require_history(HOME)
    away = context.require_history(AWAY)
    assert team_features(HOME, home) == (21.0, 17.5, 0.75)
    assert team_features(AWAY, away) == (17.5, 21.0, 0.25)
    assert matchup_features(HOME, AWAY, home, away, True) == (
        21.0,
        17.5,
        0.75,
        17.5,
        21.0,
        0.25,
        0.0,
    )
    assert matchup_features(HOME, AWAY, home, away, False)[-1] == 1.0


def test_training_rows_use_only_earlier_history(training_context):
    features, labels = build_training_data(training_context)
    # First game warms up both teams, and one later tied game has no label.
    assert features.shape == (22, 7)
    assert set(labels.tolist()) == {0, 1}
    np.testing.assert_array_equal(features[0], [14, 28, 0, 28, 14, 1, 1])
    assert labels[0] == 0
    # Changing the predicted game's score cannot change its own feature row.
    games = list(training_context.completed_games)
    games[1] = replace(games[1], home_score=999, winner_team_id=games[1].home_team_id)
    changed_features, changed_labels = build_training_data(
        replace(training_context, completed_games=tuple(games))
    )
    np.testing.assert_array_equal(features[0], changed_features[0])
    assert changed_labels[0] == 1
    # Appending future data also cannot change any existing training row.
    future = replace(games[-1], id=UNKNOWN, scheduled_at="2025-09-01T00:00:00Z")
    expanded, _ = build_training_data(
        replace(
            training_context,
            completed_games=(*training_context.completed_games, future),
        )
    )
    np.testing.assert_array_equal(features, expanded[: len(features)])


def test_same_time_games_do_not_leak(training_context):
    first, second = training_context.completed_games[:2]
    second = replace(second, scheduled_at=first.scheduled_at)
    features, labels = build_training_data(
        replace(training_context, completed_games=(first, second))
    )
    assert features.shape == (0, 7)
    assert labels.size == 0


@pytest.mark.parametrize("empty", [False, True])
def test_reject_insufficient_training_data(snapshots, empty):
    context = snapshots.get("development-2025", 2025)
    if empty:
        context = replace(context, completed_games=())
    with pytest.raises(ValueError, match="both home and away wins"):
        train_model(context)


@pytest.mark.parametrize("extension", ["json", "ubj"])
def test_package_training_prediction_and_save_load(
    classifier, training_context, tmp_path, extension
):
    assert isinstance(classifier, XGBClassifier)
    home_probability = predict_matchup(classifier, HOME, AWAY, training_context, True)
    away_probability = predict_matchup(classifier, AWAY, HOME, training_context, True)
    assert 0.5 < home_probability < 1
    assert 0 < away_probability < 0.5
    artifact = tmp_path / f"model.{extension}"
    trained = XGBoostModel.train_and_save(training_context, artifact)
    assert artifact.is_file()
    assert (
        predict_matchup(trained.model, HOME, AWAY, training_context, True)
        == home_probability
    )
    with patch.object(
        XGBClassifier, "fit", side_effect=AssertionError("Loading trained")
    ):
        restored = XGBoostModel.from_file(str(artifact))
    assert (
        predict_matchup(restored.model, HOME, AWAY, training_context, True)
        == home_probability
    )


def test_reject_unsupported_artifact_format(training_context, tmp_path):
    artifact = tmp_path / "model.pkl"
    with patch.object(
        XGBClassifier, "fit", side_effect=AssertionError("Invalid path trained")
    ):
        with pytest.raises(ValueError, match="must end in .json or .ubj"):
            XGBoostModel.train_and_save(training_context, artifact)
    assert not artifact.exists()


def test_loaded_adapter_reuses_classifier(
    training_context, tmp_path, snapshots, execution_request
):
    artifact = tmp_path / "model.json"
    XGBoostModel.train_and_save(training_context, str(artifact))
    adapter = XGBoostModel.from_file(artifact)
    runner = PredictionRunner(
        ModelRegistry(
            [ModelRegistration(adapter=adapter)],
            {PredictionMethod.MACHINE_LEARNING: "xgboost-v1"},
        ),
        snapshots,
    )
    request = replace(execution_request, model_id="xgboost-v1")
    with (
        patch.object(
            XGBClassifier, "fit", side_effect=AssertionError("Inference trained")
        ),
        patch.object(
            XGBClassifier,
            "load_model",
            side_effect=AssertionError("Inference reloaded"),
        ),
    ):
        check_adapter_contract(runner, request)
        check_deterministic_repeatability(runner, request)


def test_framework_contract_and_inference_only(xgboost_runner, execution_request):
    request = replace(execution_request, model_id="xgboost-v1")
    with patch.object(
        XGBClassifier, "fit", side_effect=AssertionError("Inference trained")
    ):
        response = check_adapter_contract(xgboost_runner, request)
        check_deterministic_repeatability(xgboost_runner, request)
    assert response.prediction.method == PredictionMethod.MACHINE_LEARNING
    assert response.simulation is None
    assert sum(score.value for score in response.supporting_scores) == pytest.approx(1)
    assert response.prediction.confidence == max(
        score.value for score in response.supporting_scores
    )


@pytest.mark.parametrize(
    "probability,winner,confidence", [(0.5, HOME, 0.5), (0.2, AWAY, 0.8)]
)
def test_winner_policy(
    xgboost_runner, execution_request, probability, winner, confidence
):
    request = replace(execution_request, model_id="xgboost-v1")
    with patch(
        "gamesense_prediction.models.xgboost.adapter.predict_matchup",
        return_value=probability,
    ):
        response = check_adapter_contract(xgboost_runner, request)
    assert response.prediction.predicted_winner_team_id == winner
    assert response.prediction.confidence == confidence


@pytest.mark.parametrize("team_id", [NEW_TEAM, UNKNOWN])
def test_required_history(xgboost_runner, execution_request, team_id):
    request = replace(
        execution_request,
        model_id="xgboost-v1",
        matchup=replace(execution_request.matchup, home_team_id=team_id),
    )
    response = xgboost_runner.execute(request)
    assert isinstance(response, ModelExecutionError)
    assert response.error.code == ErrorCode.NOT_FOUND
    with pytest.raises(MissingContextError):
        team_features(team_id, ())


def test_reject_simulation(xgboost_runner, execution_request):
    response = xgboost_runner.execute(
        replace(
            execution_request,
            model_id="xgboost-v1",
            execution_kind=ExecutionKind.SIMULATION,
            trials=10,
        )
    )
    assert isinstance(response, ModelExecutionError)
    assert response.error.code == ErrorCode.UNSUPPORTED_METHOD


def test_reject_configuration(xgboost_runner, execution_request):
    response = xgboost_runner.execute(
        replace(
            execution_request, model_id="xgboost-v1", configuration={"max_depth": 3}
        )
    )
    assert isinstance(response, ModelExecutionError)
    assert response.error.code == ErrorCode.VALIDATION_ERROR
