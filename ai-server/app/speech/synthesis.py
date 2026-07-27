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

from app.events.outbound import ConversationEventPublisher
from app.speech.speaker import PcmFramer, ResponseAudioQueue, SpeakerHandle
from app.speech.tts_client import TTS_OUTPUT_FORMAT, SpeechSynthesizer, TtsError

# 취소 판정은 세션의 ResponseRegistry를 직접 알지 않고 주입 콜백으로 받는다
# (capability가 orchestration을 참조하지 않도록 — manager의 epoch 훅과 같은 방식).
CancelCheck = Callable[[str], bool]

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
    ) -> None:
        self._synthesizer = synthesizer
        self._speaker = speaker
        self._publisher = publisher
        self._is_cancelled = is_cancelled
        self._logger = logger
        self._task: asyncio.Task | None = None
        self._closed = False

    def speak(self, turn_id: str, turn_seq: int, response_id: str, text: str) -> None:
        """Start synthesizing one AI turn's text. Non-blocking."""

        if self._closed or self._is_cancelled(response_id):
            return
        # A previous turn's playback is superseded by this one; begin_response
        # flushes it. Only one AI turn speaks at a time.
        queue = self._speaker.begin_response(response_id)
        self._task = asyncio.create_task(
            self._run(turn_id, turn_seq, response_id, text, queue)
        )

    async def aclose(self) -> None:
        """Cancel in-flight synthesis and await the task. Idempotent."""

        self._closed = True
        task, self._task = self._task, None
        if task is not None:
            task.cancel()
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
        framer = PcmFramer(TTS_OUTPUT_FORMAT)
        started = False
        pcm_bytes = 0
        started_at = time.monotonic()
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
                        self._emit_started(turn_id, turn_seq, response_id, started_at)
                    pcm_bytes += frame.samples * TTS_OUTPUT_FORMAT.bytes_per_sample
                    await queue.put(frame)
            for frame in framer.flush():
                if not started:
                    started = True
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
