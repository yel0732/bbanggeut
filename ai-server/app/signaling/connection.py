"""One Signaling WebSocket connection's state machine.

Kept intentionally thin on policy per docs/AI_CONVENTIONS.md §4 ("WebSocket
Handler에 대화 정책이나 Provider 로직을 두지 않는다") — this class only
sequences messages through the states below and delegates the actual work
(Ticket verification, PeerConnection, Registry bookkeeping, Heartbeat) to its
injected collaborators.

State machine (jira/plan/DIARY-2-W03.md §4 T09):

    AWAITING_AUTH
      --session.authenticate 성공--> AUTHENTICATED
      --실패--> rtc.error 전송 후 WebSocket 종료(§11-D)
      --그 외 메시지--> rtc.error 전송, 연결 유지

    AUTHENTICATED
      --rtc.offer--> ANSWERED (Answer 생성·전송)
      --그 외 rtc.*--> rtc.error 전송, 연결 유지

    ANSWERED / CONNECTED
      --rtc.ice-candidate--> PeerConnection에 추가
      --rtc.ice-complete--> 정보성, 응답 없음
      --PeerConnection state=connected--> CONNECTED (rtc.connected 전송, Heartbeat.start(),
          Registry.register())
      --PeerConnection state=failed--> FAILED (rtc.error 전송)

    모든 상태 --WebSocket 종료(정상·비정상)--> Cleanup(Heartbeat.stop, PeerConnection.close,
        Registry.remove) — 여러 번 호출해도 안전(멱등)
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from enum import StrEnum
from typing import Any, Protocol

from fastapi import WebSocketDisconnect

from app.realtime.ticket_verification import TicketVerificationFailureCode, TicketVerificationResult
from app.session.realtime_connection_registry import (
    Heartbeat,
    RealtimeConnectionEntry,
    RealtimeConnectionRegistry,
)
from app.signaling.messages import (
    MalformedMessageError,
    RtcAnswer,
    RtcConnected,
    RtcError,
    RtcIceCandidate,
    RtcIceComplete,
    RtcOffer,
    SessionAuthenticate,
    SessionAuthenticated,
    SessionDescription,
    UnknownMessageTypeError,
    parse_incoming_message,
)
from app.webrtc.peer_connection import PeerConnection, PeerConnectionFactory, PeerConnectionState

_SIGNALING_MESSAGE_INVALID = "SIGNALING_MESSAGE_INVALID"
_WEBRTC_CONNECTION_FAILED = "WEBRTC_CONNECTION_FAILED"
_AUTH_FAILURE_CLOSE_CODE = 1008  # WebSocket policy violation


class ConnectionState(StrEnum):
    AWAITING_AUTH = "awaitingAuth"
    AUTHENTICATED = "authenticated"
    ANSWERED = "answered"
    CONNECTED = "connected"
    FAILED = "failed"
    CLOSED = "closed"


class TicketVerifier(Protocol):
    async def verify_and_consume(self, ticket: str) -> TicketVerificationResult: ...


class WebSocketLike(Protocol):
    async def receive_json(self) -> dict[str, Any]: ...
    async def send_json(self, data: dict[str, Any]) -> None: ...
    async def close(self, code: int = 1000) -> None: ...


class SignalingConnection:
    def __init__(
        self,
        websocket: WebSocketLike,
        ticket_verifier: TicketVerifier,
        peer_connection_factory: PeerConnectionFactory,
        registry: RealtimeConnectionRegistry,
        heartbeat_factory: Callable[[], Heartbeat],
        map_failure_code: Callable[[TicketVerificationFailureCode], str],
        logger: logging.Logger,
    ) -> None:
        self._websocket = websocket
        self._ticket_verifier = ticket_verifier
        self._peer_connection_factory = peer_connection_factory
        self._registry = registry
        self._heartbeat_factory = heartbeat_factory
        self._map_failure_code = map_failure_code
        self._logger = logger

        self._state = ConnectionState.AWAITING_AUTH
        self._peer_connection: PeerConnection | None = None
        self._heartbeat: Heartbeat | None = None
        self._conversation_id: str | None = None
        self._realtime_session_id: str | None = None
        self._session_epoch: int | None = None

    async def run(self) -> None:
        try:
            while self._state != ConnectionState.CLOSED:
                raw = await self._websocket.receive_json()
                await self._handle_raw(raw)
        except WebSocketDisconnect:
            pass
        finally:
            await self._cleanup()

    async def _handle_raw(self, raw: dict[str, Any]) -> None:
        try:
            message = parse_incoming_message(raw)
        except UnknownMessageTypeError:
            await self._send_error(
                raw.get("requestId"), _SIGNALING_MESSAGE_INVALID, "알 수 없는 메시지입니다."
            )
            return
        except MalformedMessageError:
            await self._send_error(
                raw.get("requestId"), _SIGNALING_MESSAGE_INVALID, "메시지 형식이 올바르지 않습니다."
            )
            return

        if isinstance(message, SessionAuthenticate):
            await self._handle_authenticate(message)
            return

        if self._state == ConnectionState.AWAITING_AUTH:
            await self._send_error(
                getattr(message, "request_id", None),
                _SIGNALING_MESSAGE_INVALID,
                "인증 전에는 처리할 수 없습니다.",
            )
            return

        if isinstance(message, RtcOffer):
            await self._handle_offer(message)
        elif isinstance(message, RtcIceCandidate):
            await self._handle_ice_candidate(message)
        elif isinstance(message, RtcIceComplete):
            pass  # 정보성 메시지 — 응답 없음(§4 T09)

    async def _handle_authenticate(self, message: SessionAuthenticate) -> None:
        if self._state != ConnectionState.AWAITING_AUTH:
            await self._send_error(
                message.request_id, _SIGNALING_MESSAGE_INVALID, "이미 인증되었습니다."
            )
            return

        result = await self._ticket_verifier.verify_and_consume(message.ticket)
        if not result.is_valid:
            code = self._map_failure_code(result.failure_code)
            self._logger.warning(
                "signaling authentication rejected",
                extra={"failure_code": result.failure_code.value},
            )
            await self._send_error(message.request_id, code, "인증에 실패했습니다.")
            self._state = ConnectionState.CLOSED
            await self._websocket.close(code=_AUTH_FAILURE_CLOSE_CODE)
            return

        self._conversation_id = result.conversation_id
        self._realtime_session_id = result.realtime_session_id
        self._session_epoch = result.session_epoch
        self._state = ConnectionState.AUTHENTICATED
        await self._send(
            SessionAuthenticated(
                request_id=message.request_id,
                conversation_id=result.conversation_id,
                realtime_session_id=result.realtime_session_id,
                session_epoch=result.session_epoch,
            )
        )

    async def _handle_offer(self, message: RtcOffer) -> None:
        if self._state != ConnectionState.AUTHENTICATED:
            await self._send_error(
                message.request_id, _SIGNALING_MESSAGE_INVALID, "지금은 Offer를 받을 수 없습니다."
            )
            return

        peer_connection = self._peer_connection_factory.create()
        peer_connection.on_state_change(self._on_peer_connection_state_change)
        self._peer_connection = peer_connection
        answer_sdp = await peer_connection.accept_offer(message.sdp.sdp)
        self._state = ConnectionState.ANSWERED
        await self._send(
            RtcAnswer(request_id=message.request_id, sdp=SessionDescription(type="answer", sdp=answer_sdp))
        )

    async def _handle_ice_candidate(self, message: RtcIceCandidate) -> None:
        if self._peer_connection is None:
            await self._send_error(
                message.request_id, _SIGNALING_MESSAGE_INVALID, "PeerConnection이 아직 없습니다."
            )
            return
        await self._peer_connection.add_ice_candidate(
            message.candidate.candidate,
            message.candidate.sdp_mid,
            message.candidate.sdp_mline_index,
        )

    async def _on_peer_connection_state_change(self, state: PeerConnectionState) -> None:
        if self._state == ConnectionState.CLOSED:
            return  # 이미 정리된 연결에 도착한 지연 콜백은 폐기한다(§4 T11/T12 취소 규칙과 동일 원칙)
        if state == PeerConnectionState.CONNECTED:
            if self._state == ConnectionState.CONNECTED:
                return
            self._state = ConnectionState.CONNECTED
            assert self._conversation_id and self._realtime_session_id and self._session_epoch is not None
            self._heartbeat = self._heartbeat_factory()
            await self._heartbeat.start(
                self._conversation_id, self._realtime_session_id, self._session_epoch
            )
            assert self._peer_connection is not None
            await self._registry.register(
                RealtimeConnectionEntry(
                    conversation_id=self._conversation_id,
                    realtime_session_id=self._realtime_session_id,
                    session_epoch=self._session_epoch,
                    peer_connection=self._peer_connection,
                    heartbeat=self._heartbeat,
                )
            )
            await self._send(
                RtcConnected(
                    conversation_id=self._conversation_id,
                    realtime_session_id=self._realtime_session_id,
                    session_epoch=self._session_epoch,
                )
            )
        elif state == PeerConnectionState.FAILED:
            self._state = ConnectionState.FAILED
            await self._send_error(None, _WEBRTC_CONNECTION_FAILED, "PeerConnection 연결에 실패했습니다.")

    async def _cleanup(self) -> None:
        if self._state == ConnectionState.CLOSED:
            return
        self._state = ConnectionState.CLOSED
        if self._heartbeat is not None:
            await self._heartbeat.stop()
        if self._peer_connection is not None:
            await self._peer_connection.close()
        if self._conversation_id is not None and self._realtime_session_id is not None:
            await self._registry.remove(self._conversation_id, self._realtime_session_id)

    async def _send(self, message: Any) -> None:
        await self._websocket.send_json(message.model_dump(mode="json", by_alias=True))

    async def _send_error(self, request_id: str | None, code: str, message: str) -> None:
        await self._send(RtcError(request_id=request_id, code=code, message=message))
