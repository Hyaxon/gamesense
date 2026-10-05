"""Exercise real execution boundaries, including corrupt adapter output."""

from dataclasses import replace

import pytest

from gamesense_prediction.contracts import (
    ErrorCode,
    ErrorResponse,
    ExecutionKind,
    ModelExecutionError,
    PredictionMethod,
    SupportingScore,
)
from gamesense_prediction.execution_helpers import (
    ExecutionFailureError,
    execution_error,
)
from gamesense_prediction.registry import ModelRegistration, ModelRegistry
from gamesense_prediction.runner import PredictionRunner

from .conftest import HOME, NEW_TEAM, UNKNOWN
from .contract_checks import (
    check_adapter_contract,
    check_deterministic_repeatability,
    check_seeded_repeatability,
)


def assert_code(response, code):
    error = response if isinstance(response, ErrorResponse) else response.error
    assert error.code == code
    assert "prediction" not in response.to_wire()
    assert "simulation" not in response.to_wire()


@pytest.mark.parametrize("kind", list(ExecutionKind))
def test_all_execution_kinds(runner, execution_request, kind):
    execution_request = replace(
        execution_request,
        execution_kind=kind,
        trials=10 if kind == ExecutionKind.SIMULATION else None,
    )
    result = check_adapter_contract(runner, execution_request)
    assert (result.simulation is not None) == (kind == ExecutionKind.SIMULATION)
    assert result.configuration == {"homeProbability": 0.6}
    assert result.metadata.duration_milliseconds >= 0


def test_repeatability_and_isolation(runner, execution_request):
    check_deterministic_repeatability(runner, execution_request)
    context = runner.snapshots.get("development-2025", 2025)
    history = context.completed_games
    check_adapter_contract(
        runner, replace(execution_request, configuration={"homeProbability": 0.2})
    )
    result = check_adapter_contract(runner, execution_request)
    assert result.prediction.confidence == 0.6
    assert context.completed_games == history


@pytest.mark.parametrize("trials", [1, 100000])
def test_trial_boundaries(runner, execution_request, trials):
    result = check_adapter_contract(
        runner,
        replace(
            execution_request, execution_kind=ExecutionKind.SIMULATION, trials=trials
        ),
    )
    assert result.simulation.trials == trials


@pytest.mark.parametrize("probability", [0, 0.5, 1])
def test_probability_boundaries_and_tie_policy(runner, execution_request, probability):
    result = check_adapter_contract(
        runner,
        replace(
            execution_request,
            execution_kind=ExecutionKind.SIMULATION,
            trials=10,
            configuration={"homeProbability": probability},
        ),
    )
    expected = (
        execution_request.matchup.home_team_id
        if probability >= 0.5
        else (execution_request.matchup.away_team_id)
    )
    assert result.prediction.predicted_winner_team_id == expected


@pytest.mark.parametrize("payload", [None, [], "invalid", {}, {"executionId": "bad"}])
def test_malformed_boundary_returns_plain_error(runner, payload):
    result = runner.execute(payload)
    assert isinstance(result, ErrorResponse)
    assert_code(result, ErrorCode.VALIDATION_ERROR)


def test_unknown_model(runner, execution_request):
    assert_code(
        runner.execute(replace(execution_request, model_id="not-installed")),
        ErrorCode.NOT_FOUND,
    )


def test_unknown_snapshot_and_wrong_season(runner, execution_request):
    assert_code(
        runner.execute(replace(execution_request, data_snapshot_id="missing")),
        ErrorCode.NOT_FOUND,
    )
    assert_code(
        runner.execute(
            replace(
                execution_request,
                matchup=replace(execution_request.matchup, season=2024),
            )
        ),
        ErrorCode.NOT_FOUND,
    )


