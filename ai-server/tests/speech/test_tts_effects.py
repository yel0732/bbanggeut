"""Streaming pitch/formant shaping tests with synthetic PCM only."""

import asyncio
import math
import struct

import pytest

from app.speech.audio_format import TTS_OUTPUT_FORMAT
from app.speech.tts_effects import ChildVoiceEffectSynthesizer


class PcmSynthesizer:
    def __init__(self, pcm: bytes, chunk_bytes: int = 777) -> None:
        self._pcm = pcm
        self._chunk_bytes = chunk_bytes
        self.calls: list[str] = []

    async def synthesize(self, text: str):
        self.calls.append(text)
        for offset in range(0, len(self._pcm), self._chunk_bytes):
            yield self._pcm[offset : offset + self._chunk_bytes]


class GatedPcmSynthesizer:
    def __init__(self, first: bytes, second: bytes) -> None:
        self._first = first
        self._second = second
        self.release = asyncio.Event()

    async def synthesize(self, text: str):
        yield self._first
        await self.release.wait()
        yield self._second


def _sine_pcm(frequency_hz: float, seconds: float) -> bytes:
    sample_rate = TTS_OUTPUT_FORMAT.sample_rate
    sample_count = round(sample_rate * seconds)
    samples = (
        round(12_000 * math.sin(2 * math.pi * frequency_hz * index / sample_rate))
        for index in range(sample_count)
    )
    return b"".join(struct.pack("<h", sample) for sample in samples)


async def _collect(synthesizer, text: str = "안녕") -> bytes:
    output = bytearray()
    async for chunk in synthesizer.synthesize(text):
        output.extend(chunk)
    return bytes(output)


def _estimate_frequency(pcm: bytes) -> float:
    samples = struct.unpack(f"<{len(pcm) // 2}h", pcm)
    positive_crossings = sum(
        1 for left, right in zip(samples, samples[1:]) if left <= 0 < right
    )
    seconds = len(samples) / TTS_OUTPUT_FORMAT.sample_rate
    return positive_crossings / seconds


def _middle_rms(pcm: bytes) -> float:
    samples = struct.unpack(f"<{len(pcm) // 2}h", pcm)
    start = len(samples) // 4
    middle = samples[start : len(samples) - start]
    return math.sqrt(sum(sample * sample for sample in middle) / len(middle))


@pytest.mark.asyncio
async def test_raises_pitch_two_semitones_and_preserves_duration() -> None:
    source = PcmSynthesizer(_sine_pcm(440, 1.0))
    effect = ChildVoiceEffectSynthesizer(source, shift_semitones=2)

    shaped = await _collect(effect)

    expected_hz = 440 * (2 ** (2 / 12))
    assert _estimate_frequency(shaped) == pytest.approx(expected_hz, rel=0.02)
    assert TTS_OUTPUT_FORMAT.duration_seconds(len(shaped)) == pytest.approx(
        1.0,
        abs=0.03,
    )
    assert source.calls == ["안녕"]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("target_frequency_hz", "expected_gain_db"),
    [(320, -1.5), (3500, 0.8)],
)
async def test_shapes_childlike_timbre_at_target_bands(
    target_frequency_hz: float,
    expected_gain_db: float,
) -> None:
    ratio = 2 ** (2 / 12)
    source_frequency_hz = target_frequency_hz / ratio
    pcm = _sine_pcm(source_frequency_hz, 1.0)
    effect = ChildVoiceEffectSynthesizer(
        PcmSynthesizer(pcm),
        shift_semitones=2,
    )

    shaped = await _collect(effect)

    measured_gain_db = 20 * math.log10(_middle_rms(shaped) / _middle_rms(pcm))
    assert measured_gain_db == pytest.approx(expected_gain_db, abs=0.8)


@pytest.mark.asyncio
async def test_zero_shift_passes_provider_chunks_through_unchanged() -> None:
    pcm = _sine_pcm(440, 0.1)
    effect = ChildVoiceEffectSynthesizer(
        PcmSynthesizer(pcm),
        shift_semitones=0,
    )

    assert await _collect(effect) == pcm


@pytest.mark.asyncio
async def test_emits_shaped_audio_before_provider_stream_finishes() -> None:
    pcm = _sine_pcm(440, 1.0)
    source = GatedPcmSynthesizer(pcm[:24_000], pcm[24_000:])
    stream = ChildVoiceEffectSynthesizer(source).synthesize("안녕")

    first_output = await asyncio.wait_for(anext(stream), timeout=0.5)

    assert first_output
    assert not source.release.is_set()
    source.release.set()
    await stream.aclose()


@pytest.mark.parametrize("shift", [-0.1, 4.1, float("nan"), True])
def test_rejects_invalid_shift(shift: object) -> None:
    with pytest.raises(ValueError):
        ChildVoiceEffectSynthesizer(PcmSynthesizer(b""), shift_semitones=shift)
