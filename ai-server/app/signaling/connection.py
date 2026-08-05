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
from pydantic import ValidationError

from app.events.envelope import (
    ClientInterruptPayload,
    ConversationEventEnvelope,
    ConversationEventGuard,
)
from app.events.outbound import ConversationEventPublisher
from app.session.realtime_dialogue import RealtimeDialogueSession
from app.speech.speaker import SpeakerHandle
from app.realtime.session_epoch_registry import SessionEpochRegistry
from app.realtime.ticket_verification import TicketVerificationFailureCode, TicketVerificationResult
from app.session.realtime_connection_registry import (
    Heartbeat,
    RealtimeConnectionEntry,
    RealtimeConnectionRegistry,
)
from app.signaling.error_mapping import SIGNALING_MESSAGE_INVALID, WEBRTC_CONNECTION_FAILED
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
from app.webrtc.data_channel import DataChannel
from app.webrtc.peer_connection import PeerConnection, PeerConnectionFactory, PeerConnectionState

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
        epoch_registry: SessionEpochRegistry,
        logger: logging.Logger,
        realtime_session_factory: (
            Callable[
                [ConversationEventPublisher, str, SpeakerHandle | None],
                Awaitable[RealtimeDialogueSession],
            ]
            | None
        ) = None,
    ) -> None:
        self._websocket = websocket
        self._ticket_verifier = ticket_verifier
        self._peer_connection_factory = peer_connection_factory
        self._registry = registry
        self._heartbeat_factory = heartbeat_factory
        self._map_failure_code = map_failure_code
        self._epoch_registry = epoch_registry
        self._logger = logger
        self._realtime_session_factory = realtime_session_factory

        self._state = ConnectionState.AWAITING_AUTH
        self._peer_connection: PeerConnection | None = None
        self._heartbeat: Heartbeat | None = None
        self._data_channel: DataChannel | None = None
        self._event_guard: ConversationEventGuard | None = None
        self._event_publisher: ConversationEventPublisher | None = None
        self._dialogue_session: RealtimeDialogueSession | None = None
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
                raw.get("requestId"), SIGNALING_MESSAGE_INVALID, "알 수 없는 메시지입니다."
            )
            return
        except MalformedMessageError:
            await self._send_error(
                raw.get("requestId"), SIGNALING_MESSAGE_INVALID, "메시지 형식이 올바르지 않습니다."
            )
            return

        if isinstance(message, SessionAuthenticate):
            await self._handle_authenticate(message)
            return

        if self._state == ConnectionState.AWAITING_AUTH:
            await self._send_error(
                getattr(message, "request_id", None),
                SIGNALING_MESSAGE_INVALID,
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
                message.request_id, SIGNALING_MESSAGE_INVALID, "이미 인증되었습니다."
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
                message.request_id, SIGNALING_MESSAGE_INVALID, "지금은 Offer를 받을 수 없습니다."
            )
            return

        peer_connection = self._peer_connection_factory.create()
        peer_connection.on_state_change(self._on_peer_connection_state_change)
        peer_connection.on_data_channel_received(self._on_data_channel_received)
        self._peer_connection = peer_connection
        answer_sdp = await peer_connection.accept_offer(message.sdp.sdp)
        self._state = ConnectionState.ANSWERED
        await self._send(
            RtcAnswer(request_id=message.request_id, sdp=SessionDescription(type="answer", sdp=answer_sdp))
        )

    async def _handle_ice_candidate(self, message: RtcIceCandidate) -> None:
        if self._peer_connection is None:
            await self._send_error(
                message.request_id, SIGNALING_MESSAGE_INVALID, "PeerConnection이 아직 없습니다."
            )
            return
        await self._peer_connection.add_ice_candidate(
            message.candidate.candidate,
            message.candidate.sdp_mid,
            message.candidate.sdp_mline_index,
        )

    async def _on_data_channel_received(self, channel: DataChannel) -> None:
        self._data_channel = channel
        assert self._conversation_id and self._realtime_session_id
        assert self._session_epoch is not None
        self._event_publisher = ConversationEventPublisher(
            channel,
            conversation_id=self._conversation_id,
            realtime_session_id=self._realtime_session_id,
            session_epoch=self._session_epoch,
            logger=self._logger,
        )
        # 대화 세션(STT+Dialogue)은 지금에야 만들 수 있다: stt.final과 assistant.*
        # 이벤트가 이 채널로 나가야 하는데, aiortc는 audio track을 DataChannel보다
        # 먼저 올린다(SessionAudioBridge.attach_sink 참고).
        if self._realtime_session_factory is not None and self._peer_connection is not None:
            try:
                self._dialogue_session = await self._realtime_session_factory(
                    self._event_publisher,
                    self._conversation_id,
                    self._peer_connection.speaker,
                )
                self._peer_connection.attach_audio_sink(self._dialogue_session.audio_sink)
            except Exception:  # noqa: BLE001 - 조립 실패가 연결 전체를 죽여선 안 된다
                # aiortc의 datachannel 콜백에서 예외가 빠져나가면 조용히 사라진다.
                # 대화 없이(에코만) 유지하고 명시적으로 기록한다.
                self._dialogue_session = None
                self._logger.exception(
                    "realtime dialogue session could not be assembled",
                    extra={"conversation_id": self._conversation_id},
                )
        # Guard는 대화 세션이 생긴 뒤에 만든다 — 취소된 responseId·알려진 turnId
        # 판정 훅이 그 세션의 Registry/Ledger를 읽는다(T13).
        self._event_guard = ConversationEventGuard(
            self._conversation_id,
            self._realtime_session_id,
            self._epoch_registry,
            is_response_cancelled=(
                self._dialogue_session.is_response_cancelled
                if self._dialogue_session is not None
                else None
            ),
            is_known_turn=(
                self._dialogue_session.is_known_turn
                if self._dialogue_session is not None
                else None
            ),
        )
        channel.on_message(self._on_data_channel_message)

    async def _on_data_channel_message(self, raw: dict) -> None:
        try:
            envelope = ConversationEventEnvelope.model_validate(raw)
        except ValidationError:
            self._logger.warning("malformed conversation-events payload dropped")
            return

        assert self._event_guard is not None
        discard_reason = self._event_guard.check(envelope)
        if discard_reason is not None:
            self._logger.info(
                "conversation-events payload discarded",
                extra={"discard_reason": discard_reason.value, "event_type": envelope.type},
            )
            return

        if envelope.type == "client.interrupt":
            try:
                payload = ClientInterruptPayload.model_validate(envelope.payload)
            except ValidationError:
                self._logger.warning("malformed client.interrupt payload dropped")
                return
            if self._dialogue_session is not None:
                self._dialogue_session.interrupt_current_response(
                    payload.reason,
                    expected_response_id=payload.response_id,
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
            registered = await self._registry.register(
                RealtimeConnectionEntry(
                    conversation_id=self._conversation_id,
                    realtime_session_id=self._realtime_session_id,
                    session_epoch=self._session_epoch,
                    peer_connection=self._peer_connection,
                    heartbeat=self._heartbeat,
                    data_channel=self._data_channel,
                )
            )
            if not registered:
                # 더 최신 sessionEpoch의 연결이 이미 등록되어 있다 — 이 연결은 지연된
                # 재연결 시도이므로 자신을 정리하고 물러난다(§4 T12, 리뷰에서 발견).
                self._logger.warning(
                    "stale reconnect reached connected after a newer session_epoch took over",
                    extra={
                        "conversation_id": self._conversation_id,
                        "realtime_session_id": self._realtime_session_id,
                        "session_epoch": self._session_epoch,
                    },
                )
                await self._cleanup()
                await self._websocket.close(code=1008)
                return
            await self._send(
                RtcConnected(
                    conversation_id=self._conversation_id,
                    realtime_session_id=self._realtime_session_id,
                    session_epoch=self._session_epoch,
                )
            )
        elif state == PeerConnectionState.FAILED:
            self._state = ConnectionState.FAILED
            await self._send_error(None, WEBRTC_CONNECTION_FAILED, "PeerConnection 연결에 실패했습니다.")
            # FAILED는 더 이상 쓸 수 없는 연결이다 — Heartbeat·PeerConnection·Registry
            # 항목을 즉시 정리하고 WebSocket을 닫는다(리뷰에서 발견: 이전에는 클라이언트가
            # 스스로 연결을 끊을 때까지 Lease Heartbeat가 무한히 갱신되는 누수가 있었다).
            await self._cleanup()
            await self._websocket.close(code=1011)

    async def _cleanup(self) -> None:
        if self._state == ConnectionState.CLOSED:
            return
        self._state = ConnectionState.CLOSED
        if self._heartbeat is not None:
            await self._heartbeat.stop()
        self._event_publisher = None
        if self._dialogue_session is not None:
            await self._dialogue_session.aclose()
            self._dialogue_session = None
        if self._data_channel is not None:
            self._data_channel.close()
        if self._peer_connection is not None:
            await self._peer_connection.close()  # also stops this connection's SessionAudioBridge (T13)
        if self._conversation_id is not None and self._realtime_session_id is not None:
            await self._registry.remove(self._conversation_id, self._realtime_session_id)

    async def _send(self, message: Any) -> None:
        await self._websocket.send_json(message.model_dump(mode="json", by_alias=True))

    async def _send_error(self, request_id: str | None, code: str, message: str) -> None:
        await self._send(RtcError(request_id=request_id, code=code, message=message))
