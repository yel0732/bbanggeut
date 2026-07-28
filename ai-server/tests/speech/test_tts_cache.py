"""고정 문장 TTS 캐시 테스트.

캐시가 지켜야 하는 것은 두 가지다: 등록된 문장의 재합성을 없애는 것(TTFB 실측
1.89초 제거), 그리고 **등록되지 않은 문장은 절대 담지 않는 것**(아이 발화에서
파생된 호응이 장기 메모리에 남지 않아야 한다).
"""

import logging

import pytest

from app.dialogue.pipeline import fixed_assistant_messages
from app.speech.tts_cache import CachedSpeechSynthesizer
from app.speech.tts_client import TtsError, TtsFailureCode

_LOGGER = logging.getLogger("test.speech.tts_cache")

_AUDIO = b"\x01\x02" * 600


class _FakeSynthesizer:
    """호출 텍스트를 기록하는 합성기. 텍스트별 실패를 스크립트할 수 있다."""

    def __init__(self, failing: set[str] | None = None, audio: bytes = _AUDIO) -> None:
        self.calls: list[str] = []
        self._failing = failing or set()
        self._audio = audio

    async def synthesize(self, text: str):
        self.calls.append(text)
        if text in self._failing:
            raise TtsError(TtsFailureCode.PROVIDER_FAILED)
        for offset in range(0, len(self._audio), 400):
            yield self._audio[offset : offset + 400]


async def _collect(synthesizer, text: str) -> bytes:
    chunks = bytearray()
    async for chunk in synthesizer.synthesize(text):
        chunks += chunk
    return bytes(chunks)


class TestPrewarm:
    @pytest.mark.asyncio
    async def test_prewarmed_text_is_never_synthesized_again(self) -> None:
        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
        cache.register(["괜찮아, 천천히 다시 말해 줄래?"])

        await cache.prewarm()
        first = await _collect(cache, "괜찮아, 천천히 다시 말해 줄래?")
        second = await _collect(cache, "괜찮아, 천천히 다시 말해 줄래?")

        assert inner.calls == ["괜찮아, 천천히 다시 말해 줄래?"]  # prewarm 1회뿐
        assert first == second == _AUDIO

    @pytest.mark.asyncio
    async def test_prewarm_is_idempotent(self) -> None:
        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
        cache.register(["질문 하나"])

        await cache.prewarm()
        await cache.prewarm()

        assert inner.calls == ["질문 하나"]

    @pytest.mark.asyncio
    async def test_a_provider_failure_does_not_stop_the_other_texts(self) -> None:
        """기동을 막지 않는다 — 실패한 문장만 비고 나머지는 채워진다."""

        inner = _FakeSynthesizer(failing={"실패할 문장"})
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
        cache.register(["실패할 문장", "성공할 문장"])

        cached = await cache.prewarm()

        assert cached == 1
        assert cache.cached_count == 1

    @pytest.mark.asyncio
    async def test_empty_audio_is_not_cached(self) -> None:
        """빈 스트림을 캐시하면 그 문장이 영구히 무음이 된다."""

        inner = _FakeSynthesizer(audio=b"")
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
        cache.register(["무음 응답"])

        await cache.prewarm()

        assert cache.cached_count == 0


class TestAllowlist:
    @pytest.mark.asyncio
    async def test_unregistered_text_is_passed_through_every_time(self) -> None:
        """아이 답변에서 파생된 호응은 매번 새로 합성되고 캐시에 남지 않는다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner, _LOGGER)

        derived = "동생이랑 같이 산책 갔구나. 그때 마음이 어땠는지 말해 줄래?"
        assert await _collect(cache, derived) == _AUDIO
        assert await _collect(cache, derived) == _AUDIO

        assert inner.calls == [derived, derived]  # 매번 합성
        assert cache.cached_count == 0             # 담지 않는다

    @pytest.mark.asyncio
    async def test_registered_text_is_cached_lazily_when_prewarm_did_not_run(self) -> None:
        """prewarm이 실패·미완이어도 첫 사용에서 스스로 채운다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
        cache.register(["복구 프롬프트"])

        first = await _collect(cache, "복구 프롬프트")
        second = await _collect(cache, "복구 프롬프트")

        assert inner.calls == ["복구 프롬프트"]
        assert first == second == _AUDIO

    @pytest.mark.asyncio
    async def test_lazy_caching_streams_before_it_stores(self) -> None:
        """캐시를 채우느라 첫 바이트를 늦추지 않는다 — 통과와 저장을 동시에 한다."""

        inner = _FakeSynthesizer()
        cache = CachedSpeechSynthesizer(inner, _LOGGER)
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
        assert "그때 마음이 어땠는지 말해 줄래?" in messages
        # 주제 전환 메시지는 전환 질문을 품고 있어 조합이 유한하다.
        assert any(message.startswith("게임 이야기를 들려줘서 고마워!") for message in messages)
        # 아이 답변에서 파생되는 호응은 절대 포함되지 않는다.
        assert not any("구나." in message for message in messages)
