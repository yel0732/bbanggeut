"""Per-session STT orchestration: Frames in, confirmed turns out (T02b–T05).

This is the ``AudioSink`` the audio bridge feeds. It owns one session's
converter, utterance buffer, turn numbering and in-flight recognition task, and
it is the single place where the batch shape of whisper-1 becomes visible:
audio simply accumulates until somebody declares the turn over.

``finalize_turn()`` is the seam with W02. Until Turn Detection exists nothing
calls it during a real conversation — W01's tests drive it directly, and W02
will wire a VAD to it. It is also called internally when the utterance hits its
duration cap (§3-3).

Failure handling is deliberately split (plan §4):

- **Rejected audio** (guard says silence/artifact) is *not* an error. It means
  the child did not speak, and the recovery policy for that is W03's, per
  ADR-018. No ``stt.final`` is emitted and no ``error`` event is sent.
- **Provider failure** emits an ``error`` event with ``STT_FAILED``
  (FUNCTIONAL_SPEC.md §22) and abandons the turn. There is no retry (§11-E).
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from collections.abc import Awaitable, Callable

from av import AudioFrame

from app.events.outbound import ConversationEventPublisher
from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat
from app.speech.frame_converter import AudioConversionError, AudioFrameConverter
from app.speech.hallucination import HallucinationGuard, SpeechRejectionReason
from app.speech.recognizer import SpeechRecognizer, SttError, SttResult
from app.speech.turn_state import SttTurn, TurnSequencer
from app.speech.utterance_buffer import UtteranceBuffer

FinalTurnCallback = Callable[[SttTurn, SttResult], Awaitable[None]]

_STT_FAILED = "STT_FAILED"


class SessionSttPipeline:
    """One connection's audio -> Final STT path. Never shared between sessions."""

    def __init__(
        self,
        recognizer: SpeechRecognizer,
        publisher: ConversationEventPublisher,
        logger: logging.Logger,
        *,
        guard: HallucinationGuard | None = None,
        max_utterance_seconds: float = 30.0,
        audio_format: AudioFormat = STT_INPUT_FORMAT,
        turn_sequencer: TurnSequencer | None = None,
        on_final: FinalTurnCallback | None = None,
    ) -> None:
        self._recognizer = recognizer
        self._publisher = publisher
        self._logger = logger
        self._guard = guard or HallucinationGuard()
        self._converter = AudioFrameConverter(audio_format)
        self._buffer = UtteranceBuffer(max_utterance_seconds, audio_format)
        self._turns = turn_sequencer or TurnSequencer()
        self._on_final = on_final
        self._turn: SttTurn | None = None
        self._task: asyncio.Task | None = None
        self._closed = False

    @property
    def current_turn(self) -> SttTurn | None:
        return self._turn

    @property
    def has_audio(self) -> bool:
        return not self._buffer.is_empty

    # --- AudioSink -------------------------------------------------------

    def on_frame(self, frame: AudioFrame) -> None:
        """Accept one inbound Frame. Must not block the media loop."""

        if self._closed:
            return
        try:
            chunk = self._converter.convert(frame)
        except AudioConversionError:
            self._logger.warning("inbound audio frame could not be converted")
            return
        self._ensure_turn()
        if not self._buffer.append(chunk):
            # Cap reached: close this turn with the audio we have rather than
            # splicing the utterance (§3-3).
            self._logger.info(
                "utterance reached its duration cap; finalizing",
                extra={"turn_id": self._turn.turn_id if self._turn else None},
            )
            self._schedule_finalize()

    async def aclose(self) -> None:
        """Cancel in-flight recognition and drop buffered audio. Idempotent."""

        self._closed = True
        task, self._task = self._task, None
        if task is not None:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task
        self._converter.reset()
        self._buffer.close()
        self._turn = None

    # --- turn boundary ---------------------------------------------------

    def _schedule_finalize(self) -> None:
        if self._task is not None and not self._task.done():
            return  # a finalisation is already running for this turn
        self._task = asyncio.create_task(self.finalize_turn())

    async def finalize_turn(self) -> SttResult | None:
        """Recognise the buffered utterance and confirm it as one turn.

        Called by W02's Turn Detection, or internally on buffer overflow.
        """

        if self._closed:
            return None
        turn = self._turn
        if turn is None or turn.is_finalized:
            return None

        self._drain_converter_tail()
        audio_seconds = self._buffer.duration_seconds
        if self._buffer.is_empty or self._guard.should_skip_call(audio_seconds):
            # Layer 1/2 of the guard: too little audio to be worth a call.
            self._discard_turn(turn, SpeechRejectionReason.TOO_SHORT, audio_seconds)
            return None

        wav_bytes = self._buffer.to_wav()
        try:
            result = await self._recognizer.transcribe(wav_bytes, audio_seconds)
        except SttError as error:
            self._fail_turn(turn, error)
            return None

        rejection = self._guard.evaluate(result)
        if rejection is not None:
            self._discard_turn(turn, rejection, audio_seconds)
            return None

        if not turn.accept_final(result):
            # A concurrent finalisation already confirmed this turn; the second
            # result is dropped so no duplicate question is started (§16).
            self._logger.info(
                "duplicate final dropped", extra={"turn_id": turn.turn_id}
            )
            return None

        self._start_new_utterance()
        self._publisher.publish(
            "stt.final",
            {"text": result.text, "confidence": result.confidence},
            turn_id=turn.turn_id,
            turn_seq=turn.turn_seq,
        )
        if self._on_final is not None:
            await self._on_final(turn, result)
        return result

    # --- internals -------------------------------------------------------

    def _ensure_turn(self) -> None:
        if self._turn is None or self._turn.is_finalized:
            self._turn = self._turns.next_turn()

    def _drain_converter_tail(self) -> None:
        try:
            tail = self._converter.flush()
        except AudioConversionError:
            self._logger.warning("converter tail could not be flushed")
            return
        if not tail.is_empty and not self._buffer.is_closed:
            self._buffer.append(tail)

    def _start_new_utterance(self) -> None:
        self._buffer.reset()
        self._turn = None

    def _discard_turn(
        self,
        turn: SttTurn,
        reason: SpeechRejectionReason,
        audio_seconds: float,
    ) -> None:
        """No child speech here — not an error, so no error event (ADR-018)."""

        turn.abandon()
        self._logger.info(
            "utterance discarded before becoming a turn",
            extra={
                "turn_id": turn.turn_id,
                "rejection_reason": reason.value,
                "audio_seconds": round(audio_seconds, 2),
            },
        )
        self._start_new_utterance()

    def _fail_turn(self, turn: SttTurn, error: SttError) -> None:
        turn.abandon()
        self._logger.warning(
            "speech recognition failed",
            extra={"turn_id": turn.turn_id, "failure_code": error.code.value},
        )
        self._publisher.publish(
            "error",
            {
                "code": _STT_FAILED,
                "message": "음성 인식에 실패했습니다.",
                "recoverable": True,
            },
            turn_id=turn.turn_id,
            turn_seq=turn.turn_seq,
        )
        self._start_new_utterance()
