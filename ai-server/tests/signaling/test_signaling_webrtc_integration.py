"""End-to-end test of SignalingConnection driving a real aiortc PeerConnection.

A genuine aiortc ``RTCPeerConnection`` plays the "React" role — it builds a
real SDP offer, feeds it through ``SignalingConnection._handle_raw`` exactly
as the WebSocket route would, and applies the real answer it gets back. Only
the WebSocket transport and Ticket Verifier are Fakes (no Redis/JWKS needed
for this test — see jira/plan/DIARY-2-W03.md §7); the WebRTC mechanics are
100% real aiortc, unlike tests/signaling/test_connection.py which uses
FakePeerConnection throughout.
"""

import asyncio
import logging

import pytest
from aiortc import RTCPeerConnection, RTCSessionDescription

from app.realtime.session_epoch_registry import SessionEpochRegistry
from app.realtime.ticket_verification import TicketVerificationResult
from app.session.realtime_connection_registry import RealtimeConnectionRegistry
from app.signaling.connection import SignalingConnection
from app.signaling.error_mapping import map_ticket_failure_code
from app.webrtc.peer_connection import AiortcPeerConnectionFactory

_LOGGER = logging.getLogger("test.signaling.webrtc_integration")


class _FakeWebSocket:
    def __init__(self) -> None:
        self.sent: list[dict] = []
        self.closed = False

    async def send_json(self, data: dict) -> None:
        self.sent.append(data)

    async def receive_json(self) -> dict:  # pragma: no cover - driven manually via _handle_raw
        raise NotImplementedError

    async def close(self, code: int = 1000) -> None:
        self.closed = True


class _FakeTicketVerifier:
    def __init__(self, result: TicketVerificationResult) -> None:
        self._result = result

    async def verify_and_consume(self, ticket: str) -> TicketVerificationResult:
        return self._result


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


@pytest.mark.asyncio
async def test_full_signaling_flow_reaches_connected_with_real_aiortc() -> None:
    react_client = RTCPeerConnection()
    react_client.createDataChannel("probe")

    websocket = _FakeWebSocket()
    registry = RealtimeConnectionRegistry()
    heartbeat_started = asyncio.Event()

    class _FakeHeartbeat:
        async def start(self, conversation_id: str, realtime_session_id: str, session_epoch: int) -> None:
            heartbeat_started.set()

        async def stop(self) -> None:
            pass

    connection = SignalingConnection(
        websocket=websocket,
        ticket_verifier=_FakeTicketVerifier(
            TicketVerificationResult.success("conv_1", "rt_1", 1)
        ),
        peer_connection_factory=AiortcPeerConnectionFactory(),
        registry=registry,
        heartbeat_factory=lambda: _FakeHeartbeat(),
        map_failure_code=map_ticket_failure_code,
        epoch_registry=SessionEpochRegistry(),
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
    await _wait_until(lambda: heartbeat_started.is_set())
    await _wait_until(lambda: websocket.sent[-1]["type"] == "rtc.connected")

    assert registry.find("conv_1") is not None
    assert websocket.sent[-1] == {
        "type": "rtc.connected",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
    }

    await connection._cleanup()
    await react_client.close()

    assert registry.find("conv_1") is None
