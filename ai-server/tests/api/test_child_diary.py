import json

import httpx
import pytest

from app.config import AppSettings
from app.main import create_app


def _gms_handler(request: httpx.Request) -> httpx.Response:
    payload = json.loads(request.content)
    assert payload["model"] == "gpt-5-mini"
    return httpx.Response(
        200,
        json={
            "choices": [{
                "message": {
                    "content": (
                        '{"gameLines":["오늘은 과일 자르기 게임을 했다.",'
                        '"과일을 많이 잘라서 신이 났다.",'
                        '"마지막 과일을 자른 순간이 기억에 남았다."],'
                        '"dailyLines":[]}'
                    )
                }
            }]
        },
    )


@pytest.mark.asyncio
async def test_generates_child_diary_for_one_conversation() -> None:
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
                "/internal/v1/child-diaries/summary",
                json={
                    "conversationId": "conversation-1",
                    "diaryDate": "2026-07-27",
                    "topics": [{
                        "topicType": "GAME",
                        "slots": {
                            "event": "과일 자르기 게임을 했다",
                            "people": [],
                            "place": None,
                            "emotion": "신이 났다",
                            "emotionReason": "과일을 많이 잘라서",
                            "memorableMoment": "마지막 과일을 자른 순간",
                        },
                    }],
                },
            )

    assert response.status_code == 200
    assert response.json()["title"] == "오늘의 일기"
    assert len(response.json()["lines"]) == 3
    assert response.json()["conversationId"] == "conversation-1"


@pytest.mark.asyncio
async def test_rejects_empty_conversation_facts_with_diary_error_code() -> None:
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
                "/internal/v1/child-diaries/summary",
                json={
                    "conversationId": "conversation-1",
                    "diaryDate": "2026-07-27",
                    "topics": [{"topicType": "DAILY", "slots": {}}],
                },
            )

    assert response.status_code == 422
    assert response.json() == {"error": "CHILD_DIARY_REQUEST_INVALID"}
