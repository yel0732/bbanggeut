"""Tests for the minimal GMS Chat Completions client."""

import json

import httpx
import pytest

from app.dialogue import QuestionProviderError, QuestionProviderErrorCode
from app.providers import GmsChatClient, GmsChatMessage


def _client(
    handler,
) -> tuple[GmsChatClient, httpx.AsyncClient]:
    http_client = httpx.AsyncClient(
        base_url="https://gms.example/v1",
        transport=httpx.MockTransport(handler),
    )
    return (
        GmsChatClient(
            http_client,
            api_key="PRIVATE_GMS_KEY",
            model="gpt-5.4-nano",
        ),
        http_client,
    )


@pytest.mark.asyncio
async def test_sends_openai_compatible_request() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers["Authorization"]
        captured["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={
                "choices": [
                    {"message": {"content": "  안전한 응답  "}}
                ]
            },
        )

    client, http_client = _client(handler)
    try:
        result = await client.complete(
            (
                GmsChatMessage("developer", "개발자 지시"),
                GmsChatMessage("user", "사용자 입력"),
            )
        )
    finally:
        await http_client.aclose()

    assert result == "안전한 응답"
    assert captured["url"] == "https://gms.example/v1/chat/completions"
    assert captured["authorization"] == "Bearer PRIVATE_GMS_KEY"
    assert captured["body"] == {
        "model": "gpt-5.4-nano",
        "messages": [
            {"role": "developer", "content": "개발자 지시"},
            {"role": "user", "content": "사용자 입력"},
        ],
    }


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "code", "retryable"),
    [
        (429, QuestionProviderErrorCode.RATE_LIMITED, True),
        (503, QuestionProviderErrorCode.TEMPORARY_UNAVAILABLE, True),
        (401, QuestionProviderErrorCode.AUTHENTICATION_FAILED, False),
        (400, QuestionProviderErrorCode.INVALID_REQUEST, False),
    ],
)
async def test_maps_http_errors_without_response_body(
    status: int,
    code: QuestionProviderErrorCode,
    retryable: bool,
) -> None:
    client, http_client = _client(
        lambda request: httpx.Response(
            status,
            text="PRIVATE_PROVIDER_RESPONSE",
        )
    )
    try:
        with pytest.raises(QuestionProviderError) as caught:
            await client.complete(
                (GmsChatMessage("user", "PRIVATE_PROMPT"),)
            )
    finally:
        await http_client.aclose()

    assert caught.value.code is code
    assert caught.value.retryable is retryable
    assert "PRIVATE_PROVIDER_RESPONSE" not in str(caught.value)
    assert "PRIVATE_PROMPT" not in str(caught.value)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"choices": []},
        {"choices": [{"message": {"content": None}}]},
        {"choices": [{"message": {"content": "  "}}]},
    ],
)
async def test_rejects_invalid_success_response(
    payload: dict[str, object],
) -> None:
    client, http_client = _client(
        lambda request: httpx.Response(200, json=payload)
    )
    try:
        with pytest.raises(QuestionProviderError) as caught:
            await client.complete((GmsChatMessage("user", "prompt"),))
    finally:
        await http_client.aclose()

    assert caught.value.code is (
        QuestionProviderErrorCode.UNKNOWN_PROVIDER_ERROR
    )
    assert not caught.value.retryable


@pytest.mark.asyncio
async def test_message_and_client_repr_hide_sensitive_values() -> None:
    client, http_client = _client(
        lambda request: httpx.Response(200, json={"choices": []})
    )

    try:
        assert "PRIVATE_GMS_KEY" not in repr(client)
        assert "PRIVATE_PROMPT" not in repr(
            GmsChatMessage("user", "PRIVATE_PROMPT")
        )
    finally:
        await http_client.aclose()
