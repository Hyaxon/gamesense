"""Runtime snapshot publication, validation, and concurrent writer behavior."""

from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

import pytest

from gamesense_prediction.context import MissingContextError
from gamesense_prediction.execution_validation import ContractValidationError
from gamesense_prediction.snapshots import InMemorySnapshotProvider

from .conftest import UNKNOWN


def test_publish_into_empty_provider(snapshot_payload):
    provider = InMemorySnapshotProvider()
    snapshot_id = snapshot_payload["dataSnapshotId"]
    with pytest.raises(MissingContextError):
        provider.get(snapshot_id, 2025)
    assert provider.publish(snapshot_payload) == snapshot_id
    context = provider.get(snapshot_id, 2025)
    snapshot_payload["provenance"]["notes"].append("changed")
    assert len(context.provenance["notes"]) == 1
    with pytest.raises(MissingContextError):
        provider.get(snapshot_id, 2024)


@pytest.mark.parametrize("is_schema_invalid", [True, False])
def test_invalid_publish_leaves_existing_snapshot_intact(
    snapshot_payload, is_schema_invalid
):
    provider = InMemorySnapshotProvider([snapshot_payload])
    original = provider.get(snapshot_payload["dataSnapshotId"], 2025)
    snapshot_payload["dataSnapshotId"] = "invalid-snapshot"
    if is_schema_invalid:
        del snapshot_payload["teams"]
    else:
        snapshot_payload["games"][0]["homeTeamId"] = UNKNOWN
    with pytest.raises(ContractValidationError):
        provider.publish(snapshot_payload)
    with pytest.raises(MissingContextError):
        provider.get("invalid-snapshot", 2025)
    assert provider.get(original.data_snapshot_id, 2025) is original


def test_duplicate_publish_preserves_original(snapshot_payload):
    provider = InMemorySnapshotProvider([snapshot_payload])
    original = provider.get(snapshot_payload["dataSnapshotId"], 2025)
    snapshot_payload["provenance"]["notes"].append("replacement")
    with pytest.raises(ValueError, match="Duplicate snapshot ID"):
        provider.publish(snapshot_payload)
    assert provider.get(original.data_snapshot_id, 2025) is original


@pytest.mark.parametrize("is_duplicate", [True, False])
def test_concurrent_publishes(snapshot_payload, is_duplicate):
    provider = InMemorySnapshotProvider()
    barrier = Barrier(8)

    def publish(index):
        snapshot_id = "shared" if is_duplicate else f"snapshot-{index}"
        payload = {**snapshot_payload, "dataSnapshotId": snapshot_id}
        barrier.wait(timeout=10)
        try:
            return provider.publish(payload)
        except ValueError as error:
            assert str(error) == "Duplicate snapshot ID"
            return None

    with ThreadPoolExecutor(max_workers=8) as executor:
        published = list(executor.map(publish, range(8)))
    successful = [snapshot_id for snapshot_id in published if snapshot_id is not None]
    assert len(successful) == (1 if is_duplicate else 8)
    for snapshot_id in successful:
        assert provider.get(snapshot_id, 2025).data_snapshot_id == snapshot_id
