"""Tests for WebSocket Signaling message parsing (REALTIME_EVENT_CONTRACT.md §5)."""

import pytest

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


def test_parses_session_authenticate() -> None:
    message = parse_incoming_message(
        {"type": "session.authenticate", "requestId": "req_001", "ticket": "signed-ticket"}
    )

    assert isinstance(message, SessionAuthenticate)
    assert message.request_id == "req_001"
    assert message.ticket == "signed-ticket"


def test_parses_rtc_offer() -> None:
    message = parse_incoming_message(
        {
            "type": "rtc.offer",
            "requestId": "req_002",
            "sdp": {"type": "offer", "sdp": "v=0..."},
        }
    )

    assert isinstance(message, RtcOffer)
    assert message.sdp.type == "offer"
    assert message.sdp.sdp == "v=0..."


def test_parses_rtc_ice_candidate_with_optional_fields_present() -> None:
    message = parse_incoming_message(
        {
            "type": "rtc.ice-candidate",
            "requestId": "req_003",
            "candidate": {
                "candidate": "candidate:...",
                "sdpMid": "0",
                "sdpMLineIndex": 0,
            },
        }
    )

    assert isinstance(message, RtcIceCandidate)
    assert message.candidate.sdp_mid == "0"
    assert message.candidate.sdp_mline_index == 0


def test_parses_rtc_ice_candidate_with_optional_fields_absent() -> None:
    message = parse_incoming_message(
        {
            "type": "rtc.ice-candidate",
            "requestId": "req_003",
            "candidate": {"candidate": "candidate:..."},
        }
    )

    assert isinstance(message, RtcIceCandidate)
    assert message.candidate.sdp_mid is None
    assert message.candidate.sdp_mline_index is None


def test_parses_rtc_ice_complete() -> None:
    message = parse_incoming_message({"type": "rtc.ice-complete", "requestId": "req_004"})

    assert isinstance(message, RtcIceComplete)
    assert message.request_id == "req_004"


def test_missing_type_is_unknown() -> None:
    with pytest.raises(UnknownMessageTypeError):
        parse_incoming_message({"requestId": "req_001", "ticket": "x"})


def test_unrecognized_type_is_unknown() -> None:
    with pytest.raises(UnknownMessageTypeError):
        parse_incoming_message({"type": "rtc.banana", "requestId": "req_001"})


def test_known_type_missing_required_field_is_malformed() -> None:
    with pytest.raises(MalformedMessageError):
        parse_incoming_message({"type": "session.authenticate", "requestId": "req_001"})


def test_known_type_with_extra_field_is_malformed() -> None:
    with pytest.raises(MalformedMessageError):
        parse_incoming_message(
            {
                "type": "session.authenticate",
                "requestId": "req_001",
                "ticket": "x",
                "unexpected": "field",
            }
        )


def test_outgoing_messages_serialize_with_camel_case_aliases() -> None:
    authenticated = SessionAuthenticated(
        request_id="req_001",
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=1,
    )
    answer = RtcAnswer(request_id="req_002", sdp=SessionDescription(type="answer", sdp="v=0..."))
    connected = RtcConnected(conversation_id="conv_1", realtime_session_id="rt_1", session_epoch=1)
    error = RtcError(request_id="req_002", code="WEBRTC_CONNECTION_FAILED", message="실패")

    assert authenticated.model_dump(mode="json", by_alias=True) == {
        "type": "session.authenticated",
        "requestId": "req_001",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
    }
    assert answer.model_dump(mode="json", by_alias=True) == {
        "type": "rtc.answer",
        "requestId": "req_002",
        "sdp": {"type": "answer", "sdp": "v=0..."},
    }
    assert connected.model_dump(mode="json", by_alias=True) == {
        "type": "rtc.connected",
        "conversationId": "conv_1",
        "realtimeSessionId": "rt_1",
        "sessionEpoch": 1,
    }
    assert error.model_dump(mode="json", by_alias=True) == {
        "type": "rtc.error",
        "requestId": "req_002",
        "code": "WEBRTC_CONNECTION_FAILED",
        "message": "실패",
    }
