"""Opt-in smoke test for real daily report generation through SSAFY GMS."""

import os

import httpx
import pytest

from app.config import AppSettings
from app.providers.gms_chat import GmsChatClient
from app.report.generator import DailyReportGenerator
from app.report.schemas import DailyReportSummaryRequest


@pytest.mark.gms
@pytest.mark.asyncio
async def test_real_gms_daily_report_generation() -> None:
    if os.getenv("RUN_GMS_INTEGRATION") != "1":
        pytest.skip("RUN_GMS_INTEGRATION=1 is required")
    settings = AppSettings.from_environment()

    async with httpx.AsyncClient(
        base_url=settings.gms_base_url,
        timeout=settings.gms_timeout_seconds,
    ) as http_client:
        generator = DailyReportGenerator(
            provider=GmsChatClient(
                http_client,
                api_key=settings.gms_key,
                model=settings.gms_report_model,
            ),
            timeout_seconds=settings.gms_timeout_seconds,
            max_attempts=settings.gms_max_attempts,
        )
        result = await generator.generate(
            DailyReportSummaryRequest.model_validate({
                "reportDate": "2026-07-27",
                "events": ["블록으로 탑을 만들었다"],
                "emotions": ["즐거웠다"],
                "interests": ["블록 놀이"],
                "games": [],
            })
        )

    assert result.title.strip()
    assert result.body.strip()
    assert result.model_version == settings.gms_report_model
    assert result.prompt_version == "daily-report-v1"
