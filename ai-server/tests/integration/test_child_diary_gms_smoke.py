"""Opt-in real GMS smoke test for child diary generation."""

import os

import httpx
import pytest

from app.child_diary.generator import ChildDiaryGenerator
from app.child_diary.schemas import ChildDiarySummaryRequest
from app.config import AppSettings
from app.providers.gms_chat import GmsChatClient


@pytest.mark.gms
@pytest.mark.asyncio
async def test_real_gms_child_diary_generation() -> None:
    if os.getenv("RUN_GMS_INTEGRATION") != "1":
        pytest.skip("RUN_GMS_INTEGRATION=1 is required")
    settings = AppSettings.from_environment()

    async with httpx.AsyncClient(
        base_url=settings.gms_base_url,
        timeout=settings.gms_timeout_seconds,
    ) as http_client:
        generator = ChildDiaryGenerator(
            provider=GmsChatClient(
                http_client,
                api_key=settings.gms_key,
                model=settings.gms_child_diary_model,
            ),
            timeout_seconds=settings.gms_timeout_seconds,
            max_attempts=settings.gms_max_attempts,
        )
        result = await generator.generate(
            ChildDiarySummaryRequest.model_validate({
                "conversationId": "synthetic-conversation",
                "diaryDate": "2026-07-27",
                "topics": [
                    {
                        "topicType": "GAME",
                        "slots": {
                            "event": "과일 자르기 게임을 했다",
                            "emotion": "신났다",
                            "memorableMoment": "큰 수박을 자른 순간",
                        },
                    },
                    {
                        "topicType": "DAILY",
                        "slots": {
                            "event": "친구와 놀이터에서 놀았다",
                            "emotion": "즐거웠다",
                        },
                    },
                ],
            })
        )

    assert result.title == "오늘의 일기"
    assert 3 <= len(result.lines) <= 4
    assert all(0 < len(line) <= 40 for line in result.lines)
    assert result.model_version == settings.gms_child_diary_model
