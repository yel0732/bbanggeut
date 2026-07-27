"""Unit tests for the WebRTC Frame -> STT input PCM conversion (T01).

Audio here is synthetic (digital silence and a generated sine) — no real child
speech is ever used as a fixture (``docs/AI_CONVENTIONS.md`` §14).
"""

import math
import struct
from fractions import Fraction

import pytest
from av import AudioFrame

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk
from app.speech.frame_converter import AudioConversionError, AudioFrameConverter

_WEBRTC_RATE = 48000
_FRAME_SAMPLES = 960  # 20ms at 48kHz — what aiortc delivers per Opus frame


def _make_frame(
    *,
    samples: int = _FRAME_SAMPLES,
    rate: int = _WEBRTC_RATE,
    layout: str = "stereo",
    tone_hz: int | None = None,
) -> AudioFrame:
    """Build an inbound Frame shaped like aiortc's Opus decoder output."""

    frame = AudioFrame(format="s16", layout=layout, samples=samples)
    frame.sample_rate = rate
    frame.time_base = Fraction(1, rate)
    channels = len(frame.layout.channels)
    if tone_hz is None:
        payload = bytes(samples * 2 * channels)
    else:
        payload = b"".join(
            struct.pack("<h", int(10000 * math.sin(2 * math.pi * tone_hz * i / rate)))
            for i in range(samples)
            for _ in range(channels)
        )
    for plane in frame.planes:
        plane.update(payload + bytes(plane.buffer_size - len(payload)))
    return frame


def _sample_count(chunk: PcmAudioChunk) -> int:
    return len(chunk.data) // STT_INPUT_FORMAT.bytes_per_sample


class TestAudioFormat:
    def test_derived_sizes(self) -> None:
        assert STT_INPUT_FORMAT.bytes_per_second == 32000
        assert STT_INPUT_FORMAT.bytes_per_sample == 2
        assert STT_INPUT_FORMAT.duration_seconds(32000) == 1.0

    @pytest.mark.parametrize(
        "kwargs",
        [
            {"sample_rate": 0, "channels": 1, "sample_width": 2},
            {"sample_rate": 16000, "channels": 0, "sample_width": 2},
            {"sample_rate": 16000, "channels": 1, "sample_width": -2},
        ],
    )
    def test_rejects_non_positive_dimensions(self, kwargs: dict[str, int]) -> None:
        with pytest.raises(ValueError):
            AudioFormat(**kwargs)

    def test_chunk_repr_does_not_leak_audio_bytes(self) -> None:
        chunk = PcmAudioChunk(data=b"\x11\x22\x33\x44", audio_format=STT_INPUT_FORMAT)

        assert "\\x11" not in repr(chunk)
        assert chunk.duration_seconds == pytest.approx(4 / 32000)


class TestAudioFrameConverter:
    def test_converted_chunks_carry_the_stt_input_format(self) -> None:
        converter = AudioFrameConverter()

        chunk = converter.convert(_make_frame())

        assert chunk.audio_format == STT_INPUT_FORMAT
        assert converter.target_format == STT_INPUT_FORMAT

    def test_resampling_preserves_every_sample_without_padding(self) -> None:
        """48kHz stereo -> 16kHz mono must yield exactly one third of the samples.

        This is the padding guard: av reports ``buffer_size`` larger than the
        real audio (704 bytes for a 304-sample Frame), so if the converter
        forwarded whole planes the byte count would come out inflated rather
        than landing exactly on the expected total.
        """

        converter = AudioFrameConverter()
        frame_count = 3

        produced = sum(
            _sample_count(converter.convert(_make_frame())) for _ in range(frame_count)
        )
        produced += _sample_count(converter.flush())

        assert produced == frame_count * _FRAME_SAMPLES * 16000 // _WEBRTC_RATE == 960

    def test_a_single_frame_may_produce_a_short_chunk(self) -> None:
        """The resampler's internal delay makes early output smaller than 1/3."""

        converter = AudioFrameConverter()

        first = converter.convert(_make_frame())

        assert _sample_count(first) < _FRAME_SAMPLES // 3
        assert not first.is_empty

    def test_audible_input_survives_the_padding_slice(self) -> None:
        """Guards the opposite failure of the padding slice: cutting real audio."""

        converter = AudioFrameConverter()

        chunk = converter.convert(_make_frame(tone_hz=440))

        assert any(chunk.data)

    def test_silent_input_converts_to_silent_pcm(self) -> None:
        converter = AudioFrameConverter()

        chunk = converter.convert(_make_frame())

        assert not any(chunk.data)

    def test_converter_is_reusable_after_flush(self) -> None:
        """A flushed av resampler is at EOF, so flush() must replace it."""

        converter = AudioFrameConverter()
        converter.convert(_make_frame())
        converter.flush()

        chunk = converter.convert(_make_frame())  # would raise EOFError if reused

        assert not chunk.is_empty

    def test_reset_discards_buffered_audio_instead_of_returning_it(self) -> None:
        converter = AudioFrameConverter()
        converter.convert(_make_frame())  # leaves a tail inside the resampler

        converter.reset()

        assert converter.flush().is_empty

    def test_flush_on_a_fresh_converter_returns_nothing(self) -> None:
        assert AudioFrameConverter().flush().is_empty

    def test_input_format_change_mid_stream_is_reported_as_conversion_error(self) -> None:
        converter = AudioFrameConverter()
        converter.convert(_make_frame())

        with pytest.raises(AudioConversionError):
            converter.convert(_make_frame(rate=24000))

    def test_input_already_in_target_format_passes_through(self) -> None:
        converter = AudioFrameConverter()

        chunk = converter.convert(_make_frame(samples=320, rate=16000, layout="mono"))

        assert _sample_count(chunk) == 320
        assert chunk.audio_format == STT_INPUT_FORMAT

    def test_two_converters_do_not_share_resampler_state(self) -> None:
        """Session isolation: one stream's tail must not leak into another."""

        busy = AudioFrameConverter()
        busy.convert(_make_frame())

        fresh = AudioFrameConverter()

        assert fresh.flush().is_empty
        assert not busy.flush().is_empty

    def test_rejects_a_target_format_it_cannot_express(self) -> None:
        with pytest.raises(ValueError):
            AudioFrameConverter(AudioFormat(sample_rate=16000, channels=3, sample_width=2))
