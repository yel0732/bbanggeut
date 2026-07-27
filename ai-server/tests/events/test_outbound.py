"""Outbound conversation-event publishing tests (T04)."""

import logging
from datetime import datetime, timezone

import pytest

from app.events.envelope import ConversationEventEnvelope
from app.events.outbound import ConversationEventPublisher
from app.webrtc.data_channel import FakeDataChannel

_LOGGER = logging.getLogger("test.events.outbound")


def _publisher(channel: FakeDataChannel | None = None) -> tuple[ConversationEventPublisher, FakeDataChannel]:
    channel = channel or FakeDataChannel()
    ids = iter(f"evt_{index}" for index in range(1, 100))
    publisher = ConversationEventPublisher(
        channel,
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=3,
        logger=_LOGGER,
        clock=lambda: datetime(2026, 7, 27, 10, 0, tzinfo=timezone.utc),
        event_id_factory=lambda: next(ids),
    )
    return publisher, channel


def test_publishes_a_contract_shaped_envelope() -> None:
    publisher, channel = _publisher()

    assert publisher.publish("stt.final", {"text": "재미있었어.", "confidence": 0.74},
                             turn_id="turn_1", turn_seq=1) is True

    sent = channel.sent[0]
    assert sent["type"] == "stt.final"
    assert sent["conversationId"] == "conv_1"
    assert sent["realtimeSessionId"] == "rt_1"
    assert sent["sessionEpoch"] == 3
    assert sent["turnId"] == "turn_1"
    assert sent["turnSeq"] == 1
    assert sent["responseId"] is None
    assert sent["payload"] == {"text": "재미있었어.", "confidence": 0.74}


def test_published_envelope_validates_against_the_inbound_model() -> None:
    """Same §3 Envelope both directions — parse our own output with W04's model."""

    publisher, channel = _publisher()
    publisher.publish("stt.final", {"text": "응."}, turn_id="turn_1", turn_seq=1)

    envelope = ConversationEventEnvelope.model_validate(channel.sent[0])

    assert envelope.event_id == "evt_1"
    assert envelope.occurred_at.tzinfo is not None  # §7: timezone-aware


def test_event_seq_increments_per_connection() -> None:
    publisher, channel = _publisher()

    publisher.publish("conversation.state.changed", {"currentState": "LISTENING"})
    publisher.publish("stt.final", {"text": "응."}, turn_id="t1", turn_seq=1)

    assert [event["eventSeq"] for event in channel.sent] == [1, 2]
    assert publisher.last_event_seq == 2


def test_event_ids_come_from_the_injected_factory() -> None:
    publisher, channel = _publisher()

    publisher.publish("error", {"code": "STT_FAILED"})
    publisher.publish("error", {"code": "STT_FAILED"})

    assert [event["eventId"] for event in channel.sent] == ["evt_1", "evt_2"]


def test_omitted_payload_becomes_an_empty_object() -> None:
    publisher, channel = _publisher()

    publisher.publish("assistant.response.created", turn_id="t1", response_id="resp_1")

    assert channel.sent[0]["payload"] == {}
    assert channel.sent[0]["responseId"] == "resp_1"


def test_closed_channel_is_reported_not_silently_dropped(
    caplog: pytest.LogCaptureFixture,
) -> None:
    channel = FakeDataChannel()
    channel.close()
    publisher, _ = _publisher(channel)

    with caplog.at_level(logging.WARNING, logger="test.events.outbound"):
        sent = publisher.publish("stt.final", {"text": "응."})

    assert sent is False
    assert channel.sent == []
    assert "data channel is closed" in caplog.text


def test_a_dropped_event_does_not_consume_a_sequence_number() -> None:
    channel = FakeDataChannel()
    channel.close()
    publisher, _ = _publisher(channel)

    publisher.publish("stt.final", {"text": "응."})

    assert publisher.last_event_seq == 0


def test_two_publishers_number_their_streams_independently() -> None:
    first, first_channel = _publisher()
    second, second_channel = _publisher()

    first.publish("error", {})
    first.publish("error", {})
    second.publish("error", {})

    assert first_channel.sent[-1]["eventSeq"] == 2
    assert second_channel.sent[-1]["eventSeq"] == 1


def test_no_partial_stt_event_type_is_emitted_anywhere() -> None:
    """whisper-1 is batch only and subtitles were dropped (plan §11-A).

    Guards the decision itself: if a future change starts emitting
    ``stt.partial`` again, that has to be a deliberate contract change rather
    than something that slips back in. Only executable string literals count —
    docstrings and comments explaining the decision are expected to mention it.
    """

    import ast
    from pathlib import Path

    def emitted_literals(tree: ast.Module) -> list[str]:
        docstrings = {
            id(node.body[0].value)
            for node in ast.walk(tree)
            if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef))
            and node.body
            and isinstance(node.body[0], ast.Expr)
            and isinstance(node.body[0].value, ast.Constant)
            and isinstance(node.body[0].value.value, str)
        }
        return [
            node.value
            for node in ast.walk(tree)
            if isinstance(node, ast.Constant)
            and isinstance(node.value, str)
            and id(node) not in docstrings
        ]

    app_root = Path(__file__).resolve().parents[2] / "app"
    offenders = [
        path.name
        for path in app_root.rglob("*.py")
        if "stt.partial" in emitted_literals(ast.parse(path.read_text(encoding="utf-8")))
    ]

    assert offenders == []
