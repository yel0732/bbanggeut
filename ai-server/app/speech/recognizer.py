"""Speech recognition boundary (DIARY-3-T03).

``DEC-04`` resolved to whisper-1, a **batch** API — GMS carries no
``gpt-4o-transcribe`` model and whisper-1 has no streaming mode (measured,
jira/plan/DIARY-3-W01.md §1). The abstraction therefore takes a complete
utterance and returns one result; there is deliberately no
send/partial/finish session interface, because nothing here could ever produce
a partial and ``docs/AI_CONVENTIONS.md`` §17 forbids abstractions for
capabilities we do not have.

Providers are kept behind ``SpeechRecognizer`` so a future streaming provider
is a new implementation rather than a change to the pipeline (§10).
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Protocol


@dataclass(frozen=True)
class SttResult:
    """One transcription plus the signals used to judge whether to trust it.

    ``text`` is ``repr=False``: the child's transcript must not reach logs or
    exception messages (``docs/AI_CONVENTIONS.md`` §13).
    """

    text: str = field(repr=False)
    audio_seconds: float
    no_speech_prob: float | None = None
    avg_logprob: float | None = None
    reported_end_seconds: float | None = None

    @property
    def confidence(self) -> float | None:
        """whisper's mean token log-probability mapped into ``(0, 1]``.

        A relative signal, not an accuracy figure: measured -0.299 for real
        speech (~0.74) against -0.515/-0.941 for hallucinated silence and tone
        (~0.60/~0.39). Surfaced as ``stt.final.confidence`` and stored in
        ``conversation_turn.stt_confidence`` (CONV-REQ-015).
        """

        if self.avg_logprob is None:
            return None
        return math.exp(self.avg_logprob)


class SttFailureCode(StrEnum):
    PROVIDER_TIMEOUT = "providerTimeout"
    PROVIDER_FAILED = "providerFailed"
    AUDIO_FORMAT_INVALID = "audioFormatInvalid"


class SttError(Exception):
    """Recognition failed. Carries a code, never Provider text or audio."""

    def __init__(self, code: SttFailureCode) -> None:
        super().__init__(code.value)
        self.code = code


class SpeechRecognizer(Protocol):
    async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
        """Transcribe one complete utterance, or raise ``SttError``."""
        ...


class FakeSpeechRecognizer:
    """Test double: scripted results/failures, records what it was asked to do."""

    def __init__(
        self,
        results: list[SttResult | Exception] | None = None,
    ) -> None:
        self._results: list[SttResult | Exception] = list(results or [])
        self.calls: list[tuple[int, float]] = []

    def queue(self, result: SttResult | Exception) -> None:
        self._results.append(result)

    async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
        self.calls.append((len(wav_bytes), audio_seconds))
        if not self._results:
            return SttResult(
                text="기본 응답입니다.",
                audio_seconds=audio_seconds,
                no_speech_prob=0.0,
                avg_logprob=-0.3,
                reported_end_seconds=audio_seconds,
            )
        outcome = self._results.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return outcome
