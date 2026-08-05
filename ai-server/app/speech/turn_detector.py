"""Sample-time turn detection for one child's audio stream (T06/T07/T08).

Decides when an utterance starts and ends, and gates what reaches the
utterance buffer. `DEC-04` made this pure-silence based: batch STT cannot
provide the sentence-completeness signal the original spec combined with VAD
(text only exists after the turn ends — jira/work-packages/DIARY-3-W02.md
scope adjustment), so the product-configured silence threshold closes turns.

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
ActivityCheck = Callable[[], bool]


@dataclass(frozen=True)
class TurnDetectionConfig:
    """Tunables, all injected from AppSettings (jira/plan/DIARY-3-W02.md §5)."""

    min_speech_ms: int = 250
    # AI 음성이 재생되는 동안에는 노트북 스피커의 잔여 echo를 아이의 끼어들기로
    # 오인하지 않도록 평상시보다 긴 연속 발화를 요구한다. 브라우저 AEC가 1차로
    # 제거하고 이 값이 2차 duration gate 역할을 한다. 이어폰에서는 같은 기준이
    # 적용되어도 아이의 자연스러운 문장 시작을 놓치지 않는 범위다.
    barge_in_min_speech_ms: int = 400
    trailing_silence_seconds: float = 1.0
    no_speech_timeout_seconds: float = 10.0
    preroll_ms: int = 300
    # 발화 중간 1~2프레임 미검출(호흡·무성 자음)이 시작 판정을 리셋하지 않도록
    # 허용하는 연속 non-speech 프레임 수.
    max_gap_frames: int = 2
    # 위 관용의 종료 판정 쪽 대칭. 종료 판정에는 관용이 전혀 없어서 speech 한
    # 프레임(20ms)이 쌓인 침묵을 통째로 리셋했고, 간헐적 잡음(문 닫힘, 의자 끌림)이
    # 턴 종료를 무한정 미룰 수 있었다. 이 프레임 수까지의 연속 speech는 발화 재개로
    # 보지 않는다.
    #
    # 기본값 6은 webrtcvad 실측에서 나왔다. webrtcvad는 약 80ms(4프레임)의 내부
    # hangover를 가져서, 실제 버스트보다 길게 speech를 보고한다.
    #
    #     20ms 클릭  -> speech 5프레임    40ms 버스트 -> 6프레임
    #     100ms      -> 9프레임           200ms       -> 16프레임
    #
    # 따라서 6은 클릭과 40ms급 버스트까지만 흡수한다. 100ms 이상의 소리는 발화
    # 재개로 판정된다 — 아이의 짧은 대답("응")과 길이가 겹쳐 더 올릴 수 없다.
    # **지속적인 배경음(TV 등)은 연속 run이 길어 이 관용으로 막지 못한다** —
    # 그건 입력 단 잡음 억제와 AI 출력 중 별도 Barge-in 확인 창의 몫이다.
    max_speech_blip_frames: int = 6
    # 턴 종료 시 whisper에 남겨 보낼 말꼬리 뒤 여백. 종료 판정용 침묵 전체를
    # 보내면 입력이 발화의 150%를 넘고(실측) 무음 환각 위험도 커진다.
    end_hangover_ms: int = 200

    def __post_init__(self) -> None:
        if self.min_speech_ms <= 0:
            raise ValueError("min_speech_ms must be positive")
        if self.barge_in_min_speech_ms < self.min_speech_ms:
            raise ValueError("barge_in_min_speech_ms must be at least min_speech_ms")
        if self.trailing_silence_seconds <= 0:
            raise ValueError("trailing_silence_seconds must be positive")
        if self.no_speech_timeout_seconds <= 0:
            raise ValueError("no_speech_timeout_seconds must be positive")
        if self.preroll_ms < 0:
            raise ValueError("preroll_ms must not be negative")
        if self.max_gap_frames < 0:
            raise ValueError("max_gap_frames must not be negative")
        if self.max_speech_blip_frames < 0:
            raise ValueError("max_speech_blip_frames must not be negative")
        if self.end_hangover_ms < 0:
            raise ValueError("end_hangover_ms must not be negative")


class TurnPhase(StrEnum):
    IDLE = "idle"
    SPEAKING = "speaking"


class TurnDetector:
    """One connection's start/end-of-utterance state machine.

    ``feed()`` returns the audio that belongs in the utterance buffer: nothing
    while IDLE (leading silence never reaches whisper — plan §11-C), the
    pre-roll plus everything since once speech is confirmed, and every spoken
    frame while SPEAKING.

    Silence while SPEAKING is **withheld rather than emitted**, because at that
    moment it is not yet known whether it is a mid-utterance pause (must be
    kept — CONV-REQ-072) or the end of the turn (must be trimmed). Resumed
    speech restores it in order; a turn end keeps only ``end_hangover_ms``.
    Measured before trimming: whisper received 8.56s for a 5.60s utterance.
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
        is_output_active: ActivityCheck | None = None,
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
        self._is_output_active = is_output_active
        self._logger = logger or logging.getLogger(__name__)

        # The pre-roll ring must hold the configured pre-roll PLUS the whole
        # confirmation window (candidate speech + tolerated gaps) — sizing it
        # to preroll_ms alone would clip the first syllable whenever
        # min_speech_ms > preroll_ms.
        ring_ms = (
            self._config.preroll_ms
            + max(self._config.min_speech_ms, self._config.barge_in_min_speech_ms)
            + self._config.max_gap_frames * self._frame_ms
        )
        self._preroll: deque[bytes] = deque(maxlen=max(1, ring_ms // self._frame_ms))
        self._hangover_bytes = (
            audio_format.bytes_per_second * self._config.end_hangover_ms // 1000
        )

        self._phase = TurnPhase.IDLE
        self._candidate_speech_ms = 0
        self._candidate_gap_run = 0
        self._idle_silence_ms = 0
        self._trailing_silence_ms = 0
        # 종료 판정 중인 오디오는 바로 내보내지 않고 여기 잡아 둔다. 발화가 재개되면
        # 온전히 되살리고(중간 멈춤 손실 없음), 턴이 끝나면 hangover만 남긴다.
        self._pending = bytearray()
        self._speech_blip_run = 0

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
        self._pending.clear()
        self._speech_blip_run = 0

    # --- internals ---------------------------------------------------------

    def _process(self, frame: bytes, out: bytearray) -> None:
        speech = self._vad.is_speech(frame)
        if self._phase is TurnPhase.IDLE:
            self._process_idle(frame, speech, out)
        else:
            self._process_speaking(frame, speech, out)

    def _process_idle(self, frame: bytes, speech: bool, out: bytearray) -> None:
        self._preroll.append(frame)
        output_active = (
            self._is_output_active is not None and self._is_output_active()
        )
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
            if output_active:
                # AI가 말하는 시간은 아이의 무응답 침묵이 아니다(Barge-in 계약).
                # 발화 종료 시점까지 매 프레임 0으로 되돌려야 출력이 끝난 뒤에
                # 온전한 timeout window가 새로 시작된다 — 여기서 누적을 허용하면
                # AI가 길게 말한 직후 아이가 몇 초 만에 무응답으로 몰린다.
                self._idle_silence_ms = 0
            else:
                self._idle_silence_ms += self._frame_ms
                timeout_ms = self._config.no_speech_timeout_seconds * 1000
                if self._idle_silence_ms >= timeout_ms:
                    # Fires once per full window, then re-arms: the recovery
                    # policy (W03, ADR-018) re-prompts and waits again.
                    self._idle_silence_ms = 0
                    self._notify(self._on_no_speech, "no_speech")
        start_threshold_ms = (
            self._config.barge_in_min_speech_ms
            if output_active
            else self._config.min_speech_ms
        )
        if self._candidate_speech_ms >= start_threshold_ms:
            self._confirm_speech(out, start_threshold_ms)

    def _confirm_speech(self, out: bytearray, start_threshold_ms: int) -> None:
        self._phase = TurnPhase.SPEAKING
        # The ring is sized for the larger Barge-in window, but a normal turn
        # must not receive that extra leading silence. Emit only the window
        # used by the mode that actually confirmed this start.
        emit_ms = (
            self._config.preroll_ms
            + start_threshold_ms
            + self._config.max_gap_frames * self._frame_ms
        )
        emit_frames = max(1, emit_ms // self._frame_ms)
        for buffered in list(self._preroll)[-emit_frames:]:
            out += buffered
        self._preroll.clear()
        self._candidate_speech_ms = 0
        self._candidate_gap_run = 0
        self._idle_silence_ms = 0
        self._trailing_silence_ms = 0
        self._pending.clear()
        # 방금 min_speech_ms만큼의 연속 speech를 확인하고 들어왔다 — 이미 관용치를
        # 넘긴 상태로 시작해야 첫 발화 프레임들이 보류되지 않는다.
        self._speech_blip_run = self._config.max_speech_blip_frames + 1
        self._notify(self._on_speech_start, "speech_start")

    def _process_speaking(self, frame: bytes, speech: bool, out: bytearray) -> None:
        if speech:
            self._speech_blip_run += 1
            if self._speech_blip_run <= self._config.max_speech_blip_frames:
                # 발화 재개인지 고립된 잡음인지 아직 모른다 — 판단이 설 때까지
                # 붙잡아 둔다. 재개로 판명되면 아래에서 온전히 되살아난다.
                self._pending += frame
                return
            # A long mid-utterance pause followed by more speech must not end
            # the turn (CONV-REQ-072) — the silence run restarts every time.
            out += self._pending  # 멈춤 구간을 순서 그대로 복원한다
            self._pending.clear()
            self._trailing_silence_ms = 0
            # blip_run은 여기서 리셋하지 않는다 — "연속 speech 프레임 수"라는 의미를
            # 유지해야 이어지는 발화가 매 프레임 다시 보류되지 않는다.
            out += frame
            return
        self._speech_blip_run = 0
        self._pending += frame
        self._trailing_silence_ms += self._frame_ms
        if self._trailing_silence_ms >= self._config.trailing_silence_seconds * 1000:
            # 말꼬리 직후 여백만 남기고 종료 판정용 침묵은 버린다.
            out += self._pending[: self._hangover_bytes]
            self._pending.clear()
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
