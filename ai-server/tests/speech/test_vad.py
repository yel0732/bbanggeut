"""VAD boundary and reframing tests (T06).

Uses the real webrtcvad (a local C extension — no network, so §10's "no real
Provider in unit tests" does not apply). Audio is synthetic silence/sine only.
"""

import math
import struct

import pytest

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk
from app.speech.vad import (
    VAD_FRAME_MILLISECONDS,
    FakeVoiceActivityDetector,
    VadFramer,
    WebrtcVoiceActivityDetector,
)

_FRAME_BYTES = STT_INPUT_FORMAT.bytes_per_second * VAD_FRAME_MILLISECONDS // 1000  # 640


def _silence(byte_count: int = _FRAME_BYTES) -> bytes:
    return bytes(byte_count)


def _tone(byte_count: int = _FRAME_BYTES, hz: int = 440, amp: int = 6000) -> bytes:
    samples = byte_count // 2
    return b"".join(
        struct.pack("<h", int(amp * math.sin(2 * math.pi * hz * i / 16000)))
        for i in range(samples)
    )


class TestWebrtcVoiceActivityDetector:
    def test_silence_is_not_speech(self) -> None:
        assert WebrtcVoiceActivityDetector().is_speech(_silence()) is False

    def test_voice_band_energy_is_speech(self) -> None:
        """Reproduces the planning probe: even a pure tone counts as speech —
        which is exactly why duration gating lives in the TurnDetector."""

        assert WebrtcVoiceActivityDetector().is_speech(_tone()) is True

    def test_rejects_a_frame_of_the_wrong_length(self) -> None:
        with pytest.raises(ValueError):
            WebrtcVoiceActivityDetector().is_speech(_silence(_FRAME_BYTES - 2))

    @pytest.mark.parametrize("aggressiveness", [-1, 4])
    def test_rejects_out_of_range_aggressiveness(self, aggressiveness: int) -> None:
        with pytest.raises(ValueError):
            WebrtcVoiceActivityDetector(aggressiveness)

    def test_rejects_an_unsupported_audio_format(self) -> None:
        with pytest.raises(ValueError):
            WebrtcVoiceActivityDetector(
                audio_format=AudioFormat(sample_rate=44100, channels=1, sample_width=2)
            )
        with pytest.raises(ValueError):
            WebrtcVoiceActivityDetector(
                audio_format=AudioFormat(sample_rate=16000, channels=2, sample_width=2)
            )


class TestVadFramer:
    def _chunk(self, byte_count: int, fill: int = 0) -> PcmAudioChunk:
        return PcmAudioChunk(data=bytes([fill]) * byte_count, audio_format=STT_INPUT_FORMAT)

    def test_recuts_non_uniform_chunks_into_exact_frames(self) -> None:
        """T01's converter emits 304/320/... sample chunks; webrtcvad needs
        exactly 640 bytes (measured: 25ms frames raise)."""

        framer = VadFramer()

        frames = framer.push(self._chunk(608))  # 304 samples — under one frame
        assert frames == []

        frames = framer.push(self._chunk(640))  # remainder 608 + 640 = 1248
        assert [len(frame) for frame in frames] == [_FRAME_BYTES]

        frames = framer.push(self._chunk(672))  # 608 remainder + 672 = 1280
        assert [len(frame) for frame in frames] == [_FRAME_BYTES, _FRAME_BYTES]

    def test_remainder_bytes_are_carried_not_lost(self) -> None:
        framer = VadFramer()
        framer.push(self._chunk(100, fill=7))

        frames = framer.push(self._chunk(_FRAME_BYTES, fill=9))

        assert frames[0][:100] == bytes([7]) * 100  # earlier bytes come first

    def test_empty_chunk_produces_no_frames(self) -> None:
        assert VadFramer().push(self._chunk(0)) == []

    def test_reset_drops_the_remainder(self) -> None:
        framer = VadFramer()
        framer.push(self._chunk(600, fill=7))

        framer.reset()
        frames = framer.push(self._chunk(_FRAME_BYTES, fill=9))

        assert frames == [bytes([9]) * _FRAME_BYTES]

    def test_rejects_a_mismatched_chunk_format(self) -> None:
        foreign = PcmAudioChunk(
            data=bytes(100),
            audio_format=AudioFormat(sample_rate=48000, channels=2, sample_width=2),
        )
        with pytest.raises(ValueError):
            VadFramer().push(foreign)


class TestFakeVoiceActivityDetector:
    def test_judges_by_content(self) -> None:
        fake = FakeVoiceActivityDetector()

        assert fake.is_speech(bytes(640)) is False
        assert fake.is_speech(b"\x01" + bytes(639)) is True
        assert fake.frames_judged == 2
