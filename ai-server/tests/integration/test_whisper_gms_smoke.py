"""Opt-in smoke test for real whisper-1 transcription through SSAFY GMS.

Synthetic audio only — never a real child's voice (docs/AI_CONVENTIONS.md §14).
Silence is a useful probe here precisely because whisper hallucinates on it:
this asserts the response *shape* the hallucination guard depends on, not the
transcript.
"""

import io
import os
import wave

import httpx
import pytest

from app.config import AppSettings
from app.speech.audio_format import STT_INPUT_FORMAT
from app.speech.whisper_client import WhisperSpeechRecognizer


def _silent_wav(seconds: float) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as writer:
        writer.setnchannels(STT_INPUT_FORMAT.channels)
        writer.setsampwidth(STT_INPUT_FORMAT.sample_width)
        writer.setframerate(STT_INPUT_FORMAT.sample_rate)
        writer.writeframes(bytes(int(seconds * STT_INPUT_FORMAT.bytes_per_second)))
    return buffer.getvalue()


@pytest.mark.gms
@pytest.mark.asyncio
async def test_real_whisper_transcription_returns_guard_signals() -> None:
    if os.getenv("RUN_GMS_INTEGRATION") != "1" or not os.getenv("GMS_KEY"):
        pytest.skip("RUN_GMS_INTEGRATION=1 and GMS_KEY are required")

    settings = AppSettings.from_environment()
    async with httpx.AsyncClient(base_url=settings.gms_base_url, timeout=None) as http_client:
        recognizer = WhisperSpeechRecognizer(
            http_client,
            api_key=settings.gms_key,
            model=settings.stt_model,
            language=settings.stt_language,
            timeout_seconds=settings.stt_timeout_seconds,
        )
        result = await recognizer.transcribe(_silent_wav(2.0), 2.0)

    # verbose_json must survive the proxy — the guard is built on these fields.
    assert result.no_speech_prob is not None
    assert result.avg_logprob is not None
    assert result.audio_seconds == 2.0
