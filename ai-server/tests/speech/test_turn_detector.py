"""Turn detector state machine tests (T06/T07/T08).

All timing is sample-time: "1 second of silence" is literally one second of
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
    is_output_active=None,
    **config_overrides,
) -> TurnDetector:
    return TurnDetector(
        FakeVoiceActivityDetector(),
        config=TurnDetectionConfig(**config_overrides) if config_overrides else None,
        on_speech_start=lambda: setattr(events, "speech_starts", events.speech_starts + 1),
        on_turn_end=lambda: setattr(events, "turn_ends", events.turn_ends + 1),
        on_no_speech=lambda: setattr(events, "no_speech", events.no_speech + 1),
        is_output_active=is_output_active,
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

        _feed_ms(detector, _silence(1000))
        _feed_ms(detector, _silence(1000))  # more silence — no second end

        assert events.turn_ends == 1
        assert detector.phase is TurnPhase.IDLE

    def test_a_long_mid_utterance_pause_does_not_end_the_turn(self) -> None:
        """CONV-REQ-072(M): '음… 어…' 후 말을 이어가는 아이를 자르지 않는다."""

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(500))

        _feed_ms(detector, _silence(900))  # 임계값(1.0초) 직전까지 멈춤
        _feed_ms(detector, _speech(500))    # 다시 말을 이어감
        _feed_ms(detector, _silence(900))

        assert events.turn_ends == 0
        assert detector.phase is TurnPhase.SPEAKING

    def test_a_resumed_pause_is_restored_in_full(self) -> None:
        """트림은 종료된 턴에만 적용된다 — 중간 멈춤은 한 바이트도 잃지 않는다.

        보류는 '언제 나가는가'만 바꾸고 '무엇이 나가는가'는 바꾸지 않는다. 그래서
        프레임 경계와 무관한 총량 보존으로 확인한다(선행 침묵이 없는 시나리오).
        """

        events = _Events()
        detector = _detector(events)

        emitted = _feed_ms(detector, _speech(300))
        withheld = _feed_ms(detector, _silence(800))   # 판단 보류: 아직 안 나감
        emitted += withheld
        emitted += _feed_ms(detector, _speech(500))     # 재개 → 멈춤 구간 복원

        assert withheld == 0
        assert emitted == STT_INPUT_FORMAT.bytes_per_second * 1600 // 1000
        assert events.turn_ends == 0

    def test_trailing_silence_is_trimmed_to_the_hangover(self) -> None:
        """종료 판정용 침묵 3초를 whisper에 보내지 않는다(실측 입력 153% 문제)."""

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(300))

        returned = _feed_ms(detector, _silence(1000))

        assert events.turn_ends == 1
        assert returned == STT_INPUT_FORMAT.bytes_per_second * 200 // 1000

    def test_isolated_noise_blips_do_not_postpone_the_turn_end(self) -> None:
        """간헐적 잡음(문 닫힘·의자 끌림)이 종료를 무한정 미루지 못한다.

        수정 전에는 speech 한 프레임(20ms)이 쌓인 침묵을 통째로 리셋했다.

        주의: FakeVoiceActivityDetector에는 webrtcvad의 내부 hangover(약 80ms)가
        없다. 실제로는 같은 길이의 버스트가 4프레임쯤 더 길게 speech로 보고되므로,
        여기 120ms는 실환경의 40ms급 버스트에 해당한다.
        """

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(300))

        for _ in range(20):  # blip을 끼운 침묵을 3초 넘게 흘린다
            _feed_ms(detector, _silence(160))
            _feed_ms(detector, _speech(120))  # 6프레임 = max_speech_blip_frames

        assert events.turn_ends == 1

    def test_sustained_background_speech_still_blocks_the_end(self) -> None:
        """한계를 고정한다: 지속적인 배경 음성은 이 관용으로 막지 못한다.

        연속 run이 허용치를 넘으면 발화 재개로 판정하는 것이 맞다 — 아이의 짧은
        대답과 길이가 겹쳐 관용치를 더 올릴 수 없다. TV 같은 지속 배경음은 입력 단
        잡음 억제와 AI 발화 구간 게이팅의 몫이다(ADR-020).
        """

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _speech(300))

        for _ in range(20):
            _feed_ms(detector, _silence(100))
            _feed_ms(detector, _speech(200))  # 10프레임 연속 = 재개로 판정

        assert events.turn_ends == 0
        assert detector.phase is TurnPhase.SPEAKING

    def test_a_new_turn_can_start_after_the_previous_one_ends(self) -> None:
        events = _Events()
        detector = _detector(events)

        _feed_ms(detector, _speech(500))
        _feed_ms(detector, _silence(1000))
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
        _feed_ms(detector, _silence(1000))  # turn end — 침묵 카운터 리셋

        _feed_ms(detector, _silence(9_000))
        assert events.no_speech == 0
        _feed_ms(detector, _silence(1_000))
        assert events.no_speech == 1


class TestBargeInWindow:
    """AI 출력 중에는 더 긴 확인 창(barge_in_min_speech_ms=400)이 적용된다."""

    def test_normal_threshold_is_not_enough_during_output(self) -> None:
        events = _Events()
        detector = _detector(events, is_output_active=lambda: True)

        _feed_ms(detector, _speech(300))  # 평상시(250ms)라면 이미 확정됐다

        assert events.speech_starts == 0
        assert detector.phase is TurnPhase.IDLE

    def test_sustained_speech_confirms_a_barge_in(self) -> None:
        events = _Events()
        detector = _detector(events, is_output_active=lambda: True)

        returned = _feed_ms(detector, _speech(420))

        assert events.speech_starts == 1
        assert detector.phase is TurnPhase.SPEAKING
        # 확정 발화는 pre-roll을 포함해 버리지 않는다(계약).
        assert returned >= STT_INPUT_FORMAT.bytes_per_second * 400 // 1000

    def test_threshold_returns_to_normal_when_output_stops(self) -> None:
        active = {"on": True}
        events = _Events()
        detector = _detector(events, is_output_active=lambda: active["on"])

        _feed_ms(detector, _speech(300))
        assert events.speech_starts == 0
        _feed_ms(detector, _silence(200))  # 후보가 죽고 다시 IDLE 침묵

        active["on"] = False
        _feed_ms(detector, _speech(260))

        assert events.speech_starts == 1

    def test_normal_confirmation_is_trimmed_to_the_normal_window(self) -> None:
        """링은 barge-in 창(400ms) 크기지만, 평상시 확정 턴에 그 여분의 선행
        침묵까지 내보내면 안 된다 — whisper 입력이 침묵으로 부풀고 무음 환각
        위험이 커진다."""

        events = _Events()
        detector = _detector(events)
        _feed_ms(detector, _silence(2000))

        returned = _feed_ms(detector, _speech(260))

        emit_cap = STT_INPUT_FORMAT.bytes_per_second * (300 + 250 + 40) // 1000
        assert returned <= emit_cap


class TestNoSpeechDuringOutput:
    """AI 출력 중 침묵은 아이의 무응답 시간이 아니다(Barge-in 계약)."""

    def test_never_fires_while_output_is_active(self) -> None:
        events = _Events()
        detector = _detector(events, is_output_active=lambda: True)

        _feed_ms(detector, _silence(25_000))

        assert events.no_speech == 0

    def test_window_restarts_in_full_after_output_ends(self) -> None:
        """회귀 방지: 출력 중 누적된 침묵이 남으면 AI가 길게 말한 직후 아이가
        몇 초 만에 무응답으로 몰린다. window는 출력 종료 후 처음부터 센다."""

        active = {"on": True}
        events = _Events()
        detector = _detector(events, is_output_active=lambda: active["on"])

        _feed_ms(detector, _silence(9_000))  # AI 발화 중 침묵 — 세지 않는다
        active["on"] = False

        _feed_ms(detector, _silence(9_000))
        assert events.no_speech == 0  # 아직 온전한 10초가 지나지 않았다
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
