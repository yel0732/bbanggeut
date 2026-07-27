"""Two concurrent Signaling connections must not leak state into each other.

jira/plan/DIARY-2-W06.md §12's remaining completion item — "서로 다른 두
세션의 Audio·이벤트가 섞이지 않는다" — was previously only argued from design
(``RealtimeConnectionRegistry``/``SessionEpochRegistry`` keyed by
``conversationId``). This test exercises it directly with two real aiortc
PeerConnections sharing the same registry/epoch-registry instances the real
app (``app/main.py``) hands to every ``SignalingConnection``, mirroring
tests/signaling/test_signaling_webrtc_integration.py's Fake/real split (only
the WebSocket transport and Ticket Verifier are Fakes).
"""

from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

import pytest
from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import AudioStreamTrack

from app.realtime.session_epoch_registry import SessionEpochRegistry
from app.realtime.ticket_verification import TicketVerificationResult
from app.session.realtime_connection_registry import RealtimeConnectionRegistry
from app.signaling.connection import SignalingConnection
from app.signaling.error_mapping import map_ticket_failure_code
from app.webrtc.peer_connection import AiortcPeerConnectionFactory

_LOGGER = logging.getLogger("test.signaling.dual_session_isolation")


class _FakeWebSocket:
    def __init__(self) -> None:
        self.sent: list[dict] = []

    async def send_json(self, data: dict) -> None:
        self.sent.append(data)

    async def receive_json(self) -> dict:  # pragma: no cover - driven manually
        raise NotImplementedError

    async def close(self, code: int = 1000) -> None:
        pass


class _FakeTicketVerifier:
    def __init__(self, result: TicketVerificationResult) -> None:
        self._result = result

    async def verify_and_consume(self, ticket: str) -> TicketVerificationResult:
        return self._result


class _FakeHeartbeat:
    def __init__(self) -> None:
        self.started_for: tuple[str, str, int] | None = None
        self.stopped = False

    async def start(
        self, conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> None:
        self.started_for = (conversation_id, realtime_session_id, session_epoch)

    async def stop(self) -> None:
        self.stopped = True


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


async def _collect_frames(track, sink: list) -> None:
    try:
        while True:
            sink.append(await track.recv())
    except Exception:  # noqa: BLE001 - test collector, track ending is expected on close
        pass


async def _connect(
    conversation_id: str,
    realtime_session_id: str,
    session_epoch: int,
    registry: RealtimeConnectionRegistry,
    epoch_registry: SessionEpochRegistry,
    factory: AiortcPeerConnectionFactory,
):
    react_client = RTCPeerConnection()
    react_client.addTrack(AudioStreamTrack())  # client's "mic" — deterministic silence generator
    channel = react_client.createDataChannel("conversation-events")

    received_frames: list = []

    @react_client.on("track")
    def _on_track(track) -> None:
        asyncio.ensure_future(_collect_frames(track, received_frames))

    websocket = _FakeWebSocket()
    heartbeat = _FakeHeartbeat()
    connection = SignalingConnection(
        websocket=websocket,
        ticket_verifier=_FakeTicketVerifier(
            TicketVerificationResult.success(conversation_id, realtime_session_id, session_epoch)
        ),
        peer_connection_factory=factory,
        registry=registry,
        heartbeat_factory=lambda: heartbeat,
        map_failure_code=map_ticket_failure_code,
        epoch_registry=epoch_registry,
        logger=_LOGGER,
    )

    await connection._handle_raw(
        {"type": "session.authenticate", "requestId": "req_auth", "ticket": "signed-ticket"}
    )
    assert websocket.sent[-1]["type"] == "session.authenticated"

    offer = await react_client.createOffer()
    await react_client.setLocalDescription(offer)
    await connection._handle_raw(
        {
            "type": "rtc.offer",
            "requestId": "req_offer",
            "sdp": {"type": "offer", "sdp": react_client.localDescription.sdp},
        }
    )
    answer_message = websocket.sent[-1]
    assert answer_message["type"] == "rtc.answer"
    await react_client.setRemoteDescription(
        RTCSessionDescription(sdp=answer_message["sdp"]["sdp"], type="answer")
    )

    await _wait_until(lambda: react_client.connectionState == "connected")
    await _wait_until(lambda: heartbeat.started_for is not None)

    return connection, react_client, channel, received_frames, heartbeat


def _envelope(
    conversation_id: str, realtime_session_id: str, session_epoch: int, event_id: str
) -> dict:
    return {
        "type": "child.turn.finalized",
        "eventId": event_id,
        "conversationId": conversation_id,
        "realtimeSessionId": realtime_session_id,
        "sessionEpoch": session_epoch,
        "eventSeq": 1,
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "payload": {},
    }


@pytest.mark.asyncio
async def test_two_concurrent_sessions_stay_isolated(caplog) -> None:
    registry = RealtimeConnectionRegistry()
    epoch_registry = SessionEpochRegistry()
    factory = AiortcPeerConnectionFactory()

    conn_a, client_a, channel_a, frames_a, heartbeat_a = await _connect(
        "conv_A", "rt_A", 1, registry, epoch_registry, factory
    )
    conn_b, client_b, channel_b, frames_b, heartbeat_b = await _connect(
        "conv_B", "rt_B", 1, registry, epoch_registry, factory
    )

    # Registry entries are independent per conversationId — not overwritten or shared.
    entry_a = registry.find("conv_A")
    entry_b = registry.find("conv_B")
    assert entry_a is not None and entry_b is not None
    assert entry_a is not entry_b
    assert entry_a.conversation_id == "conv_A"
    assert entry_b.conversation_id == "conv_B"
    assert heartbeat_a.started_for == ("conv_A", "rt_A", 1)
    assert heartbeat_b.started_for == ("conv_B", "rt_B", 1)

    # Each session's echo audio flows independently on its own track.
    await _wait_until(lambda: len(frames_a) >= 3)
    await _wait_until(lambda: len(frames_b) >= 3)

    # A reconnecting at a higher sessionEpoch must not affect B's epoch bookkeeping.
    epoch_registry.register("conv_A", 5)
    assert epoch_registry.is_current("conv_A", 1) is False
    assert epoch_registry.is_current("conv_B", 1) is True

    # DataChannel events are guarded per-connection: A's now-stale-epoch event is
    # discarded, while B's event at the same epoch VALUE is still accepted.
    with caplog.at_level(logging.INFO, logger=_LOGGER.name):
        caplog.clear()
        channel_a.send(json.dumps(_envelope("conv_A", "rt_A", 1, "evt_a_1")))
        await asyncio.sleep(0.2)
        assert any("discarded" in record.message for record in caplog.records)

        caplog.clear()
        channel_b.send(json.dumps(_envelope("conv_B", "rt_B", 1, "evt_b_1")))
        await asyncio.sleep(0.2)
        assert not any("discarded" in record.message for record in caplog.records)

    # Closing A's connection must not disturb B's registry entry or in-flight audio.
    await conn_a._cleanup()
    await client_a.close()
    assert registry.find("conv_A") is None
    assert registry.find("conv_B") is entry_b
    assert heartbeat_a.stopped is True
    assert heartbeat_b.stopped is False

    frames_b_before_close = len(frames_b)
    await asyncio.sleep(0.3)
    assert len(frames_b) > frames_b_before_close  # B's audio keeps flowing after A closed

    await conn_b._cleanup()
    await client_b.close()
    assert registry.find("conv_B") is None
    assert heartbeat_b.stopped is True
