"""Pydantic models for the WebSocket Signaling messages defined in
REALTIME_EVENT_CONTRACT.md §5 (``ws://localhost:8000/realtime/signaling``).

Incoming messages (React -> FastAPI): ``session.authenticate``, ``rtc.offer``,
``rtc.ice-candidate``, ``rtc.ice-complete``. Outgoing messages (FastAPI ->
React): ``session.authenticated``, ``rtc.answer``, ``rtc.ice-candidate``,
``rtc.connected``, ``rtc.error``. ``rtc.ice-candidate`` is exchanged in both
directions (see jira/plan/DIARY-2-W03.md §4 T09).
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError


class SessionDescription(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["offer", "answer"]
    sdp: str


class IceCandidate(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    candidate: str
    sdp_mid: str | None = Field(default=None, alias="sdpMid")
    sdp_mline_index: int | None = Field(default=None, alias="sdpMLineIndex")


class SessionAuthenticate(BaseModel):
    """React -> FastAPI: the first and only message allowed before auth."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["session.authenticate"] = "session.authenticate"
    request_id: str = Field(alias="requestId")
    ticket: str


class SessionAuthenticated(BaseModel):
    """FastAPI -> React: sent once ``session.authenticate`` succeeds."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["session.authenticated"] = "session.authenticated"
    request_id: str = Field(alias="requestId")
    conversation_id: str = Field(alias="conversationId")
    realtime_session_id: str = Field(alias="realtimeSessionId")
    session_epoch: int = Field(alias="sessionEpoch")


class RtcOffer(BaseModel):
    """React -> FastAPI only (T11: React가 Offer를 생성한다)."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.offer"] = "rtc.offer"
    request_id: str = Field(alias="requestId")
    sdp: SessionDescription


class RtcAnswer(BaseModel):
    """FastAPI -> React only (T11: FastAPI가 Answer를 생성한다)."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.answer"] = "rtc.answer"
    request_id: str = Field(alias="requestId")
    sdp: SessionDescription


class RtcIceCandidate(BaseModel):
    """Bidirectional: each side forwards its own local ICE candidates."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.ice-candidate"] = "rtc.ice-candidate"
    request_id: str = Field(alias="requestId")
    candidate: IceCandidate


class RtcIceComplete(BaseModel):
    """React -> FastAPI: informational only, no response is sent back."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.ice-complete"] = "rtc.ice-complete"
    request_id: str = Field(alias="requestId")


class RtcConnected(BaseModel):
    """FastAPI -> React only, sent when the PeerConnection reaches ``connected``."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.connected"] = "rtc.connected"
    conversation_id: str = Field(alias="conversationId")
    realtime_session_id: str = Field(alias="realtimeSessionId")
    session_epoch: int = Field(alias="sessionEpoch")


class RtcError(BaseModel):
    """FastAPI -> React only."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    type: Literal["rtc.error"] = "rtc.error"
    request_id: str | None = Field(default=None, alias="requestId")
    code: str
    message: str


IncomingMessage = SessionAuthenticate | RtcOffer | RtcIceCandidate | RtcIceComplete

_INCOMING_MESSAGE_TYPES: dict[str, type[IncomingMessage]] = {
    "session.authenticate": SessionAuthenticate,
    "rtc.offer": RtcOffer,
    "rtc.ice-candidate": RtcIceCandidate,
    "rtc.ice-complete": RtcIceComplete,
}


class UnknownMessageTypeError(Exception):
    """Raised when a raw message's ``type`` is missing or not recognized."""


class MalformedMessageError(Exception):
    """Raised when a raw message's ``type`` is recognized but the payload fails validation."""


def parse_incoming_message(raw: dict[str, Any]) -> IncomingMessage:
    """Parse a raw decoded-JSON dict into one of the known incoming message models.

    Raises ``UnknownMessageTypeError`` for a missing/unrecognized ``type`` and
    ``MalformedMessageError`` when the ``type`` is known but required fields are
    missing or invalid.
    """

    message_type = raw.get("type")
    model = _INCOMING_MESSAGE_TYPES.get(message_type) if isinstance(message_type, str) else None
    if model is None:
        raise UnknownMessageTypeError(str(message_type))
    try:
        return model.model_validate(raw)
    except ValidationError as error:
        raise MalformedMessageError(message_type) from error
