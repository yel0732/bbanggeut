"""Streaming TTS boundary and GMS adapter (DIARY-3-T16).

``gpt-4o-mini-tts`` is the only TTS model on GMS (measured, jira/plan/
DIARY-3-W01.md §1); with ``response_format=pcm`` it streams raw 24kHz mono s16
with no container. First bytes arrived at ~1.9s while full synthesis took
~3.2s, so the point of this boundary is to hand chunks on as they arrive rather
than wait for the whole clip.

Deliberate omissions, mirroring the STT adapter:

- **No ``prompt``/``instructions``.** Voice styling is a product decision at
  the level of the character's name (W04 plan §11-B); the voice is a config
  knob and nothing shapes tone beyond it yet.
- **No retry.** The question text still exists, so a failure becomes
  ``assistant.audio.failed`` and the client falls back to showing text (§22
  ``TTS_FAILED``) rather than making the child wait through a retry.
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from enum import StrEnum

import httpx

from app.speech.audio_format import TTS_OUTPUT_FORMAT

__all__ = [
    "TTS_OUTPUT_FORMAT",
    "TtsError",
    "TtsFailureCode",
    "SpeechSynthesizer",
    "GmsSpeechSynthesizer",
    "FakeSpeechSynthesizer",
]

_SPEECH_PATH = "/audio/speech"


class TtsFailureCode(StrEnum):
    PROVIDER_TIMEOUT = "providerTimeout"
    PROVIDER_FAILED = "providerFailed"


class TtsError(Exception):
    """Synthesis failed. Carries a code, never Provider text or audio bytes."""

    def __init__(self, code: TtsFailureCode) -> None:
        super().__init__(code.value)
        self.code = code


class SpeechSynthesizer:
    """Boundary a session synthesizes one AI turn's text through."""

    def synthesize(self, text: str) -> AsyncIterator[bytes]:  # pragma: no cover - protocol
        """Yield 24kHz mono s16 PCM chunks, or raise ``TtsError``."""
        raise NotImplementedError


class GmsSpeechSynthesizer:
    """Streams speech from GMS's gpt-4o-mini-tts."""

    def __init__(
        self,
        http_client: httpx.AsyncClient,
        *,
        api_key: str,
        model: str = "gpt-4o-mini-tts",
        voice: str = "alloy",
        first_byte_timeout_seconds: float = 10.0,
    ) -> None:
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValueError("api_key must contain text")
        if not isinstance(voice, str) or not voice.strip():
            raise ValueError("voice must contain text")
        if first_byte_timeout_seconds <= 0:
            raise ValueError("first_byte_timeout_seconds must be positive")
        self._http_client = http_client
        self._api_key = api_key
        self._model = model
        self._voice = voice
        self._first_byte_timeout_seconds = first_byte_timeout_seconds

    async def synthesize(self, text: str) -> AsyncIterator[bytes]:
        request = self._http_client.build_request(
            "POST",
            _SPEECH_PATH,
            headers={"Authorization": f"Bearer {self._api_key}"},
            json={
                "model": self._model,
                "voice": self._voice,
                "input": text,
                "response_format": "pcm",
            },
        )
        try:
            response = await self._send(request)
        except (TimeoutError, httpx.TimeoutException):
            raise TtsError(TtsFailureCode.PROVIDER_TIMEOUT) from None
        except httpx.HTTPError:
            raise TtsError(TtsFailureCode.PROVIDER_FAILED) from None

        try:
            if response.status_code >= 400:
                # Body may echo Provider detail; only the code crosses out (§13).
                raise TtsError(TtsFailureCode.PROVIDER_FAILED)
            async for chunk in self._iter_chunks(response):
                if chunk:
                    yield chunk
        finally:
            await response.aclose()

    async def _send(self, request: httpx.Request) -> httpx.Response:
        # Bound only the wait for the first byte — total time is the clip length
        # and must not itself time out mid-stream.
        async with asyncio.timeout(self._first_byte_timeout_seconds):
            return await self._http_client.send(request, stream=True)

    async def _iter_chunks(self, response: httpx.Response) -> AsyncIterator[bytes]:
        try:
            async for chunk in response.aiter_bytes():
                yield chunk
        except httpx.HTTPError:
            raise TtsError(TtsFailureCode.PROVIDER_FAILED) from None


class FakeSpeechSynthesizer:
    """Test double: scripted chunks, a release gate, and failure injection."""

    def __init__(
        self,
        chunks: list[bytes] | None = None,
        *,
        error: TtsError | None = None,
    ) -> None:
        self._chunks = list(chunks or [])
        self._error = error
        self.calls: list[str] = []
        self.release: asyncio.Event | None = None
        self.started = asyncio.Event()

    async def synthesize(self, text: str) -> AsyncIterator[bytes]:
        self.calls.append(text)
        self.started.set()
        for index, chunk in enumerate(self._chunks):
            if self.release is not None and index > 0:
                await self.release.wait()
            yield chunk
        if self._error is not None:
            raise self._error
