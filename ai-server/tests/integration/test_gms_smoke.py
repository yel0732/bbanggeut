"""Opt-in smoke test for the real SSAFY GMS endpoint."""

import os

import httpx
import pytest

from app.config import AppSettings
from app.providers import GmsChatClient, GmsChatMessage


@pytest.mark.gms
@pytest.mark.asyncio
async def test_real_gms_chat_completion() -> None:
    if os.getenv("RUN_GMS_INTEGRATION") != "1" or not os.getenv("GMS_KEY"):
        pytest.skip("RUN_GMS_INTEGRATION=1 and GMS_KEY are required")

    settings = AppSettings.from_environment()
    async with httpx.AsyncClient(
        base_url=settings.gms_base_url,
        timeout=settings.gms_timeout_seconds,
    ) as http_client:
        client = GmsChatClient(
            http_client,
            api_key=settings.gms_key,
            model=settings.gms_analysis_model,
        )
        result = await client.complete(
            (
                GmsChatMessage("developer", "Answer in Korean."),
                GmsChatMessage("user", "한 단어로 인사해 줘."),
            )
        )

    assert result
