"""Per-session STT orchestration: Frames in, confirmed turns out (T02b–T05).

This is the ``AudioSink`` the audio bridge feeds. It owns one session's
converter, utterance buffer, turn numbering and in-flight recognition task, and
it is the single place where the batch shape of whisper-1 becomes visible:
audio simply accumulates until somebody declares the turn over.

Turn boundaries come from the ``TurnDetector`` (W02) when a ``vad`` is
injected: leading silence never reaches the buffer (pre-roll excepted), and
end-of-silence calls ``request_finalize()``. Without a ``vad`` the pipeline
behaves as in W01 — everything is buffered and only the duration cap (or an
external ``finalize_turn()`` call, e.g. from tests) closes a turn.

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
from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk
from app.speech.frame_converter import AudioConversionError, AudioFrameConverter
from app.speech.hallucination import HallucinationGuard, SpeechRejectionReason
from app.speech.recognizer import SpeechRecognizer, SttError, SttResult
from app.speech.turn_detector import TurnDetectionConfig, TurnDetector
from app.speech.turn_state import SttTurn, TurnSequencer
from app.speech.utterance_buffer import UtteranceBuffer
from app.speech.vad import VoiceActivityDetector

FinalTurnCallback = Callable[[SttTurn, SttResult], Awaitable[None]]
NoSpeechCallback = Callable[[], None]
OutputActiveCheck = Callable[[], bool]
# AI가 말하는 중 확인된 사람 음성을 끼어들기로 처리한다. 실제 진행 중인 응답을
# 취소했을 때만 True를 반환한다.
BargeInCallback = Callable[[], bool]
# 수집된 발화가 확정 턴이 되지 못하고 버려졌다(너무 짧음·환각·STT 실패).
# barge-in 직후라면 중단된 응답의 재발화 근거가 된다.
TurnDiscardedCallback = Callable[[], None]

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
        vad: VoiceActivityDetector | None = None,
        turn_detection: TurnDetectionConfig | None = None,
        on_no_speech: NoSpeechCallback | None = None,
        is_output_active: OutputActiveCheck | None = None,
        on_barge_in: BargeInCallback | None = None,
        on_turn_discarded: TurnDiscardedCallback | None = None,
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
        self._pending_finalize = False
        self._closed = False
        self._is_output_active = is_output_active
        self._on_barge_in = on_barge_in
        self._on_turn_discarded = on_turn_discarded
        self._reject_confirmed_speech = False
        self._output_was_active = False
        self._detector: TurnDetector | None = None
        if vad is not None:
            self._detector = TurnDetector(
                vad,
                config=turn_detection,
                audio_format=audio_format,
                on_speech_start=self._handle_speech_start,
                on_turn_end=self.request_finalize,
                on_no_speech=self._handle_no_speech,
                is_output_active=is_output_active,
                logger=logger,
            )
        self._on_no_speech = on_no_speech

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
        output_active = self._is_output_active is not None and self._is_output_active()
        if output_active and not self._output_was_active and self.has_audio:
            # 아이가 AI의 LLM/TTS 준비 시간에 먼저 말을 시작한 뒤 출력이 켜지는
            # 경합. 이미 확인된 아이 발화를 버리지 않고 즉시 현재 응답을 중단한다.
            accepted = self._on_barge_in is not None and self._on_barge_in()
            if not accepted:
                self._discard_buffered_input()
        self._output_was_active = (
            self._is_output_active is not None and self._is_output_active()
        )
        try:
            chunk = self._converter.convert(frame)
        except AudioConversionError:
            self._logger.warning("inbound audio frame could not be converted")
            return
        if self._detector is None:
            self._accept_audio(chunk)
            return
        detected = self._detector.feed(chunk)
        if self._reject_confirmed_speech:
            # 출력 중 확인된 음성이지만 취소할 활성 응답이 없었다. 오디오와 detector
            # 상태를 함께 버려 다음 정상 발화와 이어붙지 않게 한다.
            self._reject_confirmed_speech = False
            self._detector.reset()
            self._converter.reset()
            return
        for audio in detected:
            self._accept_audio(audio)
        self._output_was_active = (
            self._is_output_active is not None and self._is_output_active()
        )

    def _accept_audio(self, chunk: PcmAudioChunk) -> None:
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
        if self._detector is not None:
            self._detector.reset()
        self._converter.reset()
        self._buffer.close()
        self._turn = None

    # --- turn boundary ---------------------------------------------------

    def request_finalize(self) -> None:
        """Ask for the current utterance to be confirmed as a turn.

        The one entry point for external callers (the Turn Detector, and later
        W02+ consumers): the work runs as a pipeline-owned task, so
        ``aclose()`` can cancel it — awaiting ``finalize_turn()`` directly
        would escape that tracking (W01 review follow-up, W02 plan §3-5).
        Duplicate requests while one is running are dropped.
        """

        if self._closed:
            return
        self._schedule_finalize()

    def _schedule_finalize(self) -> None:
        if self._task is not None and not self._task.done():
            # A finalisation is already running. That used to mean "same turn,
            # duplicate request — skip", but W03 lengthened the critical
            # section: ``on_final`` awaits the dialogue pipeline (~1–3s LLM),
            # and a child can finish their NEXT utterance inside that window.
            # Silently skipping would leave that utterance buffered but never
            # finalised until yet another turn ended (found in W03 review) —
            # so the request is remembered and re-run once the current one
            # completes, preserving turn order.
            self._pending_finalize = True
            return
        self._task = asyncio.create_task(self._run_finalize())

    async def _run_finalize(self) -> None:
        await self.finalize_turn()
        while self._pending_finalize and not self._closed:
            self._pending_finalize = False
            await self.finalize_turn()

    async def finalize_turn(self) -> SttResult | None:
        """Recognise the buffered utterance and confirm it as one turn.

        Internal/test entry point — external callers use ``request_finalize()``
        so the run is owned (and cancellable) by the pipeline. Also invoked on
        buffer overflow.
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

        # Snapshot and hand the buffer over to the NEXT utterance *before*
        # awaiting the Provider. Recognition takes ~2s (measured); any frames
        # arriving in that window are the child's next speech — resetting after
        # the await would silently destroy them (found in review: the overflow
        # path, where the child is mid-sentence, made this a real audio loss).
        # It also collapses the concurrent-finalisation race to a no-op: the
        # second caller now finds an empty buffer/no turn instead of firing a
        # second Provider call.
        wav_bytes = self._buffer.to_wav()
        self._start_new_utterance()
        try:
            result = await self._recognizer.transcribe(wav_bytes, audio_seconds)
        except SttError as error:
            self._fail_turn(turn, error)
            return None

        rejection = self._guard.evaluate(result)
        if rejection is not None:
            self._reject_result(turn, rejection, audio_seconds)
            return None

        if not turn.accept_final(result):
            self._logger.info(
                "duplicate final dropped", extra={"turn_id": turn.turn_id}
            )
            return None

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

    def _handle_speech_start(self) -> None:
        output_active = self._is_output_active is not None and self._is_output_active()
        if not output_active:
            self._logger.info("speech start confirmed")
            return

        accepted = self._on_barge_in is not None and self._on_barge_in()
        if accepted:
            self._logger.info("barge-in speech confirmed")
            return
        self._reject_confirmed_speech = True
        self._logger.info("speech during assistant output ignored without an active response")

    def _discard_buffered_input(self) -> None:
        if self._detector is not None:
            self._detector.reset()
        self._converter.reset()
        if not self._buffer.is_closed:
            self._buffer.reset()
        self._turn = None

    def _handle_no_speech(self) -> None:
        # AI가 말하는 시간은 아이의 무응답 시간이 아니다. 출력 중 silence window가
        # 지나도 no-response 사다리를 올리지 않는다.
        if self._is_output_active is not None and self._is_output_active():
            return
        if self._on_no_speech is not None:
            self._on_no_speech()
            return
        self._log_no_speech()

    def _log_no_speech(self) -> None:
        # 신호만 기록한다 — NO_RESPONSE 턴 생성·복구 질문은 W03이 DIARY-1
        # 정책을 재사용해 연결한다(ADR-018, W02 plan §11-E).
        self._logger.info("no speech detected within the timeout window")

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
        """Pre-call rejection: the buffered audio itself is not worth a turn.

        The only rejection path that still owns the buffer (nothing was
        snapshotted yet), so it resets it. No error event — silence is not an
        error (ADR-018).
        """

        turn.abandon()
        self._log_rejection(turn, reason, audio_seconds)
        self._start_new_utterance()
        if self._on_turn_discarded is not None:
            self._on_turn_discarded()

    def _reject_result(
        self,
        turn: SttTurn,
        reason: SpeechRejectionReason,
        audio_seconds: float,
    ) -> None:
        """Post-call rejection (hallucination): the buffer already belongs to
        the next utterance and must not be touched."""

        turn.abandon()
        self._log_rejection(turn, reason, audio_seconds)
        if self._on_turn_discarded is not None:
            self._on_turn_discarded()

    def _log_rejection(
        self,
        turn: SttTurn,
        reason: SpeechRejectionReason,
        audio_seconds: float,
    ) -> None:
        self._logger.info(
            "utterance discarded before becoming a turn",
            extra={
                "turn_id": turn.turn_id,
                "rejection_reason": reason.value,
                "audio_seconds": round(audio_seconds, 2),
            },
        )

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
        if self._on_turn_discarded is not None:
            self._on_turn_discarded()
