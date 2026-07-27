"""Audio format model and the STT input contract (DIARY-3-T01).

``docs/AI_CONVENTIONS.md`` §11: "Audio format은 sample rate, channel, sample
width를 타입 또는 명시적 모델로 함께 전달한다." PCM bytes therefore never
travel through this package on their own — they are always paired with the
``AudioFormat`` that describes them, so no conversion step can silently assume
a rate or channel count.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class AudioFormat:
    """Sample rate, channel count and sample width of a PCM stream."""

    sample_rate: int
    channels: int
    sample_width: int  # bytes per sample (s16 -> 2)

    def __post_init__(self) -> None:
        for name, value in (
            ("sample_rate", self.sample_rate),
            ("channels", self.channels),
            ("sample_width", self.sample_width),
        ):
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise ValueError(f"{name} must be a positive integer")

    @property
    def bytes_per_second(self) -> int:
        return self.sample_rate * self.channels * self.sample_width

    @property
    def bytes_per_sample(self) -> int:
        """Bytes for one sample across all channels (one interleaved frame)."""

        return self.channels * self.sample_width

    def duration_seconds(self, byte_count: int) -> float:
        return byte_count / self.bytes_per_second


# whisper-1은 16kHz mono s16 입력을 기대한다(jira/plan/DIARY-3-W01.md §1).
# Provider 고정 요구사항이라 설정이 아닌 상수로 둔다.
STT_INPUT_FORMAT = AudioFormat(sample_rate=16000, channels=1, sample_width=2)

# gpt-4o-mini-tts의 pcm 출력은 24kHz mono s16이다(실측, DIARY-3-W01.md §1).
TTS_OUTPUT_FORMAT = AudioFormat(sample_rate=24000, channels=1, sample_width=2)


@dataclass(frozen=True)
class PcmAudioChunk:
    """Raw PCM bytes together with the format describing them.

    ``data`` is ``repr=False`` on purpose: ``docs/AI_CONVENTIONS.md`` §13 bans
    Audio bytes from logs, and a chunk that lands in a log line or an exception
    message must not dump the child's audio.
    """

    data: bytes = field(repr=False)
    audio_format: AudioFormat

    @property
    def is_empty(self) -> bool:
        return not self.data

    @property
    def duration_seconds(self) -> float:
        return self.audio_format.duration_seconds(len(self.data))
