"""Redis Stream event contract and producer (DIARY-3-W05, T21/T22).

FastAPI produces confirmed conversation events onto the ``conversation-events``
Stream; Spring (W06) consumes them into MySQL. The two sides share the *same*
JSON — the fixtures in ``tests/events/test_stream_schema.py`` and Spring's
``ConversationStreamEventContractTest`` are byte-for-byte identical, which (with
the design docs kept out of git locally) is the real contract guarantee.

Design rules that live here (jira/plan/DIARY-3-W05.md §2):

- **Only confirmed events reach the Stream.** Every publish call sits at a
  "just confirmed" point in the dialogue/synthesis flow; partial/cancelled
  results have no code path here.
- **Publish success is not persistence.** The producer decides no business
  state; MySQL is W06's job under at-least-once + idempotency.
- **One outbox, one writer.** Events go on a bounded queue and a single writer
  task ``xadd``s them in order, so retries never reorder and publishing never
  blocks the LLM/TTS flow (§8: session-owned task, cancel-then-await).
- **Redis errors are not swallowed.** Retries with backoff, then a structured
  ``error`` log (identifiers only) and a degraded flag — no DataChannel error,
  since the child has no recovery to perform.
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
import uuid
from collections.abc import Callable
from datetime import datetime, timezone
from enum import StrEnum
from typing import Any

from redis.exceptions import RedisError

from app.dialogue.schemas import DiarySlots

STREAM_KEY = "conversation-events"
_EVENT_FIELD = "event"  # xadd field carrying the Envelope JSON (§11-G)

Clock = Callable[[], datetime]
EventIdFactory = Callable[[], str]


class StreamEventType(StrEnum):
    SESSION_STARTED = "SESSION_STARTED"
    CHILD_TURN_FINALIZED = "CHILD_TURN_FINALIZED"
    AI_TURN_GENERATED = "AI_TURN_GENERATED"
    AI_TURN_COMPLETED = "AI_TURN_COMPLETED"
    AI_TURN_INTERRUPTED = "AI_TURN_INTERRUPTED"
    SESSION_COMPLETED = "SESSION_COMPLETED"
    SESSION_FAILED = "SESSION_FAILED"


def _default_clock() -> datetime:
    return datetime.now(timezone.utc)


def _default_event_id_factory() -> str:
    return f"evt_internal_{uuid.uuid4().hex}"


def _slots_payload(slots: DiarySlots) -> dict[str, Any]:
    # 계약은 camelCase — DiarySlots의 alias 직렬화를 그대로 쓴다.
    return slots.model_dump(mode="json", by_alias=True)


class ConversationStreamProducer:
    """One session's Stream publisher: bounded outbox + single writer task."""

    def __init__(
        self,
        redis_client,
        *,
        conversation_id: str,
        realtime_session_id: str,
        session_epoch: int,
        logger: logging.Logger,
        max_attempts: int = 3,
        retry_backoff_seconds: float = 0.5,
        outbox_size: int = 256,
        close_drain_seconds: float = 2.0,
        clock: Clock = _default_clock,
        event_id_factory: EventIdFactory = _default_event_id_factory,
    ) -> None:
        self._redis = redis_client
        self._conversation_id = conversation_id
        self._realtime_session_id = realtime_session_id
        self._session_epoch = session_epoch
        self._logger = logger
        self._max_attempts = max(1, max_attempts)
        self._retry_backoff_seconds = retry_backoff_seconds
        self._close_drain_seconds = close_drain_seconds
        self._clock = clock
        self._event_id_factory = event_id_factory
        self._outbox: asyncio.Queue[str] = asyncio.Queue(maxsize=outbox_size)
        self._writer: asyncio.Task | None = None
        self._degraded = False
        self._closed = False

    @property
    def is_degraded(self) -> bool:
        return self._degraded

    def start(self) -> None:
        if self._writer is None:
            self._writer = asyncio.create_task(self._run_writer())

    # --- publish API (동기, 즉시 반환) -------------------------------------

    def publish_session_started(self, connected_at: datetime) -> None:
        self._enqueue(
            StreamEventType.SESSION_STARTED,
            {"connectedAt": _iso(connected_at)},
        )

    def publish_child_turn_finalized(
        self,
        *,
        turn_id: str,
        turn_seq: int,
        text: str,
        stt_confidence: float | None,
        started_at: datetime,
        completed_at: datetime,
        topic_type: str | None = None,
    ) -> None:
        self._enqueue(
            StreamEventType.CHILD_TURN_FINALIZED,
            {
                "text": text,
                "sttConfidence": stt_confidence,
                "startedAt": _iso(started_at),
                "completedAt": _iso(completed_at),
                "topicType": topic_type,
            },
            turn_id=turn_id,
            turn_seq=turn_seq,
        )

    def publish_ai_turn_generated(
        self,
        *,
        turn_id: str,
        turn_seq: int,
        response_id: str,
        text: str,
        question_text: str | None = None,
        target_slot: str | None,
        topic_type: str,
        topic_slots: DiarySlots,
        slots_topic_type: str | None = None,
        topic_question_count: int | None = None,
        session_question_count: int | None = None,
        topic_completion_reason: str | None = None,
    ) -> None:
        self._enqueue(
            StreamEventType.AI_TURN_GENERATED,
            {
                "text": text,
                "questionText": question_text or text,
                "targetSlot": target_slot,
                # 재연결 스냅숏이 최신 슬롯을 담게 하는 동기화 필드(§11-B).
                "topicType": topic_type,
                "slotsTopicType": slots_topic_type or topic_type,
                "topicSlots": _slots_payload(topic_slots),
                "topicQuestionCount": topic_question_count,
                "sessionQuestionCount": session_question_count,
                "topicCompletionReason": topic_completion_reason,
            },
            turn_id=turn_id,
            turn_seq=turn_seq,
            response_id=response_id,
        )

    def publish_ai_turn_completed(
        self,
        *,
        turn_id: str,
        turn_seq: int,
        response_id: str,
        text: str,
        audio_started_at: datetime,
        audio_completed_at: datetime,
    ) -> None:
        self._enqueue(
            StreamEventType.AI_TURN_COMPLETED,
            {
                "text": text,
                "audioStartedAt": _iso(audio_started_at),
                "audioCompletedAt": _iso(audio_completed_at),
            },
            turn_id=turn_id,
            turn_seq=turn_seq,
            response_id=response_id,
        )

    def publish_session_completed(self, *, reason: str, completed_at: datetime) -> None:
        self._enqueue(
            StreamEventType.SESSION_COMPLETED,
            {"reason": reason, "completedAt": _iso(completed_at)},
        )

    def publish_session_failed(
        self, *, code: str, message: str, failed_at: datetime
    ) -> None:
        # 발행 API만 준비 — 실제 배선은 DIARY-4 장애 복구(§11-F).
        self._enqueue(
            StreamEventType.SESSION_FAILED,
            {"code": code, "message": message, "failedAt": _iso(failed_at)},
        )

    async def aclose(self) -> None:
        """Drain briefly, then stop the writer. Idempotent."""

        self._closed = True
        writer = self._writer
        self._writer = None
        if writer is None:
            return
        # 남은 이벤트를 마저 배출한다. writer는 매 이벤트를 성공·드롭·재시도 소진 중
        # 하나로 반드시 끝내므로 join()은 유계 시간에 완료된다 — timeout은 xadd 자체가
        # 무한정 매달리는 경우의 안전장치일 뿐이다.
        with contextlib.suppress(TimeoutError):
            await asyncio.wait_for(self._outbox.join(), timeout=self._close_drain_seconds)
        writer.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await writer

    # --- internals ---------------------------------------------------------

    def _enqueue(
        self,
        event_type: StreamEventType,
        payload: dict[str, Any],
        *,
        turn_id: str | None = None,
        turn_seq: int | None = None,
        response_id: str | None = None,
    ) -> None:
        if self._closed:
            return
        envelope = {
            "eventId": self._event_id_factory(),
            "eventType": event_type.value,
            "conversationId": self._conversation_id,
            "realtimeSessionId": self._realtime_session_id,
            "sessionEpoch": self._session_epoch,
            "turnId": turn_id,
            "turnSeq": turn_seq,
            "responseId": response_id,
            "occurredAt": _iso(self._clock()),
            "payload": payload,
        }
        serialized = json.dumps(envelope, ensure_ascii=False)
        try:
            self._outbox.put_nowait(serialized)
        except asyncio.QueueFull:
            # 순서 있는 부분 손실이 무순서 손실보다 낫다 — 새 이벤트를 드롭(§11-D).
            # eventId 멱등이라 재발행 복구는 W08에서 재론한다.
            self._degraded = True
            self._logger.error(
                "conversation stream outbox full; event dropped",
                extra={
                    "conversation_id": self._conversation_id,
                    "event_type": event_type.value,
                },
            )

    async def _run_writer(self) -> None:
        while True:
            serialized = await self._outbox.get()
            try:
                await self._xadd_with_retry(serialized)
            finally:
                self._outbox.task_done()

    async def _xadd_with_retry(self, serialized: str) -> None:
        for attempt in range(1, self._max_attempts + 1):
            try:
                await self._redis.xadd(STREAM_KEY, {_EVENT_FIELD: serialized})
                return
            except RedisError:
                if attempt >= self._max_attempts:
                    self._degraded = True
                    # 원문(text 포함)은 로그에 넣지 않는다 — 식별자만(§13).
                    self._logger.error(
                        "conversation stream publish failed after retries",
                        extra={
                            "conversation_id": self._conversation_id,
                            "attempts": attempt,
                        },
                    )
                    return
                await asyncio.sleep(self._retry_backoff_seconds * attempt)


def _iso(moment: datetime) -> str:
    if moment.tzinfo is None:
        raise ValueError("stream event datetimes must be timezone-aware")
    return moment.isoformat()
