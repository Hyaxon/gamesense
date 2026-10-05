"""Reusable checks for model authors; call these from algorithm-specific tests."""

from gamesense_prediction.contracts import ModelExecutionResult, response_from_wire
from gamesense_prediction.execution_validation import validate_response


def check_adapter_contract(runner, execution_request):
    response = runner.execute(execution_request)
    assert isinstance(response, ModelExecutionResult), response.to_wire()
    entry = runner.registry.get(execution_request.model_id)
    validate_response(
        execution_request,
        response,
        entry.descriptor,
        runner.registry.catalog,
        entry.configuration_validator,
    )
    assert response_from_wire(response.to_wire()) == response
    return response


def check_deterministic_repeatability(runner, execution_request):
    first = check_adapter_contract(runner, execution_request).prediction.to_wire()
    second = check_adapter_contract(runner, execution_request).prediction.to_wire()
    for payload in (first, second):
        payload.pop("id")
        payload.pop("generatedAt")
    assert first == second


def check_seeded_repeatability(runner, execution_request):
    """Use only when the adapter promises repeatability for this runtime."""
    assert execution_request.seed is not None
    first = check_adapter_contract(runner, execution_request)
    second = check_adapter_contract(runner, execution_request)
    assert first.prediction.predicted_winner_team_id == (
        second.prediction.predicted_winner_team_id
    )
    assert first.prediction.confidence == second.prediction.confidence
    if first.simulation is not None:
        assert first.simulation.home_win_probability == (
            second.simulation.home_win_probability
        )
        assert first.simulation.away_win_probability == (
            second.simulation.away_win_probability
        )
