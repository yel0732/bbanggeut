"""Tests for the Signaling WebSocket connection state machine (T09/T10/T11/T12).

Uses Fake collaborators throughout so the state machine is verified without a
real WebSocket, aiortc PeerConnection, Redis-backed Ticket Verifier, or
Heartbeat timer loop (see jira/plan/DIARY-2-W03.md §3 principle 3).
"""

import logging

import pytest
from fastapi import WebSocketDisconnect

from app.realtime.session_epoch_registry import SessionEpochRegistry
from app.realtime.ticket_verification import (
    TicketVerificationFailureCode,
    TicketVerificationResult,
)
from app.session.realtime_connection_registry import RealtimeConnectionRegistry
from app.signaling.connection import ConnectionState, SignalingConnection
from app.signaling.error_mapping import map_ticket_failure_code
from app.webrtc.data_channel import FakeDataChannel
from app.webrtc.peer_connection import FakePeerConnectionFactory, PeerConnectionState

_LOGGER = logging.getLogger("test.signaling.connection")


class FakeWebSocket:
    def __init__(self, incoming: list[dict] | None = None) -> None:
        self._incoming = list(incoming or [])
        self.sent: list[dict] = []
        self.closed = False
        self.close_code: int | None = None

    def queue(self, message: dict) -> None:
        self._incoming.append(message)

    async def receive_json(self) -> dict:
        if not self._incoming:
            raise WebSocketDisconnect()
        return self._incoming.pop(0)

    async def send_json(self, data: dict) -> None:
        self.sent.append(data)

    async def close(self, code: int = 1000) -> None:
        self.closed = True
        self.close_code = code


class FakeTicketVerifier:
    def __init__(self, result: TicketVerificationResult) -> None:
        self._result = result
        self.calls: list[str] = []

    async def verify_and_consume(self, ticket: str) -> TicketVerificationResult:
        self.calls.append(ticket)
        return self._result


class FakeHeartbeat:
    def __init__(self) -> None:
        self.started_with: tuple[str, str, int] | None = None
        self.start_call_count = 0
        self.stop_call_count = 0

    async def start(self, conversation_id: str, realtime_session_id: str, session_epoch: int) -> None:
        self.started_with = (conversation_id, realtime_session_id, session_epoch)
        self.start_call_count += 1

    async def stop(self) -> None:
        self.stop_call_count += 1


def _build_connection(
    *,
    websocket: FakeWebSocket,
    ticket_result: TicketVerificationResult,
    registry: RealtimeConnectionRegistry | None = None,
    peer_connection_factory: FakePeerConnectionFactory | None = None,
) -> tuple[SignalingConnection, FakeTicketVerifier, FakePeerConnectionFactory, RealtimeConnectionRegistry, FakeHeartbeat]:
    ticket_verifier = FakeTicketVerifier(ticket_result)
    factory = peer_connection_factory or FakePeerConnectionFactory()
    registry = registry or RealtimeConnectionRegistry()
    heartbeat = FakeHeartbeat()
    connection = SignalingConnection(
        websocket=websocket,
        ticket_verifier=ticket_verifier,
        peer_connection_factory=factory,
        registry=registry,
        heartbeat_factory=lambda: heartbeat,
        map_failure_code=map_ticket_failure_code,
        epoch_registry=SessionEpochRegistry(),
        logger=_LOGGER,
    )
    return connection, ticket_verifier, factory, registry, heartbeat


def _authenticate_message() -> dict:
    return {"type": "session.authenticate", "requestId": "req_auth", "ticket": "signed-ticket"}


def _offer_message() -> dict:
    return {
        "type": "rtc.offer",
        "requestId": "req_offer",
        "sdp": {"type": "offer", "sdp": "v=0...offer"},
    }


def _ice_candidate_message() -> dict:
    return {
        "type": "rtc.ice-candidate",
        "requestId": "req_ice",
        "candidate": {"candidate": "candidate:1", "sdpMid": "0", "sdpMLineIndex": 0},
    }


@pytest.mark.asyncio
async def test_rejects_rtc_message_before_authentication() -> None:
    websocket = FakeWebSocket([_offer_message()])
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection.run()

    assert websocket.sent[0]["type"] == "rtc.error"
    assert websocket.sent[0]["code"] == "SIGNALING_MESSAGE_INVALID"
    assert not websocket.closed
    assert factory.created == []


@pytest.mark.asyncio
async def test_authenticate_success_then_offer_flow() -> None:
    websocket = FakeWebSocket()
    connection, ticket_verifier, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    # Drive messages via _handle_raw directly (not run()) so the connection's
    # intermediate state can be inspected without triggering run()'s
    # disconnect-cleanup once the fake queue runs dry.
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())

    assert ticket_verifier.calls == ["signed-ticket"]
    assert websocket.sent[0] == {
        "type": "session.authenticated",
        "requestId": "req_auth",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
    }
    assert websocket.sent[1]["type"] == "rtc.answer"
    assert websocket.sent[1]["sdp"]["type"] == "answer"
    assert connection._state == ConnectionState.ANSWERED
    assert factory.created[0].accepted_offer_sdp == "v=0...offer"