def test_missing_and_duplicate_team(runner, execution_request):
    assert_code(
        runner.execute(
            replace(
                execution_request,
                matchup=replace(execution_request.matchup, home_team_id=UNKNOWN),
            )
        ),
        ErrorCode.NOT_FOUND,
    )
    assert_code(
        runner.execute(
            replace(
                execution_request,
                matchup=replace(execution_request.matchup, away_team_id=HOME),
            )
        ),
        ErrorCode.VALIDATION_ERROR,
    )


def test_noncanonical_ids_rejected(runner, execution_request):
    result = runner.execute(
        replace(execution_request, execution_id="00000000-0000-0000-0000-000000000000")
    )
    assert isinstance(result, ErrorResponse)
    assert_code(result, ErrorCode.VALIDATION_ERROR)


def test_unsupported_seed(runner, execution_request):
    assert_code(
        runner.execute(
            replace(execution_request, execution_kind=ExecutionKind.STOCHASTIC, seed=42)
        ),
        ErrorCode.UNSUPPORTED_METHOD,
    )


def test_seeded_adapter_contract(adapter, example_module, snapshots, execution_request):
    # Synthetic test adapter only: declares its fixed output repeatable for every seed.
    registry = ModelRegistry(
        [
            ModelRegistration(
                adapter=adapter,
                supports_seed=True,
                configuration_schema=example_module.CONFIGURATION_SCHEMA,
            )
        ],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    runner = PredictionRunner(registry, snapshots)
    execution_request = replace(
        execution_request, execution_kind=ExecutionKind.SIMULATION, trials=10, seed=42
    )
    check_seeded_repeatability(runner, execution_request)


def test_unsupported_kind(
    adapter, example_module, snapshots, execution_request, monkeypatch
):
    descriptor = replace(
        adapter.get_descriptor(),
        supported_execution_kinds=(ExecutionKind.DETERMINISTIC,),
    )
    monkeypatch.setattr(adapter, "get_descriptor", lambda: descriptor)
    registry = ModelRegistry(
        [
            ModelRegistration(
                adapter=adapter,
                configuration_schema=example_module.CONFIGURATION_SCHEMA,
            )
        ],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    runner = PredictionRunner(registry, snapshots)
    assert_code(
        runner.execute(
            replace(execution_request, execution_kind=ExecutionKind.STOCHASTIC)
        ),
        ErrorCode.UNSUPPORTED_METHOD,
    )


@pytest.mark.parametrize(
    "configuration",
    [
        {"unknown": True},
        {"homeProbability": "0.6"},
        {"homeProbability": True},
        {"homeProbability": 2},
    ],
)
def test_configuration_validation_before_execution(
    runner, execution_request, monkeypatch, configuration
):
    calls = []
    monkeypatch.setattr(
        runner.registry.get("example-v1").adapter,
        "execute",
        lambda *_: calls.append(True),
    )
    result = runner.execute(replace(execution_request, configuration=configuration))
    assert_code(result, ErrorCode.VALIDATION_ERROR)
    assert not calls


def test_model_without_configuration_schema(
    adapter, snapshots, execution_request, monkeypatch
):
    descriptor = replace(adapter.get_descriptor(), configuration_schema_id=None)
    monkeypatch.setattr(adapter, "get_descriptor", lambda: descriptor)
    registry = ModelRegistry(
        [ModelRegistration(adapter=adapter)],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    runner = PredictionRunner(registry, snapshots)
    original = adapter.execute
    monkeypatch.setattr(
        adapter, "execute", lambda *args: replace(original(*args), configuration=None)
    )
    check_adapter_contract(runner, execution_request)
    check_adapter_contract(runner, replace(execution_request, configuration={}))
    assert_code(
        runner.execute(replace(execution_request, configuration={"setting": 1})),
        ErrorCode.VALIDATION_ERROR,
    )


def test_missing_required_history(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter
    monkeypatch.setattr(
        adapter, "execute", lambda _r, context: context.require_history(NEW_TEAM)
    )
    assert_code(runner.execute(execution_request), ErrorCode.NOT_FOUND)


def test_expected_failure_and_returned_error(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter

    def fail(*_args):
        raise ExecutionFailureError(
            ErrorCode.PREDICTION_FAILED, "Numerical calculation failed."
        )

    monkeypatch.setattr(adapter, "execute", fail)
    result = runner.execute(execution_request)
    assert_code(result, ErrorCode.PREDICTION_FAILED)
    assert isinstance(result, ModelExecutionError)
    monkeypatch.setattr(
        adapter,
        "execute",
        lambda *_: execution_error(
            execution_request, ErrorCode.PREDICTION_FAILED, "No converged result."
        ),
    )
    assert_code(runner.execute(execution_request), ErrorCode.PREDICTION_FAILED)


def test_unexpected_error_is_safe_and_logged(
    runner, execution_request, monkeypatch, caplog
):
    def fail(*_args):
        raise RuntimeError("SECRET/internal/file")

    monkeypatch.setattr(runner.registry.get("example-v1").adapter, "execute", fail)
    result = runner.execute(execution_request)
    assert_code(result, ErrorCode.INTERNAL_ERROR)
    assert "SECRET" not in str(result.to_wire())
    assert execution_request.execution_id in caplog.text


def test_inconsistent_provider_is_internal_failure(
    runner, execution_request, monkeypatch
):
    context = runner.snapshots.get("development-2025", 2025)
    monkeypatch.setattr(
        runner.snapshots, "get", lambda *_: replace(context, season=2024)
    )
    assert_code(runner.execute(execution_request), ErrorCode.INTERNAL_ERROR)


@pytest.mark.parametrize(
    "mutation",
    [
        lambda r: replace(r, execution_id=UNKNOWN),
        lambda r: replace(r, model=replace(r.model, version="changed")),
        lambda r: replace(r, matchup=replace(r.matchup, season=2024)),
        lambda r: replace(
            r, prediction=replace(r.prediction, predicted_winner_team_id=UNKNOWN)
        ),
        lambda r: replace(
            r, prediction=replace(r.prediction, method=PredictionMethod.ELO)
        ),
        lambda r: replace(r, prediction=replace(r.prediction, confidence=0.4)),
        lambda r: replace(r, prediction=replace(r.prediction, confidence=float("nan"))),
        lambda r: replace(r, metadata=replace(r.metadata, data_snapshot_id="other")),
        lambda r: replace(
            r, metadata=replace(r.metadata, completed_at="2000-01-01T00:00:00Z")
        ),
        lambda r: replace(r, metadata=replace(r.metadata, duration_milliseconds=-1)),
        lambda r: replace(r, configuration=None),
        lambda r: replace(r, configuration={"unknown": 1}),
        lambda r: replace(
            r, supporting_scores=(SupportingScore(team_id=UNKNOWN, name="x", value=1),)
        ),
        lambda r: replace(
            r, supporting_scores=(SupportingScore(team_id=HOME, name="x", value=1),) * 2
        ),
        lambda r: replace(
            r,
            supporting_scores=(
                SupportingScore(team_id=HOME, name="x", value=float("inf")),
            ),
        ),
        lambda r: r.to_wire(),
    ],
)
def test_invalid_adapter_outputs_are_internal(
    runner, execution_request, monkeypatch, mutation
):
    adapter = runner.registry.get("example-v1").adapter
    original = adapter.execute
    monkeypatch.setattr(adapter, "execute", lambda *args: mutation(original(*args)))
    assert_code(runner.execute(execution_request), ErrorCode.INTERNAL_ERROR)


@pytest.mark.parametrize(
    "mutation",
    [
        lambda r: replace(r, simulation=None),
        lambda r: replace(r, simulation=replace(r.simulation, trials=1)),
        lambda r: replace(
            r, simulation=replace(r.simulation, away_win_probability=0.7)
        ),
        lambda r: replace(
            r, simulation=replace(r.simulation, method=PredictionMethod.ELO)
        ),
        lambda r: replace(r, prediction=replace(r.prediction, confidence=0.8)),
        lambda r: replace(
            r,
            prediction=replace(
                r.prediction, predicted_winner_team_id=r.matchup.away_team_id
            ),
        ),
        lambda r: replace(r, simulation=replace(r.simulation, id=r.prediction.id)),
    ],
)
def test_invalid_simulation_outputs(runner, execution_request, monkeypatch, mutation):
    adapter = runner.registry.get("example-v1").adapter
    original = adapter.execute
    monkeypatch.setattr(adapter, "execute", lambda *args: mutation(original(*args)))
    execution_request = replace(
        execution_request, execution_kind=ExecutionKind.SIMULATION, trials=10
    )
    assert_code(runner.execute(execution_request), ErrorCode.INTERNAL_ERROR)


def test_away_cannot_win_equal_odds(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter
    original = adapter.execute
    monkeypatch.setattr(
        adapter,
        "execute",
        lambda *args: replace(
            original(*args),
            prediction=replace(
                original(*args).prediction,
                predicted_winner_team_id=execution_request.matchup.away_team_id,
            ),
        ),
    )
    assert_code(
        runner.execute(
            replace(execution_request, configuration={"homeProbability": 0.5})
        ),
        ErrorCode.INTERNAL_ERROR,
    )


def test_changed_descriptor_after_registration(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter
    changed = replace(adapter.get_descriptor(), version="changed")
    monkeypatch.setattr(adapter, "get_descriptor", lambda: changed)
    assert_code(runner.execute(execution_request), ErrorCode.INTERNAL_ERROR)


def test_invalid_error_correlation(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter
    invalid = execution_error(
        execution_request, ErrorCode.PREDICTION_FAILED, "Failure."
    )
    monkeypatch.setattr(
        adapter, "execute", lambda *_: replace(invalid, execution_id=UNKNOWN)
    )
    assert_code(runner.execute(execution_request), ErrorCode.INTERNAL_ERROR)


def test_configuration_must_reflect_explicit_settings(
    runner, execution_request, monkeypatch
):
    adapter = runner.registry.get("example-v1").adapter
    original = adapter.execute
    monkeypatch.setattr(
        adapter,
        "execute",
        lambda *args: replace(original(*args), configuration={"homeProbability": 0.9}),
    )
    assert_code(
        runner.execute(
            replace(execution_request, configuration={"homeProbability": 0.7})
        ),
        ErrorCode.INTERNAL_ERROR,
    )


def test_mismatched_actual_seed_is_internal(
    adapter, example_module, snapshots, execution_request, monkeypatch
):
    registry = ModelRegistry(
        [
            ModelRegistration(
                adapter=adapter,
                supports_seed=True,
                configuration_schema=example_module.CONFIGURATION_SCHEMA,
            )
        ],
        {PredictionMethod.HOME_GROWN: "example-v1"},
    )
    original = adapter.execute

    def corrupt(*args):
        response = original(*args)
        return replace(response, metadata=replace(response.metadata, seed=43))

    monkeypatch.setattr(adapter, "execute", corrupt)
    runner = PredictionRunner(registry, snapshots)
    assert_code(
        runner.execute(
            replace(execution_request, execution_kind=ExecutionKind.STOCHASTIC, seed=42)
        ),
        ErrorCode.INTERNAL_ERROR,
    )


def test_valid_negative_diagnostic_score(runner, execution_request, monkeypatch):
    adapter = runner.registry.get("example-v1").adapter
    original = adapter.execute
    monkeypatch.setattr(
        adapter,
        "execute",
        lambda *args: replace(
            original(*args),
            supporting_scores=(
                SupportingScore(
                    team_id=HOME, name="diagnostic", value=-1, unit="points"
                ),
            ),
        ),
    )
    result = check_adapter_contract(runner, execution_request)
    assert result.supporting_scores[0].value == -1
