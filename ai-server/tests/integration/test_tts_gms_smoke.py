"""Opt-in smoke test for real gpt-4o-mini-tts streaming through SSAFY GMS.

Uses a short fixed phrase (no child data). Asserts that pcm streams back in
more than one chunk — the property the whole streaming design rests on.
"""

import os

import httpx
import pytest

from app.config import AppSettings
from app.speech.tts_client import GmsSpeechSynthesizer


@pytest.mark.gms
@pytest.mark.asyncio
async def test_real_tts_streams_pcm_in_multiple_chunks() -> None:
    if os.getenv("RUN_GMS_INTEGRATION") != "1" or not os.getenv("GMS_KEY"):
        pytest.skip("RUN_GMS_INTEGRATION=1 and GMS_KEY are required")

    settings = AppSettings.from_environment()
    async with httpx.AsyncClient(base_url=settings.gms_base_url, timeout=None) as http_client:
        synthesizer = GmsSpeechSynthesizer(
            http_client,
            api_key=settings.gms_key,
            model=settings.tts_model,
            voice=settings.tts_voice,
            first_byte_timeout_seconds=settings.tts_timeout_seconds,
        )
        chunks = 0
        total = 0
        async for chunk in synthesizer.synthesize("안녕하세요. 오늘 무엇을 했나요?"):
            chunks += 1
            total += len(chunk)

    assert total > 0
    assert chunks >= 2  # streamed, not a single blob
