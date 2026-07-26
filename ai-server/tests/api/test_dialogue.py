"""Tests for the internal stateless dialogue endpoint."""

import json

import httpx
import pytest

from app.config import AppSettings
from app.main import create_app


def _gms_handler(request: httpx.Request) -> httpx.Response:
    payload = json.loads(request.content)
    developer_prompt = payload["messages"][0]["content"]
    if "구조화하는 분석기" in developer_prompt:
        content = (
            '{"event":"블록 놀이","people":[],"place":null,'
            '"emotion":null,"emotionReason":null,'
            '"memorableMoment":null}'
        )
    elif "판정하는 분류기" in developer_prompt:
        content = '{"valid":true,"issues":[]}'
    else:
        content = "그때 어떤 기분이었어?"
    return httpx.Response(
        200,
        json={"choices": [{"message": {"content": content}}]},
    )


@pytest.mark.asyncio
async def test_processes_internal_dialogue_request() -> None:
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
                "/internal/v1/dialogue/turns",
                json={
                    "requestId": "request-1",
                    "conversationId": "conversation-1",
                    "childAnswer": "합성 답변",
                    "currentQuestion": None,
                    "currentSlots": {},
                    "recentQuestions": [],
                    "gameContext": None,
                    "questionCount": 0,
                    "maxQuestionCount": 6,
                },
            )

    assert response.status_code == 200
    assert response.json()["decision"] == "CONTINUE_TOPIC"
    assert response.json()["assistantQuestion"] == (
        "그때 마음이 어땠는지 말해 줄래?"
    )
    assert response.json()["assistantMessage"] == (
        "합성 답변구나. 그때 마음이 어땠는지 말해 줄래?"
    )
    assert response.json()["questionGenerationStatus"] == "templateUsed"
    assert response.json()["questionGenerationSource"] == (
        "ruleBasedTemplate"
    )


@pytest.mark.asyncio
async def test_accepts_spring_topic_context_json_contract() -> None:
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
                "/internal/v1/dialogue/turns",
                json={
                    "requestId": "request-topic-context",
                    "conversationId": "conversation-topic-context",
                    "childAnswer": "오늘 친구랑 영화를 봤어",
                    "currentQuestion": None,
                    "currentTopic": "DAILY",
                    "nextTopic": None,
                    "currentTopicSlots": {},
                    "recentQuestions": [],
                    "topicContext": {
                        "topic": "DAILY",
                        "games": [],
                    },
                    "topicQuestionCount": 0,
                    "topicMaxQuestionCount": 6,
                },
            )

    assert response.status_code == 200
    assert response.json()["decision"] == "CONTINUE_TOPIC"


@pytest.mark.asyncio
async def test_validation_error_does_not_echo_child_answer() -> None:
    private_answer = "PRIVATE_CHILD_ANSWER"
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
                "/internal/v1/dialogue/turns",
                json={
                    "requestId": "",
                    "conversationId": "conversation-1",
                    "childAnswer": private_answer,
                    "currentSlots": {},
                    "questionCount": 0,
                    "maxQuestionCount": 6,
                },
            )

    assert response.status_code == 422
    assert private_answer not in response.text
    assert response.json() == {"error": "DIALOGUE_REQUEST_INVALID"}


@pytest.mark.asyncio
async def test_rejects_oversized_child_answer_before_gms_call() -> None:
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        return _gms_handler(request)

    app = create_app(
        AppSettings(gms_key="test-key"),
        transport=httpx.MockTransport(handler),
    )

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/internal/v1/dialogue/turns",
                json={
                    "requestId": "request-oversized",
                    "conversationId": "conversation-1",
                    "childAnswer": "가" * 2001,
                    "currentSlots": {},
                    "questionCount": 0,
                    "maxQuestionCount": 6,
                },
            )

    assert response.status_code == 422
    assert response.json() == {"error": "DIALOGUE_REQUEST_INVALID"}
    assert call_count == 0
