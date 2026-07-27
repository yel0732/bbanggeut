"""Turn detector state machine tests (T06/T07/T08).

All timing is sample-time: "3 seconds of silence" is literally three seconds of
silent PCM fed through the detector — no ``sleep()``, no clocks, fully
deterministic (W02 plan §3-1). The FakeVoiceActivityDetector judges by content
(non-zero bytes = speech), so scenarios read as plain audio scripts.
"""

import pytest

from app.speech.audio_format import STT_INPUT_FORMAT, PcmAudioChunk
from app.speech.turn_detector import TurnDetectionConfig, TurnDetector, TurnPhase
from app.speech.vad import FakeVoiceActivityDetector


def _speech(ms: int) -> PcmAudioChunk:
    byte_count = STT_INPUT_FORMAT.bytes_per_second * ms // 1000
    return PcmAudioChunk(data=b"\x11\x22" * (byte_count // 2), audio_format=STT_INPUT_FORMAT)


def _silence(ms: int) -> PcmAudioChunk:
    byte_count = STT_INPUT_FORMAT.bytes_per_second * ms // 1000
    return PcmAudioChunk(data=bytes(byte_count), audio_format=STT_INPUT_FORMAT)


class _Events:
    def __init__(self) -> None:
        self.speech_starts = 0
        self.turn_ends = 0
        self.no_speech = 0


def _detector(
    events: _Events,
    **config_overrides,
) -> TurnDetector:
    return TurnDetector(
        FakeVoiceActivityDetector(),
        config=TurnDetectionConfig(**config_overrides) if config_overrides else None,
        on_speech_start=lambda: setattr(events, "speech_starts", events.speech_starts + 1),
        on_turn_end=lambda: setattr(events, "turn_ends", events.turn_ends + 1),
        on_no_speech=lambda: setattr(events, "no_speech", events.no_speech + 1),
    )


def _feed_ms(detector: TurnDetector, chunk: PcmAudioChunk) -> int:
    """Feed one chunk and return the number of returned audio bytes."""

    return sum(len(piece.data) for piece in detector.feed(chunk))


class TestSpeechStart:
    def test_sustained_speech_confirms_a_start_once(self) -> None:
        events = _Events()
        detector = _detector(events)

        returned = _feed_ms(detector, _speech(300))

        assert events.speech_starts == 1
        assert detector.phase is TurnPhase.SPEAKING
        assert returned > 0

    def test_leading_silence_produces_no_buffer_audio(self) -> None:
        """Gating (§11-C): whisper never sees what came before speech."""

        events = _Events()
        detector = _detector(events)

        assert _feed_ms(detector, _silence(2000)) == 0
        assert events.speech_starts == 0
        assert detector.phase is TurnPhase.IDLE

    def test_confirmation_includes_the_preroll_and_candidate_audio(self) -> None:
        """The first syllable must not be clipped: audio from before the
        confirmation instant comes back out of the ring."""

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _silence(1000))

        returned = _feed_ms(detector, _speech(260))

        # 반환량 = 후보 구간(260ms) + pre-roll로 보존된 선행 침묵 일부(최대 300ms)
        candidate_bytes = STT_INPUT_FORMAT.bytes_per_second * 260 // 1000
        assert returned >= candidate_bytes
        preroll_cap = STT_INPUT_FORMAT.bytes_per_second * (300 + 260 + 40) // 1000
        assert returned <= preroll_cap

    def test_a_cough_does_not_start_a_turn(self) -> None:
        """T08: a short burst (~120ms) dies out against min_speech_ms=250."""

        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _speech(120))
        returned = _feed_ms(detector, _silence(1000))

        assert events.speech_starts == 0
        assert returned == 0

    def test_repeated_coughs_never_accumulate_into_a_start(self) -> None:
        events = _Events()
        detector = _detector(events)

        for _ in range(5):
            _feed_ms(detector, _speech(100))
            _feed_ms(detector, _silence(500))

        assert events.speech_starts == 0

    def test_brief_dropouts_do_not_reset_the_candidate(self) -> None:
        """1~2 missed frames (breath, unvoiced consonant) must not restart the
        confirmation from zero (plan §4 T06)."""

        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _speech(140))
        _feed_ms(detector, _silence(40))  # 2 frames — within max_gap_frames
        _feed_ms(detector, _speech(140))

        assert events.speech_starts == 1


