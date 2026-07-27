"""GMS whisper-1 adapter tests against a mock transport (T03).

The real gateway is only touched by the opt-in smoke test
(``tests/integration/test_whisper_gms_smoke.py``); §10 forbids real Provider
calls from ordinary unit tests.
"""

import httpx
import pytest

from app.speech.recognizer import SttError, SttFailureCode
from app.speech.whisper_client import WhisperSpeechRecognizer

# Shape of a real GMS verbose_json body (values from the 2026-07-27 probe).
_REAL_SPEECH_BODY = {
    "task": "transcribe",
    "language": "korean",
    "duration": 3.65,
    "text": "오늘 과일 자르기 게임을 했는데 정말 재미있었어.",
    "segments": [
        {
            "id": 0,
            "start": 0.0,
            "end": 3.65,
            "text": " 오늘 과일 자르기 게임을 했는데 정말 재미있었어.",
            "avg_logprob": -0.299,
            "no_speech_prob": 0.0,
        }
    ],
}


def _recognizer(handler, **kwargs) -> tuple[WhisperSpeechRecognizer, httpx.AsyncClient]:
    client = httpx.AsyncClient(
        base_url="https://gms.example/v1",
        transport=httpx.MockTransport(handler),
    )
    return WhisperSpeechRecognizer(client, api_key="test-key", **kwargs), client


@pytest.mark.asyncio
async def test_parses_verbose_json_into_a_result() -> None:
    recognizer, client = _recognizer(lambda request: httpx.Response(200, json=_REAL_SPEECH_BODY))
    async with client:
        result = await recognizer.transcribe(b"RIFFfake", 3.65)

    assert result.text == "오늘 과일 자르기 게임을 했는데 정말 재미있었어."
    assert result.no_speech_prob == 0.0
    assert result.avg_logprob == -0.299
    assert result.reported_end_seconds == 3.65
    assert result.confidence == pytest.approx(0.7415, abs=1e-3)


@pytest.mark.asyncio
async def test_sends_the_documented_request_shape() -> None:
    seen: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["auth"] = request.headers.get("authorization")
        seen["body"] = request.content
        return httpx.Response(200, json=_REAL_SPEECH_BODY)

    recognizer, client = _recognizer(handler)
    async with client:
        await recognizer.transcribe(b"RIFFfake", 3.65)

    body = seen["body"]
    assert seen["url"] == "https://gms.example/v1/audio/transcriptions"
    assert seen["auth"] == "Bearer test-key"
    assert b"whisper-1" in body
    assert b"verbose_json" in body
    assert b'name="language"' in body and b"ko" in body


@pytest.mark.asyncio
async def test_never_sends_a_prompt() -> None:
    """Measured: a prompt induces fluent Korean hallucination on silence and
    drops no_speech_prob 0.94 -> 0.51, disarming the guard (plan §1)."""

    seen: dict[str, bytes] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = request.content
        return httpx.Response(200, json=_REAL_SPEECH_BODY)

    recognizer, client = _recognizer(handler)
    async with client:
        await recognizer.transcribe(b"RIFFfake", 3.65)

    assert b'name="prompt"' not in seen["body"]


@pytest.mark.asyncio
async def test_aggregates_multi_segment_signals() -> None:
    """The prompt-hallucination probe returned two segments; the guard needs the
    worst no_speech_prob and the furthest timestamp, not just the first."""

    body = {
        "duration": 2.0,
        "text": "오늘의 컨텐츠는 여기까지입니다. 시청해주셔서 감사합니다.",
        "segments": [
            {"end": 3.0, "avg_logprob": -0.9, "no_speech_prob": 0.4},
            {"end": 5.0, "avg_logprob": -1.1, "no_speech_prob": 0.51},
        ],
    }
    recognizer, client = _recognizer(lambda request: httpx.Response(200, json=body))
    async with client:
        result = await recognizer.transcribe(b"RIFFfake", 2.0)

    assert result.no_speech_prob == 0.51
    assert result.reported_end_seconds == 5.0
    assert result.avg_logprob == pytest.approx(-1.0)


@pytest.mark.asyncio
async def test_reports_our_measured_duration_not_the_provider_echo() -> None:
    body = dict(_REAL_SPEECH_BODY, duration=99.0)
    recognizer, client = _recognizer(lambda request: httpx.Response(200, json=body))
    async with client:
        result = await recognizer.transcribe(b"RIFFfake", 3.65)

    assert result.audio_seconds == 3.65


@pytest.mark.asyncio
async def test_missing_segments_yield_no_signals_rather_than_failing() -> None:
    recognizer, client = _recognizer(
        lambda request: httpx.Response(200, json={"text": "안녕", "duration": 1.0})
    )
    async with client:
        result = await recognizer.transcribe(b"RIFFfake", 1.0)

    assert result.text == "안녕"
    assert result.no_speech_prob is None
    assert result.confidence is None


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [400, 401, 429, 500, 503])
async def test_http_errors_become_provider_failed(status: int) -> None:
    recognizer, client = _recognizer(
        lambda request: httpx.Response(status, json={"message": "internal detail"})
    )
    async with client:
        with pytest.raises(SttError) as error:
            await recognizer.transcribe(b"RIFFfake", 2.0)

    assert error.value.code is SttFailureCode.PROVIDER_FAILED
    assert "internal detail" not in str(error.value)


@pytest.mark.asyncio
async def test_timeout_is_reported_as_timeout() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("too slow", request=request)

    recognizer, client = _recognizer(handler)
    async with client:
        with pytest.raises(SttError) as error:
            await recognizer.transcribe(b"RIFFfake", 2.0)

    assert error.value.code is SttFailureCode.PROVIDER_TIMEOUT


@pytest.mark.asyncio
async def test_network_error_is_reported_as_provider_failure() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("no route", request=request)

    recognizer, client = _recognizer(handler)
    async with client:
        with pytest.raises(SttError) as error:
            await recognizer.transcribe(b"RIFFfake", 2.0)

    assert error.value.code is SttFailureCode.PROVIDER_FAILED


@pytest.mark.asyncio
async def test_unparseable_body_is_reported_as_provider_failure() -> None:
    recognizer, client = _recognizer(lambda request: httpx.Response(200, text="not json"))
    async with client:
        with pytest.raises(SttError) as error:
            await recognizer.transcribe(b"RIFFfake", 2.0)

    assert error.value.code is SttFailureCode.PROVIDER_FAILED


@pytest.mark.asyncio
async def test_calls_the_provider_only_once_per_utterance() -> None:
    """No retry: realtime audio cannot be resent and the child is waiting (§11-E)."""

    calls: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(500, json={})

    recognizer, client = _recognizer(handler)
    async with client:
        with pytest.raises(SttError):
            await recognizer.transcribe(b"RIFFfake", 2.0)

    assert len(calls) == 1


@pytest.mark.asyncio
async def test_empty_audio_is_rejected_without_calling_the_provider() -> None:
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(200, json=_REAL_SPEECH_BODY)

    recognizer, client = _recognizer(handler)
    async with client:
        with pytest.raises(SttError) as error:
            await recognizer.transcribe(b"", 0.0)

    assert error.value.code is SttFailureCode.AUDIO_FORMAT_INVALID
    assert calls == []


def test_rejects_invalid_construction() -> None:
    client = httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200)))
    with pytest.raises(ValueError):
        WhisperSpeechRecognizer(client, api_key="  ")
    with pytest.raises(ValueError):
        WhisperSpeechRecognizer(client, api_key="k", timeout_seconds=0)
