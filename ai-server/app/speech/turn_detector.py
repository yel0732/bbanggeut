"""Sample-time turn detection for one child's audio stream (T06/T07/T08).

Decides when an utterance starts and ends, and gates what reaches the
utterance buffer. `DEC-04` made this pure-silence based: batch STT cannot
provide the sentence-completeness signal the original spec combined with VAD
(text only exists after the turn ends — jira/work-packages/DIARY-3-W02.md
scope adjustment), so the end-of-turn threshold is deliberately generous.

Time here is **received samples, not the wall clock**. A live WebRTC
microphone keeps sending silent frames while the child is quiet, so silence
duration is simply silent audio received — 16,000 samples is exactly one
second. That makes every test a matter of feeding N seconds of synthetic PCM,
with no ``sleep()`` and no injected clock (plan §3-1; the Work Package's
"monotonic Clock" requirement is about determinism, which this satisfies more
strongly). Frames stopping entirely is a *connection* problem and belongs to
the Lease/track-ended handling from DIARY-2, not to this state machine.

What the layers filter (CONV-REQ-074 asks for a reduced misfire *rate*, not
perfection):

- coughs/clicks (short bursts)  -> ``min_speech_ms`` duration gate here
- ambient low-energy noise      -> webrtcvad aggressiveness (vad.py)
- sustained laughter            -> passes here; W01's hallucination guard is
                                   the second net
- "음", "어", one-word answers  -> accepted on purpose — they are answers
"""

from __future__ import annotations

import logging
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass
from enum import StrEnum

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk
from app.speech.vad import VadFramer, VoiceActivityDetector

TurnEventCallback = Callable[[], None]


@dataclass(frozen=True)
class TurnDetectionConfig:
    """Tunables, all injected from AppSettings (jira/plan/DIARY-3-W02.md §5)."""

    min_speech_ms: int = 250
    trailing_silence_seconds: float = 3.0
    no_speech_timeout_seconds: float = 10.0
    preroll_ms: int = 300
    # 발화 중간 1~2프레임 미검출(호흡·무성 자음)이 시작 판정을 리셋하지 않도록
    # 허용하는 연속 non-speech 프레임 수.
    max_gap_frames: int = 2

    def __post_init__(self) -> None:
        if self.min_speech_ms <= 0:
            raise ValueError("min_speech_ms must be positive")
        if self.trailing_silence_seconds <= 0:
            raise ValueError("trailing_silence_seconds must be positive")
        if self.no_speech_timeout_seconds <= 0:
            raise ValueError("no_speech_timeout_seconds must be positive")
        if self.preroll_ms < 0:
            raise ValueError("preroll_ms must not be negative")
        if self.max_gap_frames < 0:
            raise ValueError("max_gap_frames must not be negative")


class TurnPhase(StrEnum):
    IDLE = "idle"
    SPEAKING = "speaking"


