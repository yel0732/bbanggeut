import json

import httpx
import pytest

from app.config import AppSettings
from app.main import create_app


def _gms_handler(request: httpx.Request) -> httpx.Response:
    payload = json.loads(request.content)
    assert payload["model"] == "gpt-5.4-mini"
    return httpx.Response(
        200,
        json={
            "choices": [{
                "message": {
                    "content": (
                        '{"title":"즐거운 축구 이야기",'
                        '"dailyStory":"친구와 축구한 이야기를 즐겁게 나누었어요.",'
                        '"emotionDetails":[{"emotion":"즐거움","reason":"친구와 축구해서"}],'
                        '"interests":["축구"],"notableQuotes":[],'
                        '"conversationGuide":{"description":"축구 이야기를 나눠보세요.",'
                        '"questions":["어떤 순간이 즐거웠어?"]}}'
                    )
                }
            }]
        },
    )


@pytest.mark.asyncio
async def test_generates_daily_report_summary() -> None:
    app = create_app(
        AppSettings(gms_key="test-key"),
        transport=httpx.MockTransport(_gms_handler),
    )

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/internal/v1/reports/daily-summary",
                json={
                    "reportDate": "2026-07-27",
                    "events": ["친구와 축구함"],
                    "emotions": ["즐거움"],
                    "interests": ["축구"],
                    "conversations": [],
                    "games": [],
                },
            )

    assert response.status_code == 200
    assert response.json() == {
        "title": "즐거운 축구 이야기",
        "dailyStory": "친구와 축구한 이야기를 즐겁게 나누었어요.",
        "emotionDetails": [{"emotion": "즐거움", "reason": "친구와 축구해서"}],
        "interests": ["축구"],
        "notableQuotes": [],
        "conversationGuide": {
            "description": "축구 이야기를 나눠보세요.",
            "questions": ["어떤 순간이 즐거웠어?"],
        },
        "modelVersion": "gpt-5.4-mini",
        "promptVersion": "daily-report-v2",
    }


@pytest.mark.asyncio
async def test_rejects_no_activity_request_without_provider_call() -> None:
    app = create_app(
        AppSettings(gms_key="test-key"),
        transport=httpx.MockTransport(_gms_handler),
    )

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/internal/v1/reports/daily-summary",
                json={
                    "reportDate": "2026-07-27",
                    "events": [],
                    "emotions": [],
                    "interests": [],
                    "games": [],
                },
            )

    assert response.status_code == 503
