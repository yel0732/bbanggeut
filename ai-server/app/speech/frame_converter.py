"""WebRTC ``AudioFrame`` -> STT input PCM conversion (DIARY-3-T01).

aiortc hands us Opus-decoded Frames (typically 48kHz stereo s16, 20ms each);
whisper-1 wants 16kHz mono s16. This module owns that conversion and nothing
else — accumulating a whole utterance is T02's job.

Three behaviours of ``av.AudioResampler`` (av 17.1.0) shape this design. All
three were measured directly, not assumed (jira/plan/DIARY-3-W01.md §1):

1. ``resample()`` returns a ``list[AudioFrame]`` that may be empty or hold more
   than one Frame, and the output sizes are NOT uniform — a steady stream of
   20ms/960-sample inputs yields 304, then 320, then 320 samples, because the
   resampler holds an internal delay. Callers therefore never get "one Frame in,
   one Frame out"; they get a byte run to append.
2. **Output planes are padded.** A 304-sample mono s16 Frame reports
   ``buffer_size == 704`` where only ``304 * 2 == 608`` bytes are real audio.
   Handing ``bytes(plane)`` straight to a Provider would splice ~96 bytes of
   garbage into every chunk, so every Frame is sliced to its true length.
3. **A resampler cannot be reused after being flushed** — ``resample(None)``
   puts it at EOF and the next ``resample(frame)`` raises ``EOFError``. Both
   ``flush()`` and ``reset()`` therefore replace the instance rather than
   trying to rewind it.
"""

from __future__ import annotations

from av import AudioFrame
from av.audio.resampler import AudioResampler

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk

_SAMPLE_FORMAT_BY_WIDTH = {2: "s16"}
_LAYOUT_BY_CHANNELS = {1: "mono", 2: "stereo"}


class AudioConversionError(Exception):
    """A Frame could not be converted to the target STT input format.

    Wraps the underlying ``av`` failure so Provider/SDK exception types do not
    leak into the pipeline (``docs/AI_CONVENTIONS.md`` §10).
    """


class AudioFrameConverter:
    """Converts one session's inbound Frames to the STT input format.

    One instance per audio stream — the resampler carries internal state
    between Frames, so sharing an instance across sessions would interleave two
    children's audio (``docs/AI_CONVENTIONS.md`` §8).
    """

    def __init__(self, target: AudioFormat = STT_INPUT_FORMAT) -> None:
        try:
            self._sample_format = _SAMPLE_FORMAT_BY_WIDTH[target.sample_width]
            self._layout = _LAYOUT_BY_CHANNELS[target.channels]
        except KeyError as error:
            raise ValueError(
                "unsupported target audio format: "
                f"{target.channels}ch/{target.sample_width}B"
            ) from error
        self._target = target
        self._resampler = self._new_resampler()

    @property
    def target_format(self) -> AudioFormat:
        return self._target

    def convert(self, frame: AudioFrame) -> PcmAudioChunk:
        """Convert one inbound Frame.

        Returns an empty chunk when the resampler is still filling its internal
        delay — callers append unconditionally and judge on length.
        """

        return self._to_chunk(self._resample(frame))

    def flush(self) -> PcmAudioChunk:
        """Drain the resampler's tail at an utterance boundary.

        Used when a turn ends normally: the residual samples are real audio the
        child spoke, so they are returned rather than dropped. The resampler is
        replaced because a flushed one is at EOF (see module docstring §3).
        """

        chunk = self._to_chunk(self._resample(None))
        self._resampler = self._new_resampler()
        return chunk

    def reset(self) -> None:
        """Discard any buffered audio and start a fresh stream.

        The discard path, as opposed to ``flush()``: used when a turn is
        abandoned (connection teardown, error cleanup), where keeping the tail
        would leak one utterance's audio into the next
        (``docs/AI_CONVENTIONS.md`` §13).
        """

        self._resampler = self._new_resampler()

    def _new_resampler(self) -> AudioResampler:
        return AudioResampler(
            format=self._sample_format,
            layout=self._layout,
            rate=self._target.sample_rate,
        )

    def _resample(self, frame: AudioFrame | None) -> list[AudioFrame]:
        try:
            return self._resampler.resample(frame)
        except (ValueError, EOFError) as error:
            # ValueError: the Frame's rate/layout/format changed mid-stream and
            # no longer matches how this resampler was set up.
            # EOFError: resampling after a flush (unreachable while flush()
            # replaces the instance, but converted rather than surfaced raw).
            raise AudioConversionError("audio frame could not be resampled") from error

    def _to_chunk(self, frames: list[AudioFrame]) -> PcmAudioChunk:
        return PcmAudioChunk(
            data=b"".join(self._frame_bytes(frame) for frame in frames),
            audio_format=self._target,
        )

    def _frame_bytes(self, frame: AudioFrame) -> bytes:
        if (
            frame.sample_rate != self._target.sample_rate
            or frame.format.name != self._sample_format
            or len(frame.layout.channels) != self._target.channels
        ):
            # The resampler is configured with the target, so a mismatch here
            # means our own setup is wrong — fail loudly instead of shipping
            # mis-described bytes to the STT Provider.
            raise AudioConversionError("resampled frame does not match the target format")
        usable = frame.samples * self._target.bytes_per_sample
        return bytes(frame.planes[0])[:usable]  # drop plane padding (docstring §2)
