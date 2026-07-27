"""Per-session accumulation of one child utterance (DIARY-3-T02).

whisper-1 is a batch API: nothing can be recognised until the whole utterance
is in hand (jira/plan/DIARY-3-W01.md §1, `DEC-04`). This buffer is where an
utterance is assembled between turn boundaries, and it is the reason the STT
path needs no streaming machinery.

Two decisions worth stating, both from the plan (§3-3, §11-C):

- **Overflow closes the turn, it never drops audio.** In a streaming design a
  full buffer can drop the oldest chunk and carry on; here the buffer holds one
  indivisible input, so dropping from the middle would splice two disconnected
  halves of a sentence into a single transcription. ``append()`` instead
  reports that the cap is reached and the caller finalises the turn with the
  audio it already has.
- **Buffers are never shared between sessions** (``docs/AI_CONVENTIONS.md``
  §8). One instance belongs to one audio stream.

Raw audio is held in memory only for as long as the turn takes and is dropped
by ``reset()``/``close()`` — it is never written anywhere (ADR-006, §13).
"""

from __future__ import annotations

import io
import wave

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk


class UtteranceBuffer:
    """Accumulates one utterance's PCM up to a duration cap."""

    def __init__(
        self,
        max_seconds: float,
        audio_format: AudioFormat = STT_INPUT_FORMAT,
    ) -> None:
        if max_seconds <= 0:
            raise ValueError("max_seconds must be positive")
        self._audio_format = audio_format
        self._max_bytes = int(max_seconds * audio_format.bytes_per_second)
        self._parts: list[bytes] = []
        self._byte_count = 0
        self._closed = False

    @property
    def audio_format(self) -> AudioFormat:
        return self._audio_format

    @property
    def byte_count(self) -> int:
        return self._byte_count

    @property
    def duration_seconds(self) -> float:
        return self._audio_format.duration_seconds(self._byte_count)

    @property
    def is_empty(self) -> bool:
        return self._byte_count == 0

    @property
    def is_full(self) -> bool:
        return self._byte_count >= self._max_bytes

    @property
    def is_closed(self) -> bool:
        return self._closed

    def append(self, chunk: PcmAudioChunk) -> bool:
        """Add a chunk. Returns ``False`` once the cap is reached.

        A ``False`` return does not mean the chunk was rejected — the audio is
        always kept. It means "this utterance has grown to its limit, finalise
        the turn now" (§3-3).
        """

        if self._closed:
            raise RuntimeError("utterance buffer is closed")
        if chunk.audio_format != self._audio_format:
            raise ValueError("chunk format does not match the buffer format")
        if not chunk.is_empty:
            self._parts.append(chunk.data)
            self._byte_count += len(chunk.data)
        return not self.is_full

    def to_wav(self) -> bytes:
        """Wrap the accumulated PCM in a RIFF container.

        whisper's ``/audio/transcriptions`` takes a media file, not raw PCM, so
        a header is required. ``wave`` from the stdlib is enough — no new
        dependency (jira/plan/DIARY-3-W01.md §5).
        """

        buffer = io.BytesIO()
        with wave.open(buffer, "wb") as writer:
            writer.setnchannels(self._audio_format.channels)
            writer.setsampwidth(self._audio_format.sample_width)
            writer.setframerate(self._audio_format.sample_rate)
            writer.writeframes(b"".join(self._parts))
        return buffer.getvalue()

    def reset(self) -> None:
        """Drop the accumulated audio and start a new utterance. Idempotent."""

        self._parts.clear()
        self._byte_count = 0

    def close(self) -> None:
        """Drop the audio permanently. Idempotent; further appends raise."""

        self.reset()
        self._closed = True