@pytest.mark.asyncio
async def test_authentication_failure_sends_error_and_closes() -> None:
    websocket = FakeWebSocket([_authenticate_message()])
    connection, ticket_verifier, _, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.failure(TicketVerificationFailureCode.EXPIRED),
    )

    await connection.run()

    assert websocket.sent == [
        {
            "type": "rtc.error",
            "requestId": "req_auth",
            "code": "REALTIME_TICKET_EXPIRED",
            "message": "인증에 실패했습니다.",
        }
    ]
    assert websocket.closed
    assert websocket.close_code == 1008
    assert ticket_verifier.calls == ["signed-ticket"]


@pytest.mark.asyncio
async def test_duplicate_authenticate_is_rejected_without_reverifying() -> None:
    websocket = FakeWebSocket()
    connection, ticket_verifier, _, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_authenticate_message())

    assert ticket_verifier.calls == ["signed-ticket"]
    assert websocket.sent[1]["type"] == "rtc.error"
    assert websocket.sent[1]["code"] == "SIGNALING_MESSAGE_INVALID"
    assert connection._state == ConnectionState.AUTHENTICATED


@pytest.mark.asyncio
async def test_unknown_message_type_is_rejected_and_connection_stays_open() -> None:
    websocket = FakeWebSocket([{"type": "rtc.banana", "requestId": "req_x"}])
    connection, _, _, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection.run()

    assert websocket.sent[0]["type"] == "rtc.error"
    assert websocket.sent[0]["code"] == "SIGNALING_MESSAGE_INVALID"
    assert not websocket.closed


@pytest.mark.asyncio
async def test_ice_candidate_before_offer_is_rejected() -> None:
    websocket = FakeWebSocket([_authenticate_message(), _ice_candidate_message()])
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection.run()

    assert websocket.sent[1]["type"] == "rtc.error"
    assert factory.created == []


@pytest.mark.asyncio
async def test_ice_candidate_after_offer_is_forwarded_to_peer_connection() -> None:
    websocket = FakeWebSocket(
        [_authenticate_message(), _offer_message(), _ice_candidate_message()]
    )
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection.run()

    assert factory.created[0].added_ice_candidates == [("candidate:1", "0", 0)]


@pytest.mark.asyncio
async def test_peer_connection_connected_starts_heartbeat_and_registers() -> None:
    websocket = FakeWebSocket()
    registry = RealtimeConnectionRegistry()
    connection, _, factory, registry, heartbeat = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
        registry=registry,
    )

    # Drive setup via _handle_raw (not run()) so the connection stays open —
    # run() would immediately hit WebSocketDisconnect and run cleanup once its
    # queue drains, which in real use only happens after the state-change
    # callback below (aiortc reports "connected" while the socket is open).
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    await peer_connection.simulate_state_change(PeerConnectionState.CONNECTED)

    assert heartbeat.started_with == ("conv_1", "rt_1", 1)
    assert heartbeat.start_call_count == 1
    entry = registry.find("conv_1")
    assert entry is not None
    assert entry.realtime_session_id == "rt_1"
    assert websocket.sent[-1] == {
        "type": "rtc.connected",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
    }


@pytest.mark.asyncio
async def test_peer_connection_failed_sends_error_and_cleans_up() -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )

    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    await peer_connection.simulate_state_change(PeerConnectionState.FAILED)

    assert websocket.sent[-1]["type"] == "rtc.error"
    assert websocket.sent[-1]["code"] == "WEBRTC_CONNECTION_FAILED"
    # 리뷰에서 발견: FAILED는 즉시 정리되어야 한다 — 그렇지 않으면 클라이언트가 스스로
    # 연결을 끊을 때까지 Lease Heartbeat가 무한히 갱신되는 누수가 생긴다.
    assert connection._state == ConnectionState.CLOSED
    assert peer_connection.closed
    assert websocket.closed
    assert websocket.close_code == 1011


@pytest.mark.asyncio
async def test_peer_connection_failed_after_connected_stops_heartbeat() -> None:
    websocket = FakeWebSocket()
    registry = RealtimeConnectionRegistry()
    connection, _, factory, registry, heartbeat = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
        registry=registry,
    )

    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    await peer_connection.simulate_state_change(PeerConnectionState.CONNECTED)
    assert registry.find("conv_1") is not None

    await peer_connection.simulate_state_change(PeerConnectionState.FAILED)

    assert heartbeat.stop_call_count == 1
    assert registry.find("conv_1") is None
    assert websocket.closed


@pytest.mark.asyncio
async def test_disconnect_cleans_up_heartbeat_peer_connection_and_registry() -> None:
    websocket = FakeWebSocket()
    registry = RealtimeConnectionRegistry()
    connection, _, factory, registry, heartbeat = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
        registry=registry,
    )

    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    await peer_connection.simulate_state_change(PeerConnectionState.CONNECTED)
    assert registry.find("conv_1") is not None

    # Simulate the WebSocket disconnecting: run() observes the empty queue
    # immediately and runs cleanup.
    await connection.run()

    assert heartbeat.stop_call_count == 1
    assert peer_connection.closed
    assert registry.find("conv_1") is None


