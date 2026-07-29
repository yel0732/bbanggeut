"""Reuse synthesized audio for the AI messages that never change.

Measured time-to-first-byte for gpt-4o-mini-tts through GMS is 1.89s, and in the
sequential turn model (ADR-020) that delay sits between the child finishing a
sentence and hearing anything back. A large share of AI messages are fixed
strings, because ADR-017 makes ordinary questions rule-based templates — those
can be synthesized once and replayed.

Only **whole messages** qualify. The ordinary question path speaks an
acknowledgement derived from the child's own answer ("...산책 갔구나.") followed
by the template question, so the spoken text differs every turn. Splitting the
two and concatenating audio was considered and rejected: time-to-first-byte is
dominated by model latency rather than text length, so synthesizing a short
acknowledgement costs nearly the same as the full message and saves nothing.

The cache is deliberately **allowlist-bound**: only texts registered up front are
stored. Caching whatever comes through would grow without limit and would retain
children's own words, which the data policy keeps out of long-lived memory.
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Iterable

from app.speech.tts_client import SpeechSynthesizer, TtsError, TtsFailureCode

# 큐에 한 번에 쏟지 않도록 재생분을 잘라 내보낸다(0.5초 @ 24kHz mono s16).
_REPLAY_CHUNK_BYTES = 24_000


class CachedSpeechSynthesizer:
    """Wraps a synthesizer and replays audio for registered fixed texts.

    Shared across connections: synthesis is a stateless HTTP call, so the same
    bytes serve every child. Two connections may synthesize the same missing
    text concurrently and both will store it — the bytes are identical, so the
    duplicate call is wasted work rather than a correctness problem.
    """

    def __init__(
        self,
        inner: SpeechSynthesizer,
        logger: logging.Logger,
    ) -> None:
        self._inner = inner
        self._logger = logger
        self._allowed: set[str] = set()
        self._audio: dict[str, bytes] = {}

    @property
    def cached_count(self) -> int:
        return len(self._audio)

    def register(self, texts: Iterable[str]) -> None:
        """Declare which texts may be cached. Nothing else ever is."""

        for text in texts:
            if text and text.strip():
                self._allowed.add(text)

    async def prewarm(self) -> int:
        """Synthesize every registered text that is not cached yet.

        Best effort: a Provider failure here must not stop the server from
        starting, because the uncached path still works. Returns how many texts
        are cached afterwards.
        """

        for text in sorted(self._allowed - self._audio.keys()):
            try:
                self._audio[text] = await self._collect(text)
            except TtsError as error:
                # 텍스트 원문은 로그에 남기지 않는다(docs/AI_CONVENTIONS.md §13).
                self._logger.warning(
                    "fixed speech prewarm failed; falling back to live synthesis",
                    extra={"failure_code": error.code.value},
                )
        self._logger.info(
            "fixed speech prewarmed",
            extra={"cached": len(self._audio), "registered": len(self._allowed)},
        )
        return len(self._audio)

    async def synthesize(self, text: str) -> AsyncIterator[bytes]:
        cached = self._audio.get(text)
        if cached is not None:
            for offset in range(0, len(cached), _REPLAY_CHUNK_BYTES):
                yield cached[offset : offset + _REPLAY_CHUNK_BYTES]
            return
        if text not in self._allowed:
            async for chunk in self._inner.synthesize(text):
                yield chunk
            return
        # 등록됐지만 prewarm이 실패했거나 아직 안 돌았다 — 이번에 채운다.
        collected = bytearray()
        async for chunk in self._inner.synthesize(text):
            collected += chunk
            yield chunk
        if collected:
            self._audio[text] = bytes(collected)

    async def _collect(self, text: str) -> bytes:
        collected = bytearray()
        async for chunk in self._inner.synthesize(text):
            collected += chunk
        if not collected:
            # 빈 스트림을 캐시하면 그 문장은 영구히 무음이 된다.
            raise TtsError(TtsFailureCode.PROVIDER_FAILED)
        return bytes(collected)
