"""Unit tests for one utterance's PCM accumulation (T02).

Synthetic PCM only — no real child speech as a fixture (§14).
"""

import io
import wave

import pytest

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk
from app.speech.utterance_buffer import UtteranceBuffer


def _chunk(seconds: float, *, fill: int = 0) -> PcmAudioChunk:
    byte_count = int(seconds * STT_INPUT_FORMAT.bytes_per_second)
    return PcmAudioChunk(
        data=bytes([fill]) * byte_count,
        audio_format=STT_INPUT_FORMAT,
    )


class TestAccumulation:
    def test_tracks_duration_across_appends(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)

        buffer.append(_chunk(0.5))
        buffer.append(_chunk(0.25))

        assert buffer.duration_seconds == pytest.approx(0.75)
        assert not buffer.is_empty

    def test_empty_chunks_do_not_change_the_buffer(self) -> None:
        """The converter emits empty chunks while its resampler fills up (T01)."""

        buffer = UtteranceBuffer(max_seconds=30.0)

        assert buffer.append(_chunk(0.0)) is True
        assert buffer.is_empty

    def test_rejects_a_chunk_in_a_different_format(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        foreign = PcmAudioChunk(
            data=b"\x00" * 100,
            audio_format=AudioFormat(sample_rate=48000, channels=2, sample_width=2),
        )

        with pytest.raises(ValueError):
            buffer.append(foreign)

    def test_rejects_a_non_positive_cap(self) -> None:
        with pytest.raises(ValueError):
            UtteranceBuffer(max_seconds=0)


class TestOverflow:
    def test_append_reports_false_once_the_cap_is_reached(self) -> None:
        buffer = UtteranceBuffer(max_seconds=1.0)

        assert buffer.append(_chunk(0.6)) is True
        assert buffer.append(_chunk(0.5)) is False
        assert buffer.is_full

    def test_overflowing_audio_is_kept_not_dropped(self) -> None:
        """§3-3: a batch input must not be spliced — the caller finalises instead."""

        buffer = UtteranceBuffer(max_seconds=1.0)

        buffer.append(_chunk(0.6, fill=1))
        buffer.append(_chunk(0.5, fill=2))

        assert buffer.duration_seconds == pytest.approx(1.1)
        assert buffer.byte_count == int(1.1 * STT_INPUT_FORMAT.bytes_per_second)


class TestWavEncoding:
    def test_to_wav_round_trips_through_the_stdlib_reader(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.append(_chunk(0.5, fill=7))

        with wave.open(io.BytesIO(buffer.to_wav()), "rb") as reader:
            assert reader.getframerate() == STT_INPUT_FORMAT.sample_rate
            assert reader.getnchannels() == STT_INPUT_FORMAT.channels
            assert reader.getsampwidth() == STT_INPUT_FORMAT.sample_width
            assert reader.getnframes() == int(0.5 * STT_INPUT_FORMAT.sample_rate)

    def test_wav_payload_matches_the_appended_pcm(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.append(_chunk(0.1, fill=3))
        buffer.append(_chunk(0.1, fill=9))

        with wave.open(io.BytesIO(buffer.to_wav()), "rb") as reader:
            payload = reader.readframes(reader.getnframes())

        assert payload == _chunk(0.1, fill=3).data + _chunk(0.1, fill=9).data

    def test_declared_length_is_usable_unlike_the_tts_wav_header(self) -> None:
        """gpt-4o-mini-tts's wav header carries a junk length (plan §1) — ours must not."""

        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.append(_chunk(2.0))

        with wave.open(io.BytesIO(buffer.to_wav()), "rb") as reader:
            declared = reader.getnframes() / reader.getframerate()

        assert declared == pytest.approx(2.0)

    def test_empty_buffer_still_produces_a_valid_wav(self) -> None:
        with wave.open(io.BytesIO(UtteranceBuffer(max_seconds=30.0).to_wav()), "rb") as reader:
            assert reader.getnframes() == 0


class TestLifecycle:
    def test_reset_clears_audio_and_allows_reuse(self) -> None:
        buffer = UtteranceBuffer(max_seconds=1.0)
        buffer.append(_chunk(0.9))

        buffer.reset()

        assert buffer.is_empty
        assert not buffer.is_full
        assert buffer.append(_chunk(0.1)) is True

    def test_reset_is_idempotent(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.reset()
        buffer.reset()

        assert buffer.is_empty

    def test_close_drops_audio_and_is_idempotent(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.append(_chunk(0.5))

        buffer.close()
        buffer.close()

        assert buffer.is_empty
        assert buffer.is_closed

    def test_appending_after_close_raises(self) -> None:
        buffer = UtteranceBuffer(max_seconds=30.0)
        buffer.close()

        with pytest.raises(RuntimeError):
            buffer.append(_chunk(0.1))


def test_two_buffers_do_not_share_audio() -> None:
    """Session isolation (§8) — mirrors the same guarantee tested for T01."""

    first = UtteranceBuffer(max_seconds=30.0)
    second = UtteranceBuffer(max_seconds=30.0)

    first.append(_chunk(0.5))

    assert second.is_empty
    assert not first.is_empty
