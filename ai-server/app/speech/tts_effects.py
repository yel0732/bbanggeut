"""Streaming child-character pitch and timbre shaping for raw TTS PCM."""

from __future__ import annotations

import math
from collections.abc import AsyncIterator
from fractions import Fraction

import av

from app.speech.audio_format import TTS_OUTPUT_FORMAT
from app.speech.tts_client import SpeechSynthesizer, TtsError, TtsFailureCode

_FRAME_MILLISECONDS = 20
_MAX_SHIFT_SEMITONES = 4.0
_HIGH_PASS_HZ = 80
_LOW_MID_FREQUENCY_HZ = 320
_LOW_MID_CUT_DB = -1.5
_PRESENCE_FREQUENCY_HZ = 3500
_PRESENCE_BOOST_DB = 0.8


class ChildVoiceEffectSynthesizer(SpeechSynthesizer):
    """Raise pitch/formants and lighten timbre while preserving duration.

    Reinterpreting the PCM sample rate raises the entire spectrum, including
    the vocal formants. Resampling returns to the 24 kHz transport format and
    ``atempo`` restores the original duration. A gentle EQ then removes rumble,
    reduces adult-like lower-mid fullness, and adds consonant presence. The
    graph works on 20 ms frames, so Provider streaming and cancellation remain
    intact.
    """

    def __init__(
        self,
        inner: SpeechSynthesizer,
        *,
        shift_semitones: float = 2.0,
    ) -> None:
        if (
            isinstance(shift_semitones, bool)
            or not isinstance(shift_semitones, (int, float))
            or not math.isfinite(shift_semitones)
            or not 0 <= shift_semitones <= _MAX_SHIFT_SEMITONES
        ):
            raise ValueError("shift_semitones must be between 0 and 4")
        self._inner = inner
        self._shift_semitones = float(shift_semitones)

    async def synthesize(self, text: str) -> AsyncIterator[bytes]:
        if self._shift_semitones == 0:
            async for chunk in self._inner.synthesize(text):
                yield chunk
            return

        effect = _StreamingPitchAndFormantEffect(self._shift_semitones)
        try:
            async for chunk in self._inner.synthesize(text):
                for shaped in effect.push(chunk):
                    yield shaped
            for shaped in effect.flush():
                yield shaped
        except (av.FFmpegError, ValueError):
            raise TtsError(TtsFailureCode.PROVIDER_FAILED) from None


class _StreamingPitchAndFormantEffect:
    def __init__(self, shift_semitones: float) -> None:
        audio_format = TTS_OUTPUT_FORMAT
        self._sample_rate = audio_format.sample_rate
        self._sample_width = audio_format.sample_width
        self._frame_samples = (
            self._sample_rate * _FRAME_MILLISECONDS // 1000
        )
        self._frame_bytes = self._frame_samples * self._sample_width
        self._remainder = b""
        self._next_pts = 0
        self._closed = False

        ratio = 2 ** (shift_semitones / 12)
        shifted_sample_rate = round(self._sample_rate * ratio)

        graph = av.filter.Graph()
        source = graph.add_abuffer(
            format="s16",
            layout="mono",
            sample_rate=self._sample_rate,
            time_base=Fraction(1, self._sample_rate),
        )
        rate = graph.add("asetrate", str(shifted_sample_rate))
        resample = graph.add("aresample", str(self._sample_rate))
        tempo = graph.add("atempo", f"{1 / ratio:.8f}")
        high_pass = graph.add("highpass", f"f={_HIGH_PASS_HZ}")
        low_mid_cut = graph.add(
            "equalizer",
            f"f={_LOW_MID_FREQUENCY_HZ}:t=q:w=0.9:g={_LOW_MID_CUT_DB}",
        )
        presence_boost = graph.add(
            "equalizer",
            f"f={_PRESENCE_FREQUENCY_HZ}:t=q:w=1.0:g={_PRESENCE_BOOST_DB}",
        )
        sink = graph.add("abuffersink")
        source.link_to(rate)
        rate.link_to(resample)
        resample.link_to(tempo)
        tempo.link_to(high_pass)
        high_pass.link_to(low_mid_cut)
        low_mid_cut.link_to(presence_boost)
        presence_boost.link_to(sink)
        graph.configure()

        # Filter nodes borrow the graph's native storage. Keeping only source
        # and sink lets Graph be collected and leaves dangling FFmpeg pointers.
        self._graph = graph
        self._source = source
        self._sink = sink

    def push(self, data: bytes) -> list[bytes]:
        if self._closed:
            return []
        buffer = self._remainder + data
        output: list[bytes] = []
        offset = 0
        while offset + self._frame_bytes <= len(buffer):
            self._push_frame(buffer[offset : offset + self._frame_bytes])
            output.extend(self._drain())
            offset += self._frame_bytes
        self._remainder = buffer[offset:]
        return output

    def flush(self) -> list[bytes]:
        if self._closed:
            return []
        if self._remainder:
            if len(self._remainder) % self._sample_width:
                raise ValueError("TTS PCM ended with a partial sample")
            padded = self._remainder.ljust(self._frame_bytes, b"\x00")
            self._push_frame(padded)
            self._remainder = b""
        self._source.push(None)
        self._closed = True
        return self._drain()

    def _push_frame(self, pcm: bytes) -> None:
        frame = av.AudioFrame(
            format="s16",
            layout="mono",
            samples=self._frame_samples,
        )
        frame.sample_rate = self._sample_rate
        frame.time_base = Fraction(1, self._sample_rate)
        frame.pts = self._next_pts
        frame.planes[0].update(pcm)
        self._next_pts += self._frame_samples
        self._source.push(frame)

    def _drain(self) -> list[bytes]:
        output: list[bytes] = []
        while True:
            try:
                frame = self._sink.pull()
            except (BlockingIOError, EOFError):
                return output
            byte_count = frame.samples * self._sample_width
            output.append(bytes(frame.planes[0])[:byte_count])
