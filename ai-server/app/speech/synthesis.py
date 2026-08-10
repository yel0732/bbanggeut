"""Per-response speech synthesis: text in, paced audio + status events out (T19).

One service per connection. For each AI turn it runs a synthesis task that
streams PCM from the Provider, frames it into the response's queue, and emits
``assistant.audio.started`` / ``completed`` / ``failed`` (REALTIME_EVENT_CONTRACT
§6.2). The task is owned by the service so ``aclose`` can cancel and await it
(§8), and every chunk re-checks cancellation so a barged-in response stops
feeding the queue at once (ADR-012).

``completed`` fires when the last frame has been *consumed by the track*, not
when synthesis finishes — the child's audio is still playing after the Provider
stream ends, and the FE's speaking-state UI (T20) must line up with real sound
(W04 plan §11-C).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
import time
from collections.abc import Callable
from datetime import datetime, timezone

from app.events.outbound import ConversationEventPublisher
from app.speech.speaker import PcmFramer, ResponseAudioQueue, SpeakerHandle
from app.speech.tts_client import TTS_OUTPUT_FORMAT, SpeechSynthesizer, TtsError

# 취소 판정은 세션의 ResponseRegistry를 직접 알지 않고 주입 콜백으로 받는다
# (capability가 orchestration을 참조하지 않도록 — manager의 epoch 훅과 같은 방식).
CancelCheck = Callable[[str], bool]
# (turn_id, turn_seq, response_id, text, audio_started_at, audio_completed_at)
# — 송신 완료 시점(wall clock)을 W05 Stream 발행에 넘긴다. capability가 Stream을
# 직접 알지 않도록 콜백 주입(§1 호환 수정 5).
CompletedCallback = Callable[[str, int, str, str, datetime, datetime], None]

_TTS_FAILED = "TTS_FAILED"
_DRAIN_POLL_SECONDS = 0.02


class SpeechSynthesisService:
    """Owns one connection's synthesis tasks and their lifecycle."""

    def __init__(
        self,
        synthesizer: SpeechSynthesizer,
        speaker: SpeakerHandle,
        publisher: ConversationEventPublisher,
        is_cancelled: CancelCheck,
        logger: logging.Logger,
        on_completed: CompletedCallback | None = None,
        clock: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
    ) -> None:
        self._synthesizer = synthesizer
        self._speaker = speaker
        self._publisher = publisher
        self._is_cancelled = is_cancelled
        self._logger = logger
        self._on_completed = on_completed
        self._clock = clock
        self._tasks: dict[str, asyncio.Task] = {}
        # Latest task compatibility handle; ownership is the response-keyed map.
        self._task: asyncio.Task | None = None
        self._closed = False
        self._current_response_id: str | None = None

    @property
    def is_speaking(self) -> bool:
        """Whether this connection is producing AI audio right now.

        Spans synthesis *and* playback, not just playback. The STT pipeline
        reads it to apply the stricter Barge-in confirmation window even during
        the provider's time-to-first-byte period.
        """

        return self._current_response_id is not None

    def speak(self, turn_id: str, turn_seq: int, response_id: str, text: str) -> None:
        """Start synthesizing one AI turn's text. Non-blocking."""

        if self._closed or self._is_cancelled(response_id):
            return
        # A previous turn's playback is superseded by this one; begin_response
        # flushes it. Only one AI turn speaks at a time.
        queue = self._speaker.begin_response(response_id)
        self._current_response_id = response_id
        task = asyncio.create_task(
            self._run(turn_id, turn_seq, response_id, text, queue)
        )
        self._tasks[response_id] = task
        self._task = task

    def interrupt_response(self, response_id: str) -> bool:
        """Stop one response immediately while its provider task winds down safely.

        The ResponseRegistry is marked cancelled by the caller before this method.
        Flushing is synchronous so the outbound track becomes silent on the next
        20 ms frame; the provider task observes cancellation/flush on its next
        chunk and remains tracked until then.
        """

        if self._current_response_id != response_id:
            return False
        self._speaker.cancel_response(response_id)
        self._current_response_id = None
        task = self._tasks.get(response_id)
        if task is not None and not task.done():
            # Media callbacks are synchronous, so cancellation is requested here;
            # _run owns final cleanup and aclose still awaits any remaining task.
            task.cancel()
        return True

    async def aclose(self) -> None:
        """Cancel in-flight synthesis and await the task. Idempotent."""

        self._closed = True
        tasks = list(self._tasks.values())
        self._tasks.clear()
        self._task = None
        self._current_response_id = None
        for task in tasks:
            task.cancel()
        for task in tasks:
            with contextlib.suppress(asyncio.CancelledError):
                await task

    async def _run(
        self,
        turn_id: str,
        turn_seq: int,
        response_id: str,
        text: str,
        queue: ResponseAudioQueue,
    ) -> None:
        try:
            await self._synthesize(turn_id, turn_seq, response_id, text, queue)
        finally:
            # 성공·실패·취소 어느 경로로 끝나도 출력 활성 상태는 반드시 해제한다.
            self._tasks.pop(response_id, None)
            if self._current_response_id == response_id:
                self._current_response_id = None

    async def _synthesize(
        self,
        turn_id: str,
        turn_seq: int,
        response_id: str,
        text: str,
        queue: ResponseAudioQueue,
    ) -> None:
        framer = PcmFramer(TTS_OUTPUT_FORMAT)
        started = False
        pcm_bytes = 0
        started_at = time.monotonic()
        audio_started_at: datetime | None = None
        try:
            async for chunk in self._synthesizer.synthesize(text):
                if self._is_cancelled(response_id) or queue.is_flushed:
                    queue.flush()
                    self._logger.info(
                        "synthesis cancelled mid-stream; discarding",
                        extra={"turn_id": turn_id, "response_id": response_id},
                    )
                    return
                for frame in framer.push(chunk):
                    if not started:
                        started = True
                        audio_started_at = self._clock()
                        self._emit_started(turn_id, turn_seq, response_id, started_at)
                    pcm_bytes += frame.samples * TTS_OUTPUT_FORMAT.bytes_per_sample
                    await queue.put(frame)
            for frame in framer.flush():
                if not started:
                    started = True
                    audio_started_at = self._clock()
                    self._emit_started(turn_id, turn_seq, response_id, started_at)
                pcm_bytes += frame.samples * TTS_OUTPUT_FORMAT.bytes_per_sample
                await queue.put(frame)
        except TtsError as error:
            queue.flush()
            self._logger.warning(
                "speech synthesis failed",
                extra={"turn_id": turn_id, "failure_code": error.code.value},
            )
            self._publisher.publish(
                "assistant.audio.failed",
                {"code": _TTS_FAILED, "message": "음성 합성에 실패했습니다.", "recoverable": True},
                turn_id=turn_id,
                turn_seq=turn_seq,
                response_id=response_id,
            )
            return

        queue.finish()
        if not started:
            # Provider returned no audio at all — nothing to complete.
            return
        await self._await_drained(response_id, queue)
        if queue.is_flushed:
            return  # cancelled while draining — no completed event
        self._publisher.publish(
            "assistant.audio.completed",
            {
                "audioFormat": "opus",
                "sampleRate": 48000,  # what the peer receives after Opus encode
                "durationMs": self._duration_ms(pcm_bytes),
            },
            turn_id=turn_id,
            turn_seq=turn_seq,
            response_id=response_id,
        )
        if self._on_completed is not None and audio_started_at is not None:
            # 송신 완료 시각 = 지금(drain 완료 직후). W05가 AI_TURN_COMPLETED로 발행.
            self._on_completed(
                turn_id, turn_seq, response_id, text, audio_started_at, self._clock()
            )

    def _emit_started(
        self, turn_id: str, turn_seq: int, response_id: str, started_at: float
    ) -> None:
        self._logger.info(
            "first tts audio ready",
            extra={
                "response_id": response_id,
                "first_audio_ms": round((time.monotonic() - started_at) * 1000),
            },
        )
        self._publisher.publish(
            "assistant.audio.started",
            {"audioFormat": "opus", "sampleRate": 48000},
            turn_id=turn_id,
            turn_seq=turn_seq,
            response_id=response_id,
        )

    async def _await_drained(self, response_id: str, queue: ResponseAudioQueue) -> None:
        while not queue.drained and not queue.is_flushed:
            await asyncio.sleep(_DRAIN_POLL_SECONDS)

    @staticmethod
    def _duration_ms(pcm_bytes: int) -> int:
        seconds = TTS_OUTPUT_FORMAT.duration_seconds(pcm_bytes)
        return round(seconds * 1000)
