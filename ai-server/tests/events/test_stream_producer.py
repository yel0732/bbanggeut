"""Stream producer behavior tests (T22) — Fake redis, no network."""

import asyncio
import json
import logging
from datetime import datetime, timezone

import pytest
from redis.exceptions import ConnectionError as RedisConnectionError

from app.events.stream import ConversationStreamProducer

_LOGGER = logging.getLogger("test.events.stream_producer")
_FIXED = datetime(2026, 7, 28, 10, 0, tzinfo=timezone.utc)


class FakeRedis:
    def __init__(self, *, fail_times: int = 0) -> None:
        self.entries: list[str] = []
        self._fail_times = fail_times
        self.attempts = 0

    async def xadd(self, key: str, fields: dict) -> None:
        self.attempts += 1
        if self.attempts <= self._fail_times:
            raise RedisConnectionError("redis down")
        self.entries.append(fields["event"])


def _producer(redis, **kwargs) -> ConversationStreamProducer:
    ids = iter(f"evt_{i}" for i in range(1, 500))
    producer = ConversationStreamProducer(
        redis,
        conversation_id="conv_1",
        realtime_session_id="rt_1",
        session_epoch=1,
        logger=_LOGGER,
        clock=lambda: _FIXED,
        event_id_factory=lambda: next(ids),
        retry_backoff_seconds=0.001,
        **kwargs,
    )
    producer.start()
    return producer


def _types(entries: list[str]) -> list[str]:
    return [json.loads(e)["eventType"] for e in entries]


class TestOrdering:
    @pytest.mark.asyncio
    async def test_events_are_written_in_publish_order(self) -> None:
        redis = FakeRedis()
        producer = _producer(redis)

        producer.publish_session_started(_FIXED)
        producer.publish_child_turn_finalized(
            turn_id="t1", turn_seq=1, text="a", stt_confidence=None,
            started_at=_FIXED, completed_at=_FIXED,
        )
        producer.publish_session_completed(reason="USER_FINISHED", completed_at=_FIXED)
        await producer.aclose()

        assert _types(redis.entries) == [
            "SESSION_STARTED",
            "CHILD_TURN_FINALIZED",
            "SESSION_COMPLETED",
        ]


class TestRetry:
    @pytest.mark.asyncio
    async def test_retries_then_succeeds(self) -> None:
        redis = FakeRedis(fail_times=2)  # 2번 실패 후 3번째 성공
        producer = _producer(redis, max_attempts=3)

        producer.publish_session_started(_FIXED)
        await producer.aclose()

        assert len(redis.entries) == 1
        assert redis.attempts == 3
        assert not producer.is_degraded

    @pytest.mark.asyncio
    async def test_final_failure_logs_error_and_marks_degraded(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        redis = FakeRedis(fail_times=99)
        producer = _producer(redis, max_attempts=3)

        with caplog.at_level(logging.ERROR, logger=_LOGGER.name):
            producer.publish_session_started(_FIXED)
            await producer.aclose()

        assert redis.entries == []
        assert producer.is_degraded
        assert "publish failed after retries" in caplog.text

    @pytest.mark.asyncio
    async def test_writer_continues_after_a_failed_event(self) -> None:
        """한 이벤트 최종 실패가 이후 이벤트 발행을 막지 않는다."""

        redis = FakeRedis(fail_times=3)  # 첫 이벤트(3시도) 실패, 이후 성공
        producer = _producer(redis, max_attempts=3)

        producer.publish_session_started(_FIXED)  # 실패할 것
        producer.publish_session_completed(reason="USER_FINISHED", completed_at=_FIXED)
        await producer.aclose()

        assert _types(redis.entries) == ["SESSION_COMPLETED"]

    @pytest.mark.asyncio
    async def test_failure_log_does_not_contain_the_transcript(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        redis = FakeRedis(fail_times=99)
        producer = _producer(redis, max_attempts=1)

        with caplog.at_level(logging.ERROR, logger=_LOGGER.name):
            producer.publish_child_turn_finalized(
                turn_id="t1", turn_seq=1, text="비밀 발화입니다", stt_confidence=None,
                started_at=_FIXED, completed_at=_FIXED,
            )
            await producer.aclose()

        assert "비밀" not in caplog.text


class TestOverflow:
    @pytest.mark.asyncio
    async def test_outbox_full_drops_new_event_and_marks_degraded(
        self, caplog: pytest.LogCaptureFixture
    ) -> None:
        # writer가 첫 이벤트에서 막혀 있게 해 outbox를 채운다.
        gate = asyncio.Event()

        class _BlockingRedis(FakeRedis):
            async def xadd(self, key, fields):
                await gate.wait()
                await super().xadd(key, fields)

        redis = _BlockingRedis()
        producer = _producer(redis, outbox_size=1)

        with caplog.at_level(logging.ERROR, logger=_LOGGER.name):
            producer.publish_session_started(_FIXED)  # writer가 집어 xadd에서 대기
            await asyncio.sleep(0)  # writer가 outbox에서 하나 꺼내게 한다
            producer.publish_session_started(_FIXED)  # outbox 채움(size 1)
            producer.publish_session_started(_FIXED)  # 가득 → 드롭
            gate.set()
            await producer.aclose()

        assert producer.is_degraded
        assert "outbox full" in caplog.text


class TestLifecycle:
    @pytest.mark.asyncio
    async def test_aclose_is_idempotent(self) -> None:
        producer = _producer(FakeRedis())
        producer.publish_session_started(_FIXED)

        await producer.aclose()
        await producer.aclose()

    @pytest.mark.asyncio
    async def test_publish_after_close_is_ignored(self) -> None:
        redis = FakeRedis()
        producer = _producer(redis)
        await producer.aclose()

        producer.publish_session_started(_FIXED)

        assert redis.entries == []
