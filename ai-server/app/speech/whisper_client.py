"""GMS whisper-1 adapter for the ``SpeechRecognizer`` boundary (DIARY-3-T03).

``POST {GMS_BASE_URL}/audio/transcriptions`` with ``model=whisper-1`` and
``response_format=verbose_json``, verified against the real gateway
(jira/plan/DIARY-3-W01.md §1). ``verbose_json`` is what makes the hallucination
guard possible: it returns ``no_speech_prob`` and ``avg_logprob`` per segment,
which separate real speech (0.00 / -0.299) from silence (0.94 / -0.515).

Deliberate omissions:

- **No ``prompt``.** Measured: a Korean prompt made whisper invent a fluent
  Korean sentence for pure silence *and* dropped ``no_speech_prob`` from 0.94
  to 0.51, weakening the very signal used to detect that. The vocabulary
  benefit is not worth disarming the guard.
- **No retry.** Realtime audio has no原本 to resend and the child is waiting;
  a failed turn is handed to the dialogue recovery policy instead (§11-E).
"""

from __future__ import annotations

import asyncio

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError

from app.speech.recognizer import SttError, SttFailureCode, SttResult

_TRANSCRIPTIONS_PATH = "/audio/transcriptions"


class _WhisperSegment(BaseModel):
    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True)

    end: float | None = None
    avg_logprob: float | None = None
    no_speech_prob: float | None = None


class _WhisperResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", hide_input_in_errors=True)

    text: str | None = None
    duration: float | None = None
    segments: tuple[_WhisperSegment, ...] = ()


class WhisperSpeechRecognizer:
    """Transcribes one utterance through GMS's whisper-1."""

    def __init__(
        self,
        http_client: httpx.AsyncClient,
        *,
        api_key: str,
        model: str = "whisper-1",
        language: str = "ko",
        timeout_seconds: float = 10.0,
    ) -> None:
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValueError("api_key must contain text")
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self._http_client = http_client
        self._api_key = api_key
        self._model = model
        self._language = language
        self._timeout_seconds = timeout_seconds

    async def transcribe(self, wav_bytes: bytes, audio_seconds: float) -> SttResult:
        if not wav_bytes:
            raise SttError(SttFailureCode.AUDIO_FORMAT_INVALID)
        response = await self._post(wav_bytes)
        self._raise_for_status(response)
        return self._to_result(response, audio_seconds)

    async def _post(self, wav_bytes: bytes) -> httpx.Response:
        try:
            async with asyncio.timeout(self._timeout_seconds):
                return await self._http_client.post(
                    _TRANSCRIPTIONS_PATH,
                    headers={"Authorization": f"Bearer {self._api_key}"},
                    files={"file": ("utterance.wav", wav_bytes, "audio/wav")},
                    data={
                        "model": self._model,
                        "language": self._language,
                        "response_format": "verbose_json",
                        "temperature": "0",
                    },
                )
        except (TimeoutError, httpx.TimeoutException):
            raise SttError(SttFailureCode.PROVIDER_TIMEOUT) from None
        except httpx.HTTPError:
            raise SttError(SttFailureCode.PROVIDER_FAILED) from None

    def _raise_for_status(self, response: httpx.Response) -> None:
        if response.status_code >= 400:
            # The body can echo Provider detail; only the code crosses this
            # boundary (docs/AI_CONVENTIONS.md §13).
            raise SttError(SttFailureCode.PROVIDER_FAILED)

    def _to_result(self, response: httpx.Response, audio_seconds: float) -> SttResult:
        try:
            parsed = _WhisperResponse.model_validate(response.json())
        except (ValueError, ValidationError):
            raise SttError(SttFailureCode.PROVIDER_FAILED) from None

        segments = parsed.segments
        return SttResult(
            text=(parsed.text or "").strip(),
            # Trust our own measured duration over the Provider's echo.
            audio_seconds=audio_seconds,
            no_speech_prob=_max_of(segment.no_speech_prob for segment in segments),
            avg_logprob=_mean_of(segment.avg_logprob for segment in segments),
            reported_end_seconds=_max_of(segment.end for segment in segments),
        )


def _max_of(values) -> float | None:
    present = [value for value in values if value is not None]
    return max(present) if present else None


def _mean_of(values) -> float | None:
    present = [value for value in values if value is not None]
    return sum(present) / len(present) if present else None
