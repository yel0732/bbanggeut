"""Voice activity detection boundary (DIARY-3-T06).

The detector answers exactly one question — "does this frame carry voice-band
energy?" — and everything else (start confirmation, long-pause tolerance, end
of turn) lives in ``turn_detector.py``. The split matters because of what was
measured while planning (jira/plan/DIARY-3-W02.md §1): webrtcvad classifies
even a pure 440Hz tone as speech at every aggressiveness level. It is an
energy-band detector, not an utterance classifier, so coughs and clicks are
filtered by *duration* one layer up, never here.

``webrtcvad-wheels`` was chosen over the alternatives with the same probe: a
19kB prebuilt cp312 wheel matching our exact input format, where the original
``webrtcvad`` sdist needs a C compiler at install time and ``silero-vad``
drags in the whole of torch (plan §11-A).
"""

from __future__ import annotations

from typing import Protocol

import webrtcvad

from app.speech.audio_format import STT_INPUT_FORMAT, AudioFormat, PcmAudioChunk

# webrtcvad only accepts 10/20/30ms frames (measured: 25ms raises). 20ms
# matches the WebRTC media cadence; it is a library constraint, not a tunable.
VAD_FRAME_MILLISECONDS = 20

_SUPPORTED_SAMPLE_RATES = {8000, 16000, 32000, 48000}


class VoiceActivityDetector(Protocol):
    @property
    def frame_milliseconds(self) -> int:
        """The exact frame length this detector accepts."""
        ...

    def is_speech(self, frame: bytes) -> bool:
        """Judge one frame of ``frame_milliseconds`` mono s16 PCM."""
        ...


class WebrtcVoiceActivityDetector:
    """webrtcvad-backed detector for one session's audio stream."""

    def __init__(
        self,
        aggressiveness: int = 2,
        audio_format: AudioFormat = STT_INPUT_FORMAT,
    ) -> None:
        if aggressiveness not in range(4):
            raise ValueError("aggressiveness must be between 0 and 3")
        if audio_format.sample_rate not in _SUPPORTED_SAMPLE_RATES:
            raise ValueError("webrtcvad does not support this sample rate")
        if audio_format.channels != 1 or audio_format.sample_width != 2:
            raise ValueError("webrtcvad requires mono s16 audio")
        self._vad = webrtcvad.Vad(aggressiveness)
        self._sample_rate = audio_format.sample_rate
        self._frame_bytes = audio_format.bytes_per_second * VAD_FRAME_MILLISECONDS // 1000

    @property
    def frame_milliseconds(self) -> int:
        return VAD_FRAME_MILLISECONDS

    def is_speech(self, frame: bytes) -> bool:
        if len(frame) != self._frame_bytes:
            raise ValueError("frame length does not match the VAD frame size")
        return self._vad.is_speech(frame, self._sample_rate)


class FakeVoiceActivityDetector:
    """Content-based test double: any non-zero byte counts as speech.

    Lets turn-detector tests express scenarios as plain PCM — digital silence
    for pauses, any non-zero payload for speech — with no scripting to keep in
    sync with the number of frames fed.
    """

    def __init__(self, frame_milliseconds: int = VAD_FRAME_MILLISECONDS) -> None:
        self._frame_milliseconds = frame_milliseconds
        self.frames_judged = 0

    @property
    def frame_milliseconds(self) -> int:
        return self._frame_milliseconds

    def is_speech(self, frame: bytes) -> bool:
        self.frames_judged += 1
        return any(frame)


class VadFramer:
    """Recuts variable-size PCM chunks into exact VAD frames.

    T01's converter emits non-uniform chunk sizes (measured, W01 plan §1-B),
    while webrtcvad rejects anything that is not exactly 10/20/30ms — so the
    remainder of each chunk is carried into the next call.
    """

    def __init__(
        self,
        frame_milliseconds: int = VAD_FRAME_MILLISECONDS,
        audio_format: AudioFormat = STT_INPUT_FORMAT,
    ) -> None:
        if frame_milliseconds <= 0:
            raise ValueError("frame_milliseconds must be positive")
        self._frame_bytes = audio_format.bytes_per_second * frame_milliseconds // 1000
        self._audio_format = audio_format
        self._remainder = b""

    def push(self, chunk: PcmAudioChunk) -> list[bytes]:
        if chunk.audio_format != self._audio_format:
            raise ValueError("chunk format does not match the framer format")
        data = self._remainder + chunk.data
        frames = [
            data[offset : offset + self._frame_bytes]
            for offset in range(0, len(data) - self._frame_bytes + 1, self._frame_bytes)
        ]
        self._remainder = data[len(frames) * self._frame_bytes :]
        return frames

    def reset(self) -> None:
        self._remainder = b""
