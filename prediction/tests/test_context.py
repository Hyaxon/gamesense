"""History semantics, snapshot immutability, and explicit missing-data behavior."""

import json
from dataclasses import FrozenInstanceError

import pytest

from gamesense_prediction.context import MissingContextError
from gamesense_prediction.execution_validation import ContractValidationError
from gamesense_prediction.snapshots import (
    FileSnapshotProvider,
    InMemorySnapshotProvider,
    context_from_wire,
)

from .conftest import AWAY, HOME, NEW_TEAM, UNKNOWN


def test_history_order_records_and_ratings(snapshots):
    context = snapshots.get("development-2025", 2025)
    assert [game.scheduled_at for game in context.completed_games] == [
        "2025-08-29T00:00:00Z",
        "2025-08-31T00:00:00Z",
    ]
    assert len(context.require_history(HOME)) == 2
    assert context.record_for(HOME).wins == 1
    assert context.record_for(HOME).ties == 1
    assert context.record_for(AWAY).losses == 1
    assert context.record_for(NEW_TEAM).wins == 0
    assert context.games_for(NEW_TEAM) == ()
    assert context.require_rating(HOME, "fixture_rating") == 1520


@pytest.mark.parametrize(
    "lookup",
    [
        lambda c: c.require_team(UNKNOWN),
        lambda c: c.require_history(NEW_TEAM),
        lambda c: c.require_rating(NEW_TEAM, "elo"),
        lambda c: c.require_rating(HOME, "missing"),
    ],
)
def test_missing_data_is_explicit(snapshots, lookup):
    with pytest.raises(MissingContextError):
        lookup(snapshots.get("development-2025", 2025))


def test_missing_season_does_not_fall_back(snapshots):
    with pytest.raises(MissingContextError):
        snapshots.get("development-2025", 2024)
    with pytest.raises(MissingContextError):
        snapshots.get("other", 2025)


def test_deep_immutability_and_caller_isolation(snapshot_payload):
    provider = InMemorySnapshotProvider([snapshot_payload])
    context = provider.get("development-2025", 2025)
    snapshot_payload["ratings"][HOME]["fixture_rating"] = 0
    snapshot_payload["provenance"]["notes"].append("changed")
    snapshot_payload["games"][0]["homeScore"] = 100
    assert context.require_rating(HOME, "fixture_rating") == 1520
    assert len(context.provenance["notes"]) == 1
    with pytest.raises(TypeError):
        context.ratings[HOME]["fixture_rating"] = 0
    with pytest.raises(TypeError):
        context.teams[HOME] = context.teams[AWAY]
    with pytest.raises(FrozenInstanceError):
        context.completed_games[0].home_score = 0


def test_files_are_loaded_once(tmp_path, snapshot_payload):
    path = tmp_path / "snapshot.json"
    path.write_text(json.dumps(snapshot_payload))
    provider = FileSnapshotProvider([path])
    path.write_text("invalid modified content")
    assert (
        provider.get("development-2025", 2025).require_team(HOME).name == "Fixture Home"
    )


def test_duplicate_snapshot_rejected(snapshot_payload):
    with pytest.raises(ValueError, match="Duplicate snapshot"):
        InMemorySnapshotProvider([snapshot_payload, snapshot_payload])


@pytest.mark.parametrize(
    "mutation",
    [
        lambda p: p["teams"].append(p["teams"][0]),
        lambda p: p["games"].append(p["games"][0]),
        lambda p: p["games"][0].update(season=2024),
        lambda p: p["games"][0].update(homeScore=None),
        lambda p: p["games"][0].update(winnerTeamId=HOME),
        lambda p: p["games"][0].update(homeTeamId=UNKNOWN),
        lambda p: p["games"][0].update(homeTeamId=HOME, awayTeamId=HOME),
        lambda p: p["games"][0].pop("isNeutralSite"),
        lambda p: p["games"][2].update(homeScore=0, awayScore=0),
        lambda p: p["ratings"].update({UNKNOWN: {"rating": 1}}),
        lambda p: p["ratings"][HOME].update(rating=float("nan")),
    ],
)
def test_invalid_snapshots_rejected(snapshot_payload, mutation):
    mutation(snapshot_payload)
    with pytest.raises(ContractValidationError):
        context_from_wire(snapshot_payload)


def test_equal_timestamp_uses_id_order(snapshot_payload):
    snapshot_payload["games"][0]["scheduledAt"] = snapshot_payload["games"][1][
        "scheduledAt"
    ]
    context = context_from_wire(snapshot_payload)
    assert [game.id for game in context.completed_games] == sorted(
        game.id for game in context.completed_games
    )


def test_history_order_uses_instants_not_timestamp_strings(snapshot_payload):
    snapshot_payload["games"][0]["scheduledAt"] = "2025-08-29T00:30:00+02:00"
    snapshot_payload["games"][1]["scheduledAt"] = "2025-08-28T23:30:00Z"
    context = context_from_wire(snapshot_payload)
    assert context.completed_games[0].id == snapshot_payload["games"][0]["id"]
