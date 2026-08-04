"""고정 문장 TTS 캐시 테스트.

캐시가 지켜야 하는 것은 두 가지다: 등록된 문장의 재합성을 없애는 것(TTFB 실측
1.89초 제거), 그리고 **등록되지 않은 문장은 절대 담지 않는 것**(아이 발화에서
파생된 호응이 장기 메모리에 남지 않아야 한다).
"""

import httpx
import pytest

from app.config import AppSettings
from app.dialogue.pipeline import fixed_assistant_messages
from app.main import create_app
from app.speech.tts_cache import CachedSpeechSynthesizer

_AUDIO = b"\x01\x02" * 600


class _FakeSynthesizer:
    """호출 텍스트를 기록하는 합성기."""

    def __init__(self, audio: bytes = _AUDIO) -> None:
        self.calls: list[str] = []
        self._audio = audio

    async def synthesize(self, text: str):
        self.calls.append(text)
        for offset in range(0, len(self._audio), 400):
            yield self._audio[offset : offset + 400]


async def _collect(synthesizer, text: str) -> bytes:
    chunks = bytearray()
    async for chunk in synthesizer.synthesize(text):
        chunks += chunk
    return bytes(chunks)


@pytest.mark.asyncio
async def test_app_startup_does_not_call_tts_provider() -> None:
    def reject_provider_call(request: httpx.Request) -> httpx.Response:
        raise AssertionError(f"unexpected provider call during startup: {request.url.path}")

    app = create_app(
        AppSettings(gms_key="test-key"),
        transport=httpx.MockTransport(reject_provider_call),
    )

    async with app.router.lifespan_context(app):
        pass


class TestAllowlist:
    @pytest.mark.asyncio
    async def test_unregistered_text_is_passed_through_every_time(self) -> None:
        """아이 답변에서 파생된 호응은 매번 새로 합성되고 캐시에 남지 않는다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner)

        derived = "동생이랑 같이 산책 갔구나. 그때 마음이 어땠는지 말해 줄래?"
        assert await _collect(cache, derived) == _AUDIO
        assert await _collect(cache, derived) == _AUDIO

        assert inner.calls == [derived, derived]  # 매번 합성
        assert cache.cached_count == 0             # 담지 않는다

    @pytest.mark.asyncio
    async def test_registered_text_is_cached_after_first_use(self) -> None:
        """등록된 고정 문장은 첫 사용에서 합성한 뒤 재사용한다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner)
        cache.register(["복구 프롬프트"])

        first = await _collect(cache, "복구 프롬프트")
        second = await _collect(cache, "복구 프롬프트")

        assert inner.calls == ["복구 프롬프트"]
        assert first == second == _AUDIO

    @pytest.mark.asyncio
    async def test_lazy_caching_streams_before_it_stores(self) -> None:
        """캐시를 채우느라 첫 바이트를 늦추지 않는다 — 통과와 저장을 동시에 한다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner)
        cache.register(["복구 프롬프트"])

        received = []
        async for chunk in cache.synthesize("복구 프롬프트"):
            received.append(chunk)
            if len(received) == 1:
                # 스트림이 끝나기 전에 이미 첫 조각이 나왔다.
                assert cache.cached_count == 0

        assert cache.cached_count == 1
        assert b"".join(received) == _AUDIO


class TestFixedMessageInventory:
    def test_only_whole_fixed_messages_are_registered(self) -> None:
        messages = fixed_assistant_messages()

        assert len(messages) == len(set(messages))  # 중복 없음
        assert "괜찮아, 천천히 다시 말해 줄래?" in messages
        assert "그때 마음은 어땠어?" in messages
        # 주제 전환 메시지는 전환 질문을 품고 있어 조합이 유한하다.
        assert any(message.startswith("게임 이야기를 들려줘서 고마워!") for message in messages)
        # 아이 답변에서 파생되는 호응은 절대 포함되지 않는다.
        assert not any("구나." in message for message in messages)