class TestTurnEnd:
    def test_trailing_silence_ends_the_turn_exactly_once(self) -> None:
        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(500))

        _feed_ms(detector, _silence(3000))
        _feed_ms(detector, _silence(1000))  # more silence — no second end

        assert events.turn_ends == 1
        assert detector.phase is TurnPhase.IDLE

    def test_a_long_mid_utterance_pause_does_not_end_the_turn(self) -> None:
        """CONV-REQ-072(M): '음… 어…' 후 말을 이어가는 아이를 자르지 않는다."""

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(500))

        _feed_ms(detector, _silence(2900))  # 임계값(3.0초) 직전까지 멈춤
        _feed_ms(detector, _speech(500))    # 다시 말을 이어감
        _feed_ms(detector, _silence(2900))

        assert events.turn_ends == 0
        assert detector.phase is TurnPhase.SPEAKING

    def test_speaking_audio_is_returned_including_trailing_silence(self) -> None:
        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(300))

        returned = _feed_ms(detector, _silence(1000))

        assert returned == STT_INPUT_FORMAT.bytes_per_second  # 1초 전부

    def test_a_new_turn_can_start_after_the_previous_one_ends(self) -> None:
        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _speech(500))
        _feed_ms(detector, _silence(3000))
        returned = _feed_ms(detector, _speech(300))

        assert events.speech_starts == 2
        assert events.turn_ends == 1
        assert returned > 0


class TestNoSpeech:
    def test_fires_after_a_full_timeout_of_silence(self) -> None:
        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _silence(10_000))

        assert events.no_speech == 1

    def test_rearms_for_the_next_full_window(self) -> None:
        """The recovery policy re-prompts and waits again (ADR-018) — one
        signal per full window, not one per frame past the threshold."""

        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _silence(10_000))
        _feed_ms(detector, _silence(9_000))
        assert events.no_speech == 1

        _feed_ms(detector, _silence(1_000))
        assert events.no_speech == 2

    def test_does_not_fire_while_the_child_is_speaking(self) -> None:
        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(300))

        _feed_ms(detector, _speech(11_000))

        assert events.no_speech == 0

    def test_counter_restarts_after_a_turn_ends(self) -> None:
        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(500))
        _feed_ms(detector, _silence(3000))  # turn end — 침묵 카운터 리셋

        _feed_ms(detector, _silence(9_000))
        assert events.no_speech == 0
        _feed_ms(detector, _silence(1_000))
        assert events.no_speech == 1


class TestLifecycle:
    def test_reset_clears_state_and_preroll(self) -> None:
        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(200))  # 후보 구축 중

        detector.reset()
        returned = _feed_ms(detector, _speech(60))

        assert detector.phase is TurnPhase.IDLE
        assert returned == 0  # 이전 후보가 이어지지 않는다

    def test_two_detectors_share_no_state(self) -> None:
        first_events, second_events = _Events(), _Events()
        first = _detector(first_events)
        second = _detector(second_events)

        _feed_ms(first, _speech(300))

        assert first_events.speech_starts == 1
        assert second_events.speech_starts == 0
        assert second.phase is TurnPhase.IDLE

    def test_a_failing_callback_does_not_corrupt_the_state_machine(self) -> None:
        def boom() -> None:
            raise RuntimeError("callback broke")

        detector = TurnDetector(
            FakeVoiceActivityDetector(),
            on_speech_start=boom,
        )

        returned = sum(len(piece.data) for piece in detector.feed(_speech(300)))

        assert detector.phase is TurnPhase.SPEAKING  # 상태 전이는 완료됐다
        assert returned > 0


class TestConfigValidation:
    @pytest.mark.parametrize(
        "overrides",
        [
            {"min_speech_ms": 0},
            {"trailing_silence_seconds": 0},
            {"no_speech_timeout_seconds": 0},
            {"preroll_ms": -1},
            {"max_gap_frames": -1},
        ],
    )
    def test_rejects_invalid_config(self, overrides: dict) -> None:
        with pytest.raises(ValueError):
            TurnDetectionConfig(**overrides)