@pytest.mark.asyncio
async def test_cleanup_is_idempotent() -> None:
    websocket = FakeWebSocket()
    registry = RealtimeConnectionRegistry()
    connection, _, factory, registry, heartbeat = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
        registry=registry,
    )

    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    await peer_connection.simulate_state_change(PeerConnectionState.CONNECTED)

    await connection._cleanup()
    await connection._cleanup()

    assert heartbeat.stop_call_count == 1


@pytest.mark.asyncio
async def test_stale_reconnect_reaching_connected_after_newer_epoch_backs_off() -> None:
    """Reproduces the race found in review: an old (lower sessionEpoch) connection's
    slow SDP/ICE negotiation finally reaches ``connected`` after a newer reconnect
    (higher sessionEpoch) has already registered — the stale one must clean itself
    up and close, not evict the current, legitimate connection."""

    registry = RealtimeConnectionRegistry()

    new_websocket = FakeWebSocket()
    new_connection, _, new_factory, _, new_heartbeat = _build_connection(
        websocket=new_websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_new", 2),
        registry=registry,
    )
    await new_connection._handle_raw(_authenticate_message())
    await new_connection._handle_raw(_offer_message())
    await new_factory.created[0].simulate_state_change(PeerConnectionState.CONNECTED)
    assert registry.find("conv_1").realtime_session_id == "rt_new"

    old_websocket = FakeWebSocket()
    old_connection, _, old_factory, _, old_heartbeat = _build_connection(
        websocket=old_websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_old", 1),
        registry=registry,
    )
    await old_connection._handle_raw(_authenticate_message())
    await old_connection._handle_raw(_offer_message())
    await old_factory.created[0].simulate_state_change(PeerConnectionState.CONNECTED)

    # The stale (lower-epoch) connection must not have evicted the newer one.
    assert registry.find("conv_1").realtime_session_id == "rt_new"
    assert new_heartbeat.stop_call_count == 0
    assert not new_factory.created[0].closed
    assert not new_websocket.closed

    # The stale connection must have cleaned itself up instead.
    assert old_heartbeat.stop_call_count == 1
    assert old_factory.created[0].closed
    assert old_websocket.closed
    assert old_websocket.close_code == 1008


def _data_channel_envelope(**overrides) -> dict:
    base = {
        "type": "client.ready",
        "eventId": "evt_1",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
        "eventSeq": 1,
        "occurredAt": "2026-07-22T10:00:10+09:00",
        "payload": {"microphoneReady": True},
    }
    base.update(overrides)
    return base


@pytest.mark.asyncio
async def test_data_channel_received_wires_message_handler() -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    peer_connection = factory.created[0]
    channel = FakeDataChannel()

    await peer_connection.simulate_data_channel_received(channel)

    assert connection._data_channel is channel
    assert connection._event_guard is not None


@pytest.mark.asyncio
async def test_valid_data_channel_event_is_not_discarded(caplog) -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    channel = FakeDataChannel()
    await factory.created[0].simulate_data_channel_received(channel)

    with caplog.at_level(logging.INFO, logger=_LOGGER.name):
        await channel.simulate_message(_data_channel_envelope())

    assert not any("discarded" in record.message for record in caplog.records)
    assert not any("malformed" in record.message for record in caplog.records)


@pytest.mark.asyncio
async def test_data_channel_event_with_wrong_conversation_id_is_discarded(caplog) -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    channel = FakeDataChannel()
    await factory.created[0].simulate_data_channel_received(channel)

    with caplog.at_level(logging.INFO, logger=_LOGGER.name):
        await channel.simulate_message(_data_channel_envelope(conversationId="conv_other"))

    assert any("discarded" in record.message for record in caplog.records)


@pytest.mark.asyncio
async def test_malformed_data_channel_event_is_dropped(caplog) -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    channel = FakeDataChannel()
    await factory.created[0].simulate_data_channel_received(channel)

    with caplog.at_level(logging.WARNING, logger=_LOGGER.name):
        await channel.simulate_message({"type": "client.ready"})  # missing required fields

    assert any("malformed" in record.message for record in caplog.records)


@pytest.mark.asyncio
async def test_duplicate_event_id_is_discarded_on_second_delivery(caplog) -> None:
    websocket = FakeWebSocket()
    connection, _, factory, _, _ = _build_connection(
        websocket=websocket,
        ticket_result=TicketVerificationResult.success("conv_1", "rt_1", 1),
    )
    await connection._handle_raw(_authenticate_message())
    await connection._handle_raw(_offer_message())
    channel = FakeDataChannel()
    await factory.created[0].simulate_data_channel_received(channel)
    envelope = _data_channel_envelope()

    await channel.simulate_message(envelope)
    with caplog.at_level(logging.INFO, logger=_LOGGER.name):
        await channel.simulate_message(envelope)

    assert any("discarded" in record.message for record in caplog.records)