class TurnDetector:
    """One connection's start/end-of-utterance state machine.

    ``feed()`` returns the audio that belongs in the utterance buffer: nothing
    while IDLE (leading silence never reaches whisper — plan §11-C), the
    pre-roll plus everything since once speech is confirmed, and every frame
    while SPEAKING including trailing silence (whisper is indifferent to a
    quiet tail, and trimming it is an optimisation for later).
    """

    def __init__(
        self,
        vad: VoiceActivityDetector,
        *,
        config: TurnDetectionConfig | None = None,
        audio_format: AudioFormat = STT_INPUT_FORMAT,
        on_speech_start: TurnEventCallback | None = None,
        on_turn_end: TurnEventCallback | None = None,
        on_no_speech: TurnEventCallback | None = None,
        logger: logging.Logger | None = None,
    ) -> None:
        self._vad = vad
        self._config = config or TurnDetectionConfig()
        self._audio_format = audio_format
        self._framer = VadFramer(vad.frame_milliseconds, audio_format)
        self._frame_ms = vad.frame_milliseconds
        self._on_speech_start = on_speech_start
        self._on_turn_end = on_turn_end
        self._on_no_speech = on_no_speech
        self._logger = logger or logging.getLogger(__name__)

        # The pre-roll ring must hold the configured pre-roll PLUS the whole
        # confirmation window (candidate speech + tolerated gaps) — sizing it
        # to preroll_ms alone would clip the first syllable whenever
        # min_speech_ms > preroll_ms.
        ring_ms = (
            self._config.preroll_ms
            + self._config.min_speech_ms
            + self._config.max_gap_frames * self._frame_ms
        )
        self._preroll: deque[bytes] = deque(maxlen=max(1, ring_ms // self._frame_ms))

        self._phase = TurnPhase.IDLE
        self._candidate_speech_ms = 0
        self._candidate_gap_run = 0
        self._idle_silence_ms = 0
        self._trailing_silence_ms = 0

    @property
    def phase(self) -> TurnPhase:
        return self._phase

    def feed(self, chunk: PcmAudioChunk) -> list[PcmAudioChunk]:
        """Advance the state machine; return audio destined for the buffer."""

        out = bytearray()
        for frame in self._framer.push(chunk):
            self._process(frame, out)
        if not out:
            return []
        return [PcmAudioChunk(data=bytes(out), audio_format=self._audio_format)]

    def reset(self) -> None:
        """Drop all state (and any pre-roll audio). Idempotent."""

        self._framer.reset()
        self._preroll.clear()
        self._phase = TurnPhase.IDLE
        self._candidate_speech_ms = 0
        self._candidate_gap_run = 0
        self._idle_silence_ms = 0
        self._trailing_silence_ms = 0

    # --- internals ---------------------------------------------------------

    def _process(self, frame: bytes, out: bytearray) -> None:
        speech = self._vad.is_speech(frame)
        if self._phase is TurnPhase.IDLE:
            self._process_idle(frame, speech, out)
        else:
            self._process_speaking(frame, speech, out)

    def _process_idle(self, frame: bytes, speech: bool, out: bytearray) -> None:
        self._preroll.append(frame)
        if speech:
            self._candidate_speech_ms += self._frame_ms
            self._candidate_gap_run = 0
        else:
            if self._candidate_speech_ms > 0:
                self._candidate_gap_run += 1
                if self._candidate_gap_run > self._config.max_gap_frames:
                    # A short burst that died out — a cough, a click. Never
                    # confirmed, so nothing of it reaches the buffer.
                    self._candidate_speech_ms = 0
                    self._candidate_gap_run = 0
            self._idle_silence_ms += self._frame_ms
            timeout_ms = self._config.no_speech_timeout_seconds * 1000
            if self._idle_silence_ms >= timeout_ms:
                # Fires once per full window, then re-arms: the recovery
                # policy (W03, ADR-018) re-prompts and waits again.
                self._idle_silence_ms = 0
                self._notify(self._on_no_speech, "no_speech")
        if self._candidate_speech_ms >= self._config.min_speech_ms:
            self._confirm_speech(out)

    def _confirm_speech(self, out: bytearray) -> None:
        self._phase = TurnPhase.SPEAKING
        for buffered in self._preroll:  # pre-roll first: the first syllable
            out += buffered
        self._preroll.clear()
        self._candidate_speech_ms = 0
        self._candidate_gap_run = 0
        self._idle_silence_ms = 0
        self._trailing_silence_ms = 0
        self._notify(self._on_speech_start, "speech_start")

    def _process_speaking(self, frame: bytes, speech: bool, out: bytearray) -> None:
        out += frame
        if speech:
            # A long mid-utterance pause followed by more speech must not end
            # the turn (CONV-REQ-072) — the silence run restarts every time.
            self._trailing_silence_ms = 0
            return
        self._trailing_silence_ms += self._frame_ms
        if self._trailing_silence_ms >= self._config.trailing_silence_seconds * 1000:
            self._phase = TurnPhase.IDLE
            self._trailing_silence_ms = 0
            self._idle_silence_ms = 0
            self._notify(self._on_turn_end, "turn_end")

    def _notify(self, callback: TurnEventCallback | None, event: str) -> None:
        if callback is None:
            return
        try:
            callback()
        except Exception:  # noqa: BLE001 - a broken callback must not corrupt the state machine
            self._logger.exception("turn detector callback failed", extra={"event": event})
