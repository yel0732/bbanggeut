"""GMS gpt-4o-mini-tts streaming adapter tests (T16, MockTransport).

The real gateway is only touched by the opt-in smoke test; §10 keeps real
Providers out of the default suite.
"""

import json

import httpx
import pytest

from app.speech.tts_client import (
    DINO_VOICE_INSTRUCTIONS,
    GmsSpeechSynthesizer,
    TtsError,
    TtsFailureCode,
)


def _synth(handler, **kwargs) -> tuple[GmsSpeechSynthesizer, httpx.AsyncClient]:
    client = httpx.AsyncClient(
        base_url="https://gms.example/v1",
        transport=httpx.MockTransport(handler),
    )
    return GmsSpeechSynthesizer(client, api_key="test-key", **kwargs), client


async def _collect(synth, text: str) -> bytes:
    out = b""
    async for chunk in synth.synthesize(text):
        out += chunk
    return out


@pytest.mark.asyncio
async def test_streams_pcm_chunks() -> None:
    synth, client = _synth(lambda r: httpx.Response(200, content=b"\x01\x02" * 500))
    async with client:
        audio = await _collect(synth, "안녕")

    assert len(audio) == 1000


@pytest.mark.asyncio
async def test_sends_pcm_request_with_character_instructions() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["auth"] = request.headers.get("authorization")
        seen["body"] = request.content
        return httpx.Response(200, content=b"\x00\x00")

    synth, client = _synth(handler)
    async with client:
        await _collect(synth, "오늘 뭐 했어?")

    body = seen["body"]
    payload = json.loads(body)
    assert seen["url"] == "https://gms.example/v1/audio/speech"
    assert seen["auth"] == "Bearer test-key"
    assert b"gpt-4o-mini-tts" in body
    assert b'"response_format":"pcm"' in body
    assert payload["voice"] == "shimmer"
    assert payload["instructions"] == DINO_VOICE_INSTRUCTIONS
    assert "prompt" not in payload


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [400, 401, 429, 500, 503])
async def test_http_errors_become_provider_failed(status: int) -> None:
    synth, client = _synth(
        lambda r: httpx.Response(status, json={"message": "internal detail"})
    )
    async with client:
        with pytest.raises(TtsError) as error:
            await _collect(synth, "x")

    assert error.value.code is TtsFailureCode.PROVIDER_FAILED
    assert "internal detail" not in str(error.value)


@pytest.mark.asyncio
async def test_timeout_is_reported_as_timeout() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("too slow", request=request)

    synth, client = _synth(handler)
    async with client:
        with pytest.raises(TtsError) as error:
            await _collect(synth, "x")

    assert error.value.code is TtsFailureCode.PROVIDER_TIMEOUT


@pytest.mark.asyncio
async def test_network_error_is_reported_as_provider_failure() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("no route", request=request)

    synth, client = _synth(handler)
    async with client:
        with pytest.raises(TtsError) as error:
            await _collect(synth, "x")

    assert error.value.code is TtsFailureCode.PROVIDER_FAILED


@pytest.mark.asyncio
async def test_calls_the_provider_once_without_retry() -> None:
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(500, json={})

    synth, client = _synth(handler)
    async with client:
        with pytest.raises(TtsError):
            await _collect(synth, "x")

    assert len(calls) == 1


def test_rejects_invalid_construction() -> None:
    client = httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200)))
    with pytest.raises(ValueError):
        GmsSpeechSynthesizer(client, api_key="  ")
    with pytest.raises(ValueError):
        GmsSpeechSynthesizer(client, api_key="k", voice=" ")
    with pytest.raises(ValueError):
        GmsSpeechSynthesizer(client, api_key="k", instructions=" ")
    with pytest.raises(ValueError):
        GmsSpeechSynthesizer(client, api_key="k", first_byte_timeout_seconds=0)
