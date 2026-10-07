"""Execution metadata remains valid when the wall clock moves backwards."""

from uuid import UUID

from gamesense_prediction import execution_helpers
from gamesense_prediction.contracts import ExecutionKind
from gamesense_prediction.execution_helpers import ExecutionTimer, new_identifier


def test_generated_identifier_is_canonical_v4():
    value = new_identifier()
    assert UUID(value).version == 4
    assert str(UUID(value)) == value


def test_duration_uses_monotonic_clock(execution_request, monkeypatch):
    timer = ExecutionTimer(started_at="2025-09-02T00:00:00.000000Z", _started=10.0)
    monkeypatch.setattr(
        execution_helpers, "utc_now", lambda: "2025-09-01T00:00:00.000000Z"
    )
    monkeypatch.setattr(execution_helpers, "perf_counter", lambda: 10.25)
    metadata = timer.metadata(execution_request)
    assert metadata.duration_milliseconds == 250
    assert metadata.completed_at == metadata.started_at
    assert "seed" not in metadata.to_wire()


def test_generated_seed_is_recorded(execution_request):
    from dataclasses import replace

    execution_request = replace(
        execution_request, execution_kind=ExecutionKind.STOCHASTIC
    )
    assert ExecutionTimer().metadata(execution_request, seed=0).seed == 0
