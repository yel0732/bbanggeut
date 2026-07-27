"""Tests for RealtimeConnectionRegistry (T12)."""

import pytest

from app.session.realtime_connection_registry import (
    RealtimeConnectionEntry,
    RealtimeConnectionRegistry,
)


class _FakeHeartbeat:
    def __init__(self) -> None:
        self.stop_call_count = 0

    async def start(self, conversation_id: str, realtime_session_id: str, session_epoch: int) -> None:
        pass

    async def stop(self) -> None:
        self.stop_call_count += 1


class _FakePeerConnection:
    def __init__(self) -> None:
        self.close_call_count = 0

    async def accept_offer(self, sdp: str) -> str:
        return "v=0...answer"

    async def add_ice_candidate(self, candidate, sdp_mid, sdp_mline_index) -> None:
        pass

    def on_state_change(self, callback) -> None:
        pass

    async def close(self) -> None:
        self.close_call_count += 1


def _entry(
    conversation_id: str = "conv_1",
    realtime_session_id: str = "rt_1",
    session_epoch: int = 1,
) -> tuple[RealtimeConnectionEntry, _FakeHeartbeat, _FakePeerConnection]:
    heartbeat = _FakeHeartbeat()
    peer_connection = _FakePeerConnection()
    entry = RealtimeConnectionEntry(
        conversation_id=conversation_id,
        realtime_session_id=realtime_session_id,
        session_epoch=session_epoch,
        peer_connection=peer_connection,
        heartbeat=heartbeat,
    )
    return entry, heartbeat, peer_connection


@pytest.mark.asyncio
async def test_register_then_find_returns_the_entry() -> None:
    registry = RealtimeConnectionRegistry()
    entry, _, _ = _entry()

    await registry.register(entry)

    assert registry.find("conv_1") is entry


@pytest.mark.asyncio
async def test_find_missing_conversation_returns_none() -> None:
    registry = RealtimeConnectionRegistry()

    assert registry.find("conv_missing") is None


@pytest.mark.asyncio
async def test_remove_matching_realtime_session_id_drops_entry() -> None:
    registry = RealtimeConnectionRegistry()
    entry, _, _ = _entry()
    await registry.register(entry)

    await registry.remove("conv_1", "rt_1")

    assert registry.find("conv_1") is None


@pytest.mark.asyncio
async def test_remove_is_idempotent() -> None:
    registry = RealtimeConnectionRegistry()
    entry, _, _ = _entry()
    await registry.register(entry)

    await registry.remove("conv_1", "rt_1")
    await registry.remove("conv_1", "rt_1")  # second call is a no-op, no error

    assert registry.find("conv_1") is None


@pytest.mark.asyncio
async def test_remove_with_stale_realtime_session_id_is_ignored() -> None:
    registry = RealtimeConnectionRegistry()
    entry, _, _ = _entry(realtime_session_id="rt_current")
    await registry.register(entry)

    # A delayed cleanup call referencing an older realtimeSessionId must not
    # evict the current (newer) entry.
    await registry.remove("conv_1", "rt_stale")

    assert registry.find("conv_1") is entry


@pytest.mark.asyncio
async def test_remove_missing_conversation_does_nothing() -> None:
    registry = RealtimeConnectionRegistry()

    await registry.remove("conv_missing", "rt_1")  # must not raise


@pytest.mark.asyncio
async def test_register_evicts_and_closes_stale_entry_for_same_conversation() -> None:
    registry = RealtimeConnectionRegistry()
    stale_entry, stale_heartbeat, stale_peer_connection = _entry(realtime_session_id="rt_old")
    await registry.register(stale_entry)

    new_entry, _, _ = _entry(realtime_session_id="rt_new")
    await registry.register(new_entry)

    assert stale_heartbeat.stop_call_count == 1
    assert stale_peer_connection.close_call_count == 1
    assert registry.find("conv_1") is new_entry


@pytest.mark.asyncio
async def test_register_returns_true_on_success() -> None:
    registry = RealtimeConnectionRegistry()
    entry, _, _ = _entry()

    assert await registry.register(entry) is True


@pytest.mark.asyncio
async def test_register_with_lower_epoch_than_current_is_rejected_without_evicting() -> None:
    registry = RealtimeConnectionRegistry()
    current_entry, current_heartbeat, current_peer_connection = _entry(
        realtime_session_id="rt_new", session_epoch=2
    )
    await registry.register(current_entry)

    stale_entry, _, _ = _entry(realtime_session_id="rt_old", session_epoch=1)
    accepted = await registry.register(stale_entry)

    assert accepted is False
    assert registry.find("conv_1") is current_entry
    assert current_heartbeat.stop_call_count == 0
    assert current_peer_connection.close_call_count == 0


@pytest.mark.asyncio
async def test_different_conversations_do_not_interfere() -> None:
    registry = RealtimeConnectionRegistry()
    entry_1, heartbeat_1, _ = _entry(conversation_id="conv_1")
    entry_2, heartbeat_2, _ = _entry(conversation_id="conv_2")

    await registry.register(entry_1)
    await registry.register(entry_2)
    await registry.remove("conv_1", "rt_1")

    assert registry.find("conv_1") is None
    assert registry.find("conv_2") is entry_2
    assert heartbeat_1.stop_call_count == 0  # remove() is pure bookkeeping, no cleanup call
    assert heartbeat_2.stop_call_count == 0
